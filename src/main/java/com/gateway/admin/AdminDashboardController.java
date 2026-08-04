package com.gateway.admin;

import com.gateway.circuitbreaker.CircuitBreaker;
import com.gateway.circuitbreaker.CircuitBreakerRegistry;
import com.gateway.metrics.MetricsCollector;
import com.gateway.model.BackendNode;
import com.gateway.model.RouteConfig;
import com.gateway.routing.RouteRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final RouteRegistry routeRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsCollector metricsCollector;

    @GetMapping("/routes")
    public Mono<ResponseEntity<Collection<RouteConfig>>> getAllRoutes() {
        return Mono.just(ResponseEntity.ok(routeRegistry.getAllRoutes()));
    }

    @PostMapping("/routes/{routeId}/algorithm")
    public Mono<ResponseEntity<Map<String, Object>>> updateAlgorithm(
            @PathVariable String routeId,
            @RequestParam String algorithm) {
        
        Optional<RouteConfig> routeOpt = routeRegistry.findMatchingRoute(routeId);
        if (routeOpt.isEmpty()) {
            routeOpt = routeRegistry.getAllRoutes().stream().filter(r -> r.getId().equals(routeId)).findFirst();
        }

        if (routeOpt.isPresent()) {
            RouteConfig route = routeOpt.get();
            route.setLoadBalancerAlgorithm(algorithm.toUpperCase());
            log.info("[Admin API] Dynamic Algorithm Update for Route {}: -> {}", routeId, algorithm);
            return Mono.just(ResponseEntity.ok(Map.of("status", "SUCCESS", "routeId", routeId, "newAlgorithm", algorithm)));
        }

        return Mono.just(ResponseEntity.notFound().build());
    }

    @PostMapping("/routes/{routeId}/nodes/{nodeId}/weight")
    public Mono<ResponseEntity<Map<String, Object>>> updateNodeWeight(
            @PathVariable String routeId,
            @PathVariable String nodeId,
            @RequestParam int weight) {

        Optional<RouteConfig> routeOpt = routeRegistry.getAllRoutes().stream().filter(r -> r.getId().equals(routeId)).findFirst();
        if (routeOpt.isPresent()) {
            RouteConfig route = routeOpt.get();
            Optional<BackendNode> nodeOpt = route.getNodes().stream().filter(n -> n.getId().equals(nodeId)).findFirst();
            if (nodeOpt.isPresent()) {
                nodeOpt.get().setWeight(Math.max(1, weight));
                log.info("[Admin API] Dynamic Weight Update for Node {} in Route {}: -> {}", nodeId, routeId, weight);
                return Mono.just(ResponseEntity.ok(Map.of("status", "SUCCESS", "nodeId", nodeId, "newWeight", weight)));
            }
        }

        return Mono.just(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamMetrics() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("currentRps", metricsCollector.getCurrentRps());
                    data.put("totalRequests", metricsCollector.getTotalRequests());
                    data.put("successfulRequests", metricsCollector.getSuccessfulRequests());
                    data.put("failedRequests", metricsCollector.getFailedRequests());
                    data.put("rateLimitedRequests", metricsCollector.getRateLimitedRequests());
                    data.put("shortCircuitedRequests", metricsCollector.getShortCircuitedRequests());

                    List<Map<String, Object>> routeSnapshots = new ArrayList<>();
                    for (RouteConfig route : routeRegistry.getAllRoutes()) {
                        CircuitBreaker cb = circuitBreakerRegistry.getOrCreate(route.getId());
                        
                        Map<String, Object> routeMap = new HashMap<>();
                        routeMap.put("id", route.getId());
                        routeMap.put("pathPattern", route.getPathPattern());
                        routeMap.put("algorithm", route.getLoadBalancerAlgorithm());
                        routeMap.put("rateLimitRps", route.getRateLimitRequests());
                        routeMap.put("rateLimitWindowSeconds", route.getRateLimitWindowSeconds());
                        routeMap.put("circuitState", cb.getState().name());

                        List<Map<String, Object>> nodeSnapshots = new ArrayList<>();
                        for (BackendNode node : route.getNodes()) {
                            Map<String, Object> nodeMap = new HashMap<>();
                            nodeMap.put("id", node.getId());
                            nodeMap.put("uri", node.getUri());
                            nodeMap.put("weight", node.getWeight());
                            nodeMap.put("status", node.getStatus().name());
                            nodeMap.put("activeConnections", node.getActiveConnections().get());
                            nodeSnapshots.add(nodeMap);
                        }
                        routeMap.put("nodes", nodeSnapshots);
                        routeSnapshots.add(routeMap);
                    }
                    data.put("routes", routeSnapshots);

                    return ServerSentEvent.<Map<String, Object>>builder()
                            .id(String.valueOf(sequence))
                            .event("gateway-metrics")
                            .data(data)
                            .build();
                });
    }
}
