package com.gateway.loadbalancer;

import com.gateway.model.BackendNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    // Internal state tracking for Smooth Weighted Round-Robin (Nginx Algorithm)
    private final Map<String, Integer> currentWeights = new ConcurrentHashMap<>();

    @Override
    public String getAlgorithmName() {
        return "WEIGHTED";
    }

    @Override
    public synchronized Optional<BackendNode> chooseNode(List<BackendNode> healthyNodes, ServerWebExchange exchange) {
        if (healthyNodes == null || healthyNodes.isEmpty()) {
            return Optional.empty();
        }

        if (healthyNodes.size() == 1) {
            return Optional.of(healthyNodes.get(0));
        }

        int totalWeight = 0;
        BackendNode selectedNode = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (BackendNode node : healthyNodes) {
            int weight = Math.max(1, node.getWeight());
            totalWeight += weight;

            // Increment current weight by node's effective weight
            int current = currentWeights.getOrDefault(node.getId(), 0) + weight;
            currentWeights.put(node.getId(), current);

            if (current > maxCurrentWeight) {
                maxCurrentWeight = current;
                selectedNode = node;
            }
        }

        if (selectedNode != null) {
            // Subtract total weight from the selected node's current weight
            currentWeights.put(selectedNode.getId(), maxCurrentWeight - totalWeight);
            return Optional.of(selectedNode);
        }

        return Optional.of(healthyNodes.get(0));
    }
}
