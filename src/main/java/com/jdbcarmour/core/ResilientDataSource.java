package com.jdbcarmour.core;

import com.jdbcarmour.retry.RetryPolicy;
import com.jdbcarmour.circuitbreaker.CircuitBreaker;
import com.jdbcarmour.classifier.ExceptionClassifier;
import com.jdbcarmour.classifier.FailureType;
import com.jdbcarmour.exception.CircuitOpenException;
import com.jdbcarmour.exception.ConnectionExhaustedException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientDataSource {

    private final DataSource delegate;
    private final ExceptionClassifier classifier;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    public ResilientDataSource(DataSource delegate, ExceptionClassifier classifier,
            CircuitBreaker circuitBreaker, RetryPolicy retryPolicy) {
        this.delegate = delegate;
        this.classifier = classifier;
        this.circuitBreaker = circuitBreaker;
        this.retryPolicy = retryPolicy;
    }

    public Connection acquireConnection() throws SQLException {
        SQLException lastException = null;
        for (int attempt = 1; attempt <= retryPolicy.getMaxAttempts(); attempt++) {
            if (!circuitBreaker.allowRequest()) {
                log.warn("Circuit is OPEN — rejecting request on attempt {}", attempt);
                throw new CircuitOpenException("Circuit is OPEN — request rejected");
            }
            try {
                Connection connection = delegate.getConnection();
                circuitBreaker.recordSuccess();
                log.debug("Connection acquired on attempt {}, circuit={}",
                        attempt, circuitBreaker.getState());
                return connection;
            } catch (SQLException e) {
                lastException = e;
                FailureType type = classifier.classify(e);
                log.warn("Attempt {}/{} failed, classified as {}: {}",
                        attempt, retryPolicy.getMaxAttempts(), type, e.getMessage());
                if (type == FailureType.TRANSIENT) {
                    circuitBreaker.recordFailure();
                    if (attempt < retryPolicy.getMaxAttempts()) {
                        sleep(retryPolicy.delayForAttempt(attempt).toMillis());
                    }
                } else if (type == FailureType.UNKNOWN) {
                    circuitBreaker.recordFailure();
                    log.error("Unknown failure type, aborting");
                    throw e;
                } else {
                    // For FATAL, CONSTRAINT_VIOLATION, etc., throw the original SQLException immediately
                    log.error("Non-retryable failure ({}), aborting", type);
                    throw e;
                }
            }
        }
        log.error("All attempts exhausted: {}", retryPolicy.getMaxAttempts());
        throw new ConnectionExhaustedException(
            "Failed to acquire connection after " + retryPolicy.getMaxAttempts() + " attempts", lastException);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
