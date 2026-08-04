package com.gateway;

import com.gateway.circuitbreaker.CircuitBreaker;
import com.gateway.circuitbreaker.CircuitBreakerConfig;
import com.gateway.circuitbreaker.CircuitState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitBreakerTest {

    @Test
    void testCircuitBreakerTripsToOpenWhenFailureRateExceeded() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .slidingWindowSize(4)
                .failureRateThresholdPercentage(50.0f) // 50% failures trips circuit
                .waitDurationInOpenStateMs(500)
                .permittedHalfOpenCalls(2)
                .build();

        CircuitBreaker cb = new CircuitBreaker("test-service", config);

        assertEquals(CircuitState.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());

        // Simulate 2 successes, 2 failures (50% failure rate)
        cb.onSuccess();
        cb.onSuccess();
        cb.onFailure();
        cb.onFailure();

        // Circuit should trip to OPEN
        assertEquals(CircuitState.OPEN, cb.getState());
        assertFalse(cb.allowRequest()); // Requests blocked!
    }

    @Test
    void testTransitionToHalfOpenAndRecovery() throws InterruptedException {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .slidingWindowSize(2)
                .failureRateThresholdPercentage(50.0f)
                .waitDurationInOpenStateMs(100) // 100ms wait
                .permittedHalfOpenCalls(2)
                .build();

        CircuitBreaker cb = new CircuitBreaker("recovery-service", config);

        // Fail to open circuit
        cb.onFailure();
        cb.onFailure();
        assertEquals(CircuitState.OPEN, cb.getState());

        // Wait for open duration to elapse
        Thread.sleep(150);

        // Next request should trigger state transition to HALF_OPEN
        assertTrue(cb.allowRequest());
        assertEquals(CircuitState.HALF_OPEN, cb.getState());

        // Trial requests succeed
        cb.onSuccess();
        cb.onSuccess();

        // Should recover back to CLOSED state
        assertEquals(CircuitState.CLOSED, cb.getState());
    }
}
