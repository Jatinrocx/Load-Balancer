package com.gateway.loadbalancer;

import com.gateway.model.BackendNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public String getAlgorithmName() {
        return "ROUND_ROBIN";
    }

    @Override
    public Optional<BackendNode> chooseNode(List<BackendNode> healthyNodes, ServerWebExchange exchange) {
        if (healthyNodes == null || healthyNodes.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.abs(position.getAndIncrement() % healthyNodes.size());
        return Optional.of(healthyNodes.get(index));
    }
}
