package com.jdbcarmour.core;

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
    private final int maxAttempts;
    private final long retryDelayMs;

    public ResilientDataSource(DataSource delegate, ExceptionClassifier classifier,
            CircuitBreaker circuitBreaker, int maxAttempts, long retryDelayMs) {
        this.delegate = delegate;
        this.classifier = classifier;
        this.circuitBreaker = circuitBreaker;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public Connection acquireConnection() throws SQLException {
        SQLException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
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
                        attempt, maxAttempts, type, e.getMessage());
                if (type == FailureType.TRANSIENT) {
                    circuitBreaker.recordFailure();
                    if (attempt < maxAttempts) {
                        sleep(retryDelayMs);
                    }
                } else if (type == FailureType.UNKNOWN) {
                    circuitBreaker.recordFailure();
                    log.error("Unknown failure type, aborting");
                    throw e;
                } else {
                    log.error("Non-retryable failure ({}), aborting", type);
                    throw e;
                }
            }
        }
        log.error("All attempts exhausted: {}", maxAttempts);
        throw new ConnectionExhaustedException(
            "Failed to acquire connection after " + maxAttempts + " attempts", lastException);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
