package com.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackendNode {
    private String id;
    private String uri; // e.g. http://localhost:8081
    @Builder.Default
    private int weight = 1;
    @Builder.Default
    private AtomicInteger activeConnections = new AtomicInteger(0);
    @Builder.Default
    private NodeStatus status = NodeStatus.UP;
    @Builder.Default
    private AtomicInteger failureCount = new AtomicInteger(0);
    @Builder.Default
    private Instant lastChecked = Instant.now();

    public void incrementConnections() {
        this.activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        this.activeConnections.decrementAndGet();
    }

    public void recordSuccess() {
        this.failureCount.set(0);
        if (this.status == NodeStatus.DEGRADED) {
            this.status = NodeStatus.UP;
        }
    }

    public void recordFailure(int thresholdToDown) {
        int failures = this.failureCount.incrementAndGet();
        if (failures >= thresholdToDown) {
            this.status = NodeStatus.DOWN;
        } else {
            this.status = NodeStatus.DEGRADED;
        }
    }
}
