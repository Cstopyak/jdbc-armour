package com.jdbcarmour.retry;

import com.jdbcarmour.classifier.FailureType;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import com.jdbcarmour.circuitbreaker.CircuitBreaker;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class RetryPolicy {
    private int maxAttempts;
    private Duration initialDelay;
    private Duration maxDelay;
    private boolean jitter;
    private BackoffStrategy backoffStrategy = BackoffStrategy.EXPONENTIAL;
    private Set<FailureType> retryOn;
    private CircuitBreaker circuitBreaker;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public boolean isJitter() {
        return jitter;
    }

    public BackoffStrategy getBackoffStrategy() {
        return backoffStrategy;
    }

    public Set<FailureType> getRetryOn() {
        return retryOn;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    public void setJitter(boolean jitter) {
        this.jitter = jitter;
    }

    public void setBackoffStrategy(BackoffStrategy backoffStrategy) {
        this.backoffStrategy = backoffStrategy;
    }

    public void setRetryOn(Set<FailureType> retryOn) {
        this.retryOn = retryOn;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void defaults() {
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
        if (initialDelay == null) {
            initialDelay = Duration.ofMillis(100);
        }
        if (maxDelay == null) {
            maxDelay = Duration.ofSeconds(5);
        }
        if (backoffStrategy == null) {
            backoffStrategy = BackoffStrategy.EXPONENTIAL;
        }
        if (retryOn == null || retryOn.isEmpty()) {
            retryOn = Set.of(FailureType.TRANSIENT); // Default to retry on all TRANSIENT failures
        }
        if (circuitBreaker == null) {
            circuitBreaker = CircuitBreaker.builder()
                    .failureThreshold(5)
                    .successThreshold(2)
                    .recoveryWindow(Duration.ofSeconds(30))
                    .build();
        }
        jitter = true; // Default to enabling jitter
    }

    public RetryPolicy() {
        defaults();
    }

    private boolean shouldRetry(FailureType failureType) {
        return retryOn.contains(failureType);
    }

    public Duration delayForAttempt(int attempt) {
        Duration delay = backoffStrategy.calculateDelay(attempt);
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return delay;
    }

    public <T> T executeWithRetry(Supplier<T> operation, Function<Exception, FailureType> classifier) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!circuitBreaker.allowRequest()) {
                throw new RuntimeException("Circuit breaker is open, aborting retries.");
            }
            try {
                T result = operation.get();
                circuitBreaker.recordSuccess();
                return result;
            } catch (Exception e) {
                FailureType failureType = classifier.apply(e);
                circuitBreaker.recordFailure();
                if (attempt == maxAttempts || !shouldRetry(failureType)) {
                    throw e;
                }
                delayForAttempt(attempt);
            }
        }
        throw new RuntimeException("Retries exhausted.");
    }
}