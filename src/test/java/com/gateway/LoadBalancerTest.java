package com.gateway;

import com.gateway.loadbalancer.*;
import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class LoadBalancerTest {

    private BackendNode node1;
    private BackendNode node2;
    private BackendNode node3;
    private List<BackendNode> nodes;

    @BeforeEach
    void setUp() {
        node1 = BackendNode.builder().id("node-1").uri("http://localhost:8081").weight(3).status(NodeStatus.UP).build();
        node2 = BackendNode.builder().id("node-2").uri("http://localhost:8082").weight(1).status(NodeStatus.UP).build();
        node3 = BackendNode.builder().id("node-3").uri("http://localhost:8083").weight(1).status(NodeStatus.UP).build();
        nodes = List.of(node1, node2, node3);
    }

    @Test
    void testRoundRobinLoadBalancer() {
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        assertEquals("node-1", lb.chooseNode(nodes, exchange).get().getId());
        assertEquals("node-2", lb.chooseNode(nodes, exchange).get().getId());
        assertEquals("node-3", lb.chooseNode(nodes, exchange).get().getId());
        assertEquals("node-1", lb.chooseNode(nodes, exchange).get().getId());
    }

    @Test
    void testLeastConnectionsLoadBalancer() {
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        // Simulate active connections: node1 has 5, node2 has 2, node3 has 10
        node1.getActiveConnections().set(5);
        node2.getActiveConnections().set(2);
        node3.getActiveConnections().set(10);

        Optional<BackendNode> chosen = lb.chooseNode(nodes, exchange);
        assertTrue(chosen.isPresent());
        assertEquals("node-2", chosen.get().getId()); // Node 2 has least active connections (2)
    }

    @Test
    void testIpHashLoadBalancerStickySessions() {
        IpHashLoadBalancer lb = new IpHashLoadBalancer();

        ServerWebExchange client1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").header("X-Forwarded-For", "192.168.1.100").build()
        );
        ServerWebExchange client2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").header("X-Forwarded-For", "10.0.0.5").build()
        );

        // Multiple calls from same client IP must return the exact same node
        String nodeForClient1_first = lb.chooseNode(nodes, client1).get().getId();
        String nodeForClient1_second = lb.chooseNode(nodes, client1).get().getId();
        assertEquals(nodeForClient1_first, nodeForClient1_second);

        String nodeForClient2_first = lb.chooseNode(nodes, client2).get().getId();
        String nodeForClient2_second = lb.chooseNode(nodes, client2).get().getId();
        assertEquals(nodeForClient2_first, nodeForClient2_second);
    }

    @Test
    void testWeightedRoundRobinDistribution() {
        WeightedRoundRobinLoadBalancer lb = new WeightedRoundRobinLoadBalancer();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        // Weights: node1=3, node2=1, node3=1 (Total=5). Over 5 requests, node1 should get 3 calls.
        int node1Count = 0;
        for (int i = 0; i < 5; i++) {
            if ("node-1".equals(lb.chooseNode(nodes, exchange).get().getId())) {
                node1Count++;
            }
        }
        assertEquals(3, node1Count);
    }
}
