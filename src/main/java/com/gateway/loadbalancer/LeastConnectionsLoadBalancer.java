package com.gateway.loadbalancer;

import com.gateway.model.BackendNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    private final AtomicInteger subRoundRobin = new AtomicInteger(0);

    @Override
    public String getAlgorithmName() {
        return "LEAST_CONNECTIONS";
    }

    @Override
    public Optional<BackendNode> chooseNode(List<BackendNode> healthyNodes, ServerWebExchange exchange) {
        if (healthyNodes == null || healthyNodes.isEmpty()) {
            return Optional.empty();
        }

        int minConnections = Integer.MAX_VALUE;
        List<BackendNode> candidates = new ArrayList<>();

        for (BackendNode node : healthyNodes) {
            int activeConns = node.getActiveConnections().get();
            if (activeConns < minConnections) {
                minConnections = activeConns;
                candidates.clear();
                candidates.add(node);
            } else if (activeConns == minConnections) {
                candidates.add(node);
            }
        }

        if (candidates.isEmpty()) {
            return Optional.of(healthyNodes.get(0));
        }

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        // Tie-breaker: round-robin among candidates with equal minimum active connections
        int index = Math.abs(subRoundRobin.getAndIncrement() % candidates.size());
        return Optional.of(candidates.get(index));
    }
}
