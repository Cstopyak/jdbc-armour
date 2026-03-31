package com.jdbcarmour.core;

import com.jdbcarmour.retry.RetryEngine;
import com.jdbcarmour.circuitbreaker.CircuitBreaker;
import com.jdbcarmour.classifier.ExceptionClassifier;
import com.jdbcarmour.exception.CircuitOpenException;
import com.jdbcarmour.exception.ConnectionExhaustedException;
import com.jdbcarmour.classifier.FailureType;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientDataSource {

    private final DataSource delegate;
    private final ExceptionClassifier classifier;
    private final CircuitBreaker circuitBreaker;
    private final RetryEngine retryEngine;
    private SQLException lastException;

    public ResilientDataSource(DataSource delegate, ExceptionClassifier classifier,
            CircuitBreaker circuitBreaker, RetryEngine retryEngine) {
        this.delegate = delegate;
        this.classifier = classifier;
        this.circuitBreaker = circuitBreaker;
        this.retryEngine = retryEngine;
    }

    public Connection acquireConnection() throws SQLException {
        lastException = null;
        try {
            return retryEngine.executeWithRetry(() -> {
                if (!circuitBreaker.allowRequest()) {
                    log.warn("Circuit is OPEN - rejecting request");
                    throw new RuntimeException(new CircuitOpenException("Circuit is OPEN - request rejected"));
                }
                try {
                    Connection connection = delegate.getConnection();
                    circuitBreaker.recordSuccess();
                    log.debug("Connection acquired, circuit={}", circuitBreaker.getState());
                    return connection;
                } catch (SQLException e) {
                    lastException = e;
                    FailureType type = classifier.classify(e);
                    log.warn("Connection attempt failed, classified as {}: {}", type, e.getMessage());
                    if (type == FailureType.TRANSIENT) {
                        circuitBreaker.recordFailure();
                    } else if (type == FailureType.UNKNOWN) {
                        circuitBreaker.recordFailure();
                        log.error("Unknown failure type, aborting");
                    } else {
                        log.error("Non-retryable failure ({}), aborting", type);
                    }
                    throw new RuntimeException(e);
                }
            }, (Exception e) -> {
                if (e instanceof SQLException) {
                    return classifier.classify((SQLException) e);
                } else if (e.getCause() instanceof SQLException) {
                    return classifier.classify((SQLException) e.getCause());
                } else {
                    // For CircuitOpenException or others, treat as UNKNOWN or FATAL
                    return FailureType.UNKNOWN;
                }
            });
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            while (cause instanceof RuntimeException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (e.getMessage() != null && e.getMessage().contains("Retries exhausted")) {
                throw new ConnectionExhaustedException("Failed to acquire connection after retries exhausted", lastException);
            }
            if (cause instanceof SQLException){
                throw (SQLException) cause;
            }
            if (cause instanceof CircuitOpenException){
                throw (CircuitOpenException) cause;
            }
            throw e;
        }
    }
}
