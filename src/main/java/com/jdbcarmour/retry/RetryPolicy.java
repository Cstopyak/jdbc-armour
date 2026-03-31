package com.jdbcarmour.retry;

import com.jdbcarmour.classifier.FailureType;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final boolean jitter;
    private final BackoffStrategy backoffStrategy;
    private final Set<FailureType> retryOn;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.jitter = builder.jitter;
        this.backoffStrategy = builder.backoffStrategy;
        this.retryOn = Set.copyOf(builder.retryOn);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(5);
        private boolean jitter = true;
        private BackoffStrategy backoffStrategy = BackoffStrategy.EXPONENTIAL;
        private Set<FailureType> retryOn = Set.of(FailureType.TRANSIENT);

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
            return this;
        }

        public Builder retryOn(Set<FailureType> retryOn) {
            this.retryOn = Set.copyOf(retryOn);
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Set<FailureType> getRetryOn() {
        return retryOn;
    }


    private boolean shouldRetry(FailureType failureType, int attempts) {
        if (attempts <= maxAttempts && retryOn.contains(failureType)) {
            log.info("Attempt {} failed with {}. Retrying...", attempts, failureType);
            return true;
        }
        return retryOn.contains(failureType);
    }

    public Duration delayForAttempt(int attempt) {
        Duration delay = calculateDelay(attempt);
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return delay;
    }

     public Duration calculateDelay(int attempt) {
        long delay = 0;
        switch (this.backoffStrategy) {
            case FIXED:
                delay = initialDelay.toMillis();
                break;
            case EXPONENTIAL:
                delay = (long) (initialDelay.toMillis() * Math.pow(2, attempt - 1));
                break;
        }
        if (jitter) {
            delay += (long) (Math.random() * 100); // Add up to 100ms of jitter
        }
        return Duration.ofMillis(Math.min(delay, maxDelay.toMillis()));
    }
}