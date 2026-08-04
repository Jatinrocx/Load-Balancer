package com.gateway.metrics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsCollector {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);
    private final AtomicLong shortCircuitedRequests = new AtomicLong(0);

    private final AtomicLong requestsInLastSecond = new AtomicLong(0);
    private volatile long currentRps = 0;

    public void recordRequest() {
        totalRequests.incrementAndGet();
        requestsInLastSecond.incrementAndGet();
    }

    public void recordSuccess() {
        successfulRequests.incrementAndGet();
    }

    public void recordFailure() {
        failedRequests.incrementAndGet();
    }

    public void recordRateLimited() {
        rateLimitedRequests.incrementAndGet();
    }

    public void recordShortCircuited() {
        shortCircuitedRequests.incrementAndGet();
    }

    @Scheduled(fixedRate = 1000)
    public void calculateRps() {
        currentRps = requestsInLastSecond.getAndSet(0);
    }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getSuccessfulRequests() { return successfulRequests.get(); }
    public long getFailedRequests() { return failedRequests.get(); }
    public long getRateLimitedRequests() { return rateLimitedRequests.get(); }
    public long getShortCircuitedRequests() { return shortCircuitedRequests.get(); }
    public long getCurrentRps() { return currentRps; }
}
