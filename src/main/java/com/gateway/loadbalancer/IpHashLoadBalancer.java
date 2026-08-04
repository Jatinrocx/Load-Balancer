package com.gateway.loadbalancer;

import com.gateway.model.BackendNode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

@Component
public class IpHashLoadBalancer implements LoadBalancer {

    @Override
    public String getAlgorithmName() {
        return "IP_HASH";
    }

    @Override
    public Optional<BackendNode> chooseNode(List<BackendNode> healthyNodes, ServerWebExchange exchange) {
        if (healthyNodes == null || healthyNodes.isEmpty()) {
            return Optional.empty();
        }

        String clientIp = extractClientIp(exchange);
        int hash = Math.abs(clientIp.hashCode());
        int index = hash % healthyNodes.size();

        return Optional.of(healthyNodes.get(index));
    }

    private String extractClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. Check X-Forwarded-For header (if behind downstream proxies/load balancers)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // 2. Check X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // 3. Fallback to direct socket address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "127.0.0.1"; // Default fallback
    }
}
