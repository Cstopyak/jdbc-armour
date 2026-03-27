package com.jdbcarmour.circuitbreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CircuitBreaker {

    private final int failureThreshold;
    private final int successThreshold;
    private final Duration recoveryWindow;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile Instant openedAt;

    private CircuitBreaker(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.successThreshold = builder.successThreshold;
        this.recoveryWindow = builder.recoveryWindow;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int failureThreshold;
        private int successThreshold;
        private Duration recoveryWindow;

        private Builder() {}

        public Builder failureThreshold(int value) {
            this.failureThreshold = value;
            return this;
        }

        public Builder successThreshold(int value) {
            this.successThreshold = value;
            return this;
        }

        public Builder recoveryWindow(Duration value) {
            this.recoveryWindow = value;
            return this;
        }

        public CircuitBreaker build() {
            return new CircuitBreaker(this);
        }
    }

    public boolean allowRequest() {
        CircuitState current = state.get();
        if (current == CircuitState.CLOSED) {
            return true;
        }
        if (current == CircuitState.OPEN) {
            if (recoveryWindowElapsed()) {
                if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    successCount.set(0);
                    log.info("Circuit transitioned OPEN -> HALF_OPEN");
                }
                return true;
            }
            return false;
        }
        // HALF_OPEN — let the probe through
        return true;
    }

    public void recordSuccess() {
        CircuitState current = state.get();
        if (current == CircuitState.HALF_OPEN) {
            if (successCount.incrementAndGet() >= successThreshold) {
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                    failureCount.set(0);
                    successCount.set(0);
                    log.info("Circuit transitioned HALF_OPEN -> CLOSED");
                }
            }
        } else if (current == CircuitState.CLOSED) {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        CircuitState current = state.get();
        if (current == CircuitState.CLOSED) {
            if (failureCount.incrementAndGet() >= failureThreshold) {
                if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                    openedAt = Instant.now();
                    log.warn("Circuit transitioned CLOSED -> OPEN (failures={})", failureThreshold);
                }
            }
        } else if (current == CircuitState.HALF_OPEN) {
            if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                openedAt = Instant.now();
                successCount.set(0);
                log.warn("Circuit transitioned HALF_OPEN -> OPEN (probe failed)");
            }
        }
    }

    public CircuitState getState() {
        return state.get();
    }

    private boolean recoveryWindowElapsed() {
        Instant opened = openedAt;
        return opened != null
            && Duration.between(opened, Instant.now()).compareTo(recoveryWindow) >= 0;
    }
}
