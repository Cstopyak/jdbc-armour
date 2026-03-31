package com.jdbcarmour.retry;

import java.util.function.Supplier;
import java.util.function.Function;
import com.jdbcarmour.classifier.FailureType;

public class RetryEngine {

    private final RetryPolicy retryPolicy;

    public RetryEngine(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public <T> T executeWithRetry(Supplier<T> operation, Function<Exception, FailureType> classifier) {
        for (int attempt = 1; attempt <= retryPolicy.getMaxAttempts(); attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                FailureType type = classifier.apply(e);
                if (retryPolicy.getRetryOn().contains(type)) {
                    if (attempt < retryPolicy.getMaxAttempts()) {
                        try {
                            Thread.sleep(retryPolicy.delayForAttempt(attempt).toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", ie);
                        }
                    }
                } else {
                    throw new RuntimeException("Non-retryable exception", e);
                }
            }
        }
        throw new RuntimeException("Retries exhausted.");

    }
}