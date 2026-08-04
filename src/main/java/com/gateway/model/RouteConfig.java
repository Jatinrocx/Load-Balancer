package com.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteConfig {
    private String id;
    private String pathPattern; // e.g. "/api/v1/users/**"
    @Builder.Default
    private boolean stripPrefix = true;
    @Builder.Default
    private String loadBalancerAlgorithm = "ROUND_ROBIN"; // ROUND_ROBIN, LEAST_CONNECTIONS, WEIGHTED, IP_HASH
    @Builder.Default
    private int rateLimitRequests = 10; // e.g. 10 requests per minute
    @Builder.Default
    private int rateLimitWindowSeconds = 60; // 60 seconds (1 minute window)
    @Builder.Default
    private List<BackendNode> nodes = new ArrayList<>();
}
