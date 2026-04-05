package com.jdbcarmour.retry;

import java.util.function.Supplier;
import java.util.function.Function;
import com.jdbcarmour.classifier.FailureType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryEngine {

    private final RetryPolicy retryPolicy;

    public RetryEngine(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public <T> T executeWithRetry(Supplier<T> operation, Function<Exception, FailureType> classifier) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= retryPolicy.getMaxAttempts(); attempt++) {
            log.info("retryPolicy in progress, attempt {}/{}", attempt, retryPolicy.getMaxAttempts());
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                FailureType type = classifier.apply(e);
                if (retryPolicy.shouldRetry(type, attempt)) {
                    try {
                        retryPolicy.delayForAttempt(attempt);
                    } catch (RuntimeException ie) {
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else if (!retryPolicy.getRetryOn().contains(type)) {
                    throw new RuntimeException("Non-retryable exception", e);
                }
            }
        }
        throw new RuntimeException("Retries exhausted.", lastException);

    }
}