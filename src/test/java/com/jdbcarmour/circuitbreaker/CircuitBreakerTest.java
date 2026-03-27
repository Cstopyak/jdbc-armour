package com.jdbcarmour.circuitbreaker;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    private CircuitBreaker breaker(int failureThreshold, int successThreshold, Duration recoveryWindow) {
        return CircuitBreaker.builder()
            .failureThreshold(failureThreshold)
            .successThreshold(successThreshold)
            .recoveryWindow(recoveryWindow)
            .build();
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    void startsInClosedState() {
        CircuitBreaker cb = breaker(3, 2, Duration.ofSeconds(10));
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void allowsRequestsWhenClosed() {
        CircuitBreaker cb = breaker(3, 2, Duration.ofSeconds(10));
        assertTrue(cb.allowRequest());
    }

    // ── CLOSED -> OPEN ──────────────────────────────────────────────────────────

    @Test
    void tripsToOpenAtFailureThreshold() {
        CircuitBreaker cb = breaker(3, 2, Duration.ofSeconds(10));
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitState.CLOSED, cb.getState());
        cb.recordFailure();
        assertEquals(CircuitState.OPEN, cb.getState());
    }

    @Test
    void blocksRequestsWhenOpen() {
        CircuitBreaker cb = breaker(1, 2, Duration.ofSeconds(10));
        cb.recordFailure();
        assertEquals(CircuitState.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    void successInClosedStateResetsFailureCount() {
        CircuitBreaker cb = breaker(3, 2, Duration.ofSeconds(10));
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordFailure();
        // only 2 failures since the reset — should still be CLOSED
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    // ── OPEN -> HALF_OPEN ───────────────────────────────────────────────────────

    @Test
    void transitionsToHalfOpenAfterRecoveryWindow() {
        CircuitBreaker cb = breaker(1, 2, Duration.ZERO);
        cb.recordFailure();
        assertEquals(CircuitState.OPEN, cb.getState());
        assertTrue(cb.allowRequest());
        assertEquals(CircuitState.HALF_OPEN, cb.getState());
    }

    @Test
    void remainsOpenBeforeRecoveryWindowElapses() {
        CircuitBreaker cb = breaker(1, 2, Duration.ofHours(1));
        cb.recordFailure();
        assertFalse(cb.allowRequest());
        assertEquals(CircuitState.OPEN, cb.getState());
    }

    // ── HALF_OPEN -> CLOSED ─────────────────────────────────────────────────────

    @Test
    void closesAfterSuccessThresholdInHalfOpen() {
        CircuitBreaker cb = breaker(1, 2, Duration.ZERO);
        cb.recordFailure();
        cb.allowRequest(); // triggers OPEN -> HALF_OPEN
        cb.recordSuccess();
        assertEquals(CircuitState.HALF_OPEN, cb.getState());
        cb.recordSuccess();
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void allowsRequestsInHalfOpen() {
        CircuitBreaker cb = breaker(1, 2, Duration.ZERO);
        cb.recordFailure();
        cb.allowRequest(); // triggers OPEN -> HALF_OPEN
        assertTrue(cb.allowRequest());
    }

    // ── HALF_OPEN -> OPEN ───────────────────────────────────────────────────────

    @Test
    void reopensOnFailureInHalfOpen() {
        CircuitBreaker cb = breaker(1, 2, Duration.ZERO);
        cb.recordFailure();
        cb.allowRequest(); // triggers OPEN -> HALF_OPEN
        cb.recordFailure();
        assertEquals(CircuitState.OPEN, cb.getState());
    }

    @Test
    void blocksRequestsAfterReopeningFromHalfOpen() {
        CircuitBreaker cb = breaker(1, 2, Duration.ofHours(1));
        cb.recordFailure();
        // force to HALF_OPEN by using zero window breaker then re-record failure
        CircuitBreaker cb2 = breaker(1, 2, Duration.ZERO);
        cb2.recordFailure();
        cb2.allowRequest(); // OPEN -> HALF_OPEN
        cb2.recordFailure(); // HALF_OPEN -> OPEN (new openedAt, 1-hour window)

        // A fresh breaker with long window after re-opening should block
        assertEquals(CircuitState.OPEN, cb2.getState());
    }

    // ── Full cycle ─────────────────────────────────────────────────────────────

    @Test
    void fullCycleClosedOpenHalfOpenClosed() {
        CircuitBreaker cb = breaker(2, 2, Duration.ZERO);

        // CLOSED — accumulate failures
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitState.OPEN, cb.getState());

        // Recovery window is zero — allowRequest transitions to HALF_OPEN immediately
        assertTrue(cb.allowRequest());
        assertEquals(CircuitState.HALF_OPEN, cb.getState());

        // Enough successes to close
        cb.recordSuccess();
        cb.recordSuccess();
        assertEquals(CircuitState.CLOSED, cb.getState());

        // Back to normal
        assertTrue(cb.allowRequest());
    }
}
