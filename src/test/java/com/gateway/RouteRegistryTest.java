package com.gateway;

import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import com.gateway.model.RouteConfig;
import com.gateway.routing.RouteRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RouteRegistryTest {

    @Test
    void testRouteRegistrationAndMatching() {
        RouteRegistry registry = new RouteRegistry();

        RouteConfig route = RouteConfig.builder()
                .id("test-route")
                .pathPattern("/api/v1/test/**")
                .nodes(List.of(
                        BackendNode.builder().id("node-1").uri("http://localhost:8081").status(NodeStatus.UP).build(),
                        BackendNode.builder().id("node-2").uri("http://localhost:8082").status(NodeStatus.DOWN).build()
                ))
                .build();

        registry.registerRoute(route);

        Optional<RouteConfig> matched = registry.findMatchingRoute("/api/v1/test/users/42");
        assertTrue(matched.isPresent());
        assertEquals("test-route", matched.get().getId());

        List<BackendNode> healthy = registry.getHealthyNodes(matched.get());
        assertEquals(1, healthy.size());
        assertEquals("node-1", healthy.get(0).getId());
    }
}
