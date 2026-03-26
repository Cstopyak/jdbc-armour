package com.jdbcarmour.core;

import com.jdbcarmour.classifier.ExceptionClassifier;
import com.jdbcarmour.classifier.FailureType;
import com.jdbcarmour.exception.ConnectionExhaustedException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientDataSource {

    private final DataSource delegate;
    private final ExceptionClassifier classifier;
    private final int maxAttempts;
    private final long retryDelayMs;

    public ResilientDataSource(DataSource delegate, ExceptionClassifier classifier,
            int maxAttempts, long retryDelayMs) {
        this.delegate = delegate;
        this.classifier = classifier;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public Connection acquireConnection() throws SQLException {
        SQLException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Connection connection = delegate.getConnection();
                log.debug("Connection acquired on attempt {}", attempt);
                return connection;
            } catch (SQLException e) {
                lastException = e;
                FailureType type = classifier.classify(e);
                log.warn("Attempt {}/{} failed, classified as {}: {}", attempt, maxAttempts, type, e.getMessage());
                if (type != FailureType.TRANSIENT) {
                    log.error("Non-retryable failure ({}), aborting", type);
                    throw e;
                }
                if (attempt < maxAttempts) {
                    sleep(retryDelayMs);
                }
            }
        }
        log.error("All attempts exhausted: {} ", maxAttempts);
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
