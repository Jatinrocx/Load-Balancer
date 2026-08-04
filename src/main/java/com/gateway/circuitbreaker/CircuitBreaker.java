package com.gateway.circuitbreaker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CircuitBreaker {

    @Getter
    private final String name;
    private final CircuitBreakerConfig config;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final Queue<Boolean> slidingWindow = new ConcurrentLinkedQueue<>();
    private final AtomicInteger halfOpenTrialCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);

    @Getter
    private volatile long lastStateChangeTimestamp = Instant.now().toEpochMilli();

    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config != null ? config : new CircuitBreakerConfig();
    }

    public CircuitState getState() {
        return state.get();
    }

    public synchronized boolean allowRequest() {
        CircuitState currentState = state.get();

        if (currentState == CircuitState.CLOSED) {
            return true;
        }

        if (currentState == CircuitState.OPEN) {
            long now = Instant.now().toEpochMilli();
            if (now - lastStateChangeTimestamp >= config.getWaitDurationInOpenStateMs()) {
                log.info("[CircuitBreaker '{}'] Wait duration elapsed. Transitioning OPEN -> HALF_OPEN (probing backends)", name);
                transitionTo(CircuitState.HALF_OPEN);
                halfOpenTrialCount.set(1);
                halfOpenSuccessCount.set(0);
                return true;
            }
            return false; // Short-circuit
        }

        if (currentState == CircuitState.HALF_OPEN) {
            int currentTrials = halfOpenTrialCount.getAndIncrement();
            return currentTrials < config.getPermittedHalfOpenCalls();
        }

        return true;
    }

    public synchronized void onSuccess() {
        recordOutcome(true);

        if (state.get() == CircuitState.HALF_OPEN) {
            int successes = halfOpenSuccessCount.incrementAndGet();
            if (successes >= config.getPermittedHalfOpenCalls()) {
                log.info("[CircuitBreaker '{}'] Probing succeeded! Transitioning HALF_OPEN -> CLOSED (recovered)", name);
                slidingWindow.clear();
                transitionTo(CircuitState.CLOSED);
            }
        }
    }

    public synchronized void onFailure() {
        recordOutcome(false);

        CircuitState currentState = state.get();

        if (currentState == CircuitState.HALF_OPEN) {
            log.warn("[CircuitBreaker '{}'] Probing call failed! Transitioning HALF_OPEN -> OPEN", name);
            transitionTo(CircuitState.OPEN);
            return;
        }

        if (currentState == CircuitState.CLOSED) {
            evaluateSlidingWindow();
        }
    }

    private void recordOutcome(boolean success) {
        slidingWindow.add(success);
        while (slidingWindow.size() > config.getSlidingWindowSize()) {
            slidingWindow.poll();
        }
    }

    private void evaluateSlidingWindow() {
        if (slidingWindow.size() < config.getSlidingWindowSize()) {
            return; // Wait until full window size is collected
        }

        long failures = slidingWindow.stream().filter(success -> !success).count();
        float failureRate = ((float) failures / slidingWindow.size()) * 100.0f;

        if (failureRate >= config.getFailureRateThresholdPercentage()) {
            log.warn("[CircuitBreaker ALERT '{}'] Failure rate {:.1f}% >= threshold {:.1f}%. TRIPPING CIRCUIT to OPEN!",
                    name, failureRate, config.getFailureRateThresholdPercentage());
            transitionTo(CircuitState.OPEN);
        }
    }

    private void transitionTo(CircuitState newState) {
        state.set(newState);
        lastStateChangeTimestamp = Instant.now().toEpochMilli();
    }
}
