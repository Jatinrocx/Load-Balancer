package com.gateway.loadbalancer;

import com.gateway.model.BackendNode;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Optional;

public interface LoadBalancer {
    
    /**
     * Unique identifier for the algorithm (e.g. ROUND_ROBIN, LEAST_CONNECTIONS, WEIGHTED, IP_HASH)
     */
    String getAlgorithmName();

    /**
     * Choose the optimal backend node from the list of currently healthy nodes.
     *
     * @param healthyNodes List of nodes with status != DOWN
     * @param exchange     The current ServerWebExchange (provides access to IP headers, cookies, etc.)
     * @return Selected BackendNode wrapped in Optional
     */
    Optional<BackendNode> chooseNode(List<BackendNode> healthyNodes, ServerWebExchange exchange);
}
