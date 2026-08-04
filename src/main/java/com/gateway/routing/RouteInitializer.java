package com.gateway.routing;

import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import com.gateway.model.RouteConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteInitializer implements CommandLineRunner {

    private final RouteRegistry routeRegistry;

    @Override
    public void run(String... args) {
        log.info("[Initializer] Seeding initial route configurations...");

        // User Service Route (10 requests per minute per client)
        RouteConfig userServiceRoute = RouteConfig.builder()
                .id("user-service")
                .pathPattern("/api/v1/users/**")
                .stripPrefix(true)
                .loadBalancerAlgorithm("ROUND_ROBIN")
                .rateLimitRequests(10) // 10 requests
                .rateLimitWindowSeconds(60) // per 1 minute (60 seconds)
                .nodes(List.of(
                        BackendNode.builder().id("node-1").uri("http://backend-node-1:80").weight(3).status(NodeStatus.UP).build(),
                        BackendNode.builder().id("node-2").uri("http://backend-node-2:80").weight(2).status(NodeStatus.UP).build(),
                        BackendNode.builder().id("node-3").uri("http://backend-node-3:80").weight(1).status(NodeStatus.UP).build()
                ))
                .build();

        // Product Service Route (20 requests per minute)
        RouteConfig productServiceRoute = RouteConfig.builder()
                .id("product-service")
                .pathPattern("/api/v1/products/**")
                .stripPrefix(true)
                .loadBalancerAlgorithm("LEAST_CONNECTIONS")
                .rateLimitRequests(20)
                .rateLimitWindowSeconds(60)
                .nodes(List.of(
                        BackendNode.builder().id("prod-node-1").uri("http://backend-node-1:80").weight(1).status(NodeStatus.UP).build()
                ))
                .build();

        routeRegistry.registerRoute(userServiceRoute);
        routeRegistry.registerRoute(productServiceRoute);

        log.info("[Initializer] Successfully registered {} routes.", routeRegistry.getAllRoutes().size());
    }
}
