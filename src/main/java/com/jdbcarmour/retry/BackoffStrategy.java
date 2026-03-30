package com.jdbcarmour.retry;

import java.time.Duration;

public enum BackoffStrategy {
    FIXED,
    EXPONENTIAL;

    private RetryPolicy retryPolicy = new RetryPolicy();

    public Duration calculateDelay(int attempt) {
        long delay = 0;
        switch (this) {
            case FIXED:
                delay = retryPolicy.getInitialDelay().toMillis();
                break;
            case EXPONENTIAL:
                delay = (long) (retryPolicy.getInitialDelay().toMillis() * Math.pow(2, attempt - 1));
                break;
        }
        if (retryPolicy.isJitter()) {
            delay += (long) (Math.random() * 100); // Add up to 100ms of jitter
        }
        return Duration.ofMillis(Math.min(delay, retryPolicy.getMaxDelay().toMillis()));
    }
}