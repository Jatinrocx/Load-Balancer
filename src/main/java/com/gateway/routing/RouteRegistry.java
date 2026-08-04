package com.gateway.routing;

import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import com.gateway.model.RouteConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class RouteRegistry {

    private final Map<String, RouteConfig> routes = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public void registerRoute(RouteConfig route) {
        routes.put(route.getId(), route);
    }

    public void removeRoute(String routeId) {
        routes.remove(routeId);
    }

    public Collection<RouteConfig> getAllRoutes() {
        return routes.values();
    }

    public Optional<RouteConfig> findMatchingRoute(String path) {
        return routes.values().stream()
                .filter(route -> pathMatcher.match(route.getPathPattern(), path))
                .findFirst();
    }

    public List<BackendNode> getHealthyNodes(RouteConfig route) {
        if (route.getNodes() == null) {
            return Collections.emptyList();
        }
        return route.getNodes().stream()
                .filter(node -> node.getStatus() != NodeStatus.DOWN)
                .collect(Collectors.toList());
    }
}
