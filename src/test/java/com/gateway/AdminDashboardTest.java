package com.gateway;

import com.gateway.admin.AdminDashboardController;
import com.gateway.circuitbreaker.CircuitBreakerRegistry;
import com.gateway.metrics.MetricsCollector;
import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import com.gateway.model.RouteConfig;
import com.gateway.routing.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AdminDashboardTest {

    private RouteRegistry routeRegistry;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private MetricsCollector metricsCollector;
    private AdminDashboardController controller;

    @BeforeEach
    void setUp() {
        routeRegistry = new RouteRegistry();
        circuitBreakerRegistry = new CircuitBreakerRegistry();
        metricsCollector = new MetricsCollector();

        RouteConfig route = RouteConfig.builder()
                .id("test-route")
                .pathPattern("/api/test/**")
                .loadBalancerAlgorithm("ROUND_ROBIN")
                .nodes(List.of(BackendNode.builder().id("n1").uri("http://localhost:8081").status(NodeStatus.UP).build()))
                .build();
        routeRegistry.registerRoute(route);

        controller = new AdminDashboardController(routeRegistry, circuitBreakerRegistry, metricsCollector);
    }

    @Test
    void testDynamicAlgorithmUpdate() {
        controller.updateAlgorithm("test-route", "LEAST_CONNECTIONS").block();
        assertEquals("LEAST_CONNECTIONS", routeRegistry.getAllRoutes().iterator().next().getLoadBalancerAlgorithm());
    }

    @Test
    void testDynamicNodeWeightUpdate() {
        controller.updateNodeWeight("test-route", "n1", 5).block();
        assertEquals(5, routeRegistry.getAllRoutes().iterator().next().getNodes().get(0).getWeight());
    }

    @Test
    void testMetricsSseStream() {
        StepVerifier.create(controller.streamMetrics().take(1))
                .assertNext(sse -> {
                    assertNotNull(sse.data());
                    Map<String, Object> data = sse.data();
                    assertTrue(data.containsKey("currentRps"));
                    assertTrue(data.containsKey("routes"));
                })
                .verifyComplete();
    }
}
