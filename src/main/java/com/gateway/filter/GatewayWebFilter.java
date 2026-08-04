package com.gateway.filter;

import com.gateway.circuitbreaker.CircuitBreaker;
import com.gateway.circuitbreaker.CircuitBreakerRegistry;
import com.gateway.loadbalancer.LoadBalancer;
import com.gateway.loadbalancer.LoadBalancerFactory;
import com.gateway.metrics.MetricsCollector;
import com.gateway.model.BackendNode;
import com.gateway.model.RouteConfig;
import com.gateway.proxy.ProxyHandler;
import com.gateway.ratelimit.RateLimiterService;
import com.gateway.routing.RouteRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayWebFilter implements WebFilter {

    private final RouteRegistry routeRegistry;
    private final ProxyHandler proxyHandler;
    private final LoadBalancerFactory loadBalancerFactory;
    private final RateLimiterService rateLimiterService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MetricsCollector metricsCollector;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();

        // Bypass gateway proxy logic for admin dashboard / actuator endpoints / mock controller
        if (path.startsWith("/admin") || path.startsWith("/actuator") || path.startsWith("/mock") || path.equals("/favicon.ico")) {
            return chain.filter(exchange);
        }

        metricsCollector.recordRequest();

        // 1. Find matching route
        Optional<RouteConfig> matchingRouteOpt = routeRegistry.findMatchingRoute(path);
        if (matchingRouteOpt.isEmpty()) {
            log.warn("[Gateway] No route matching path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(
                            "{\"error\": \"No route configured for path\", \"path\": \"%s\"}".formatted(path).getBytes(StandardCharsets.UTF_8)
                    )
            ));
        }

        RouteConfig route = matchingRouteOpt.get();

        // 2. Distributed Per-Client Rate Limiting Check (e.g. 10 requests per 60 seconds)
        String clientIp = extractClientIp(exchange);
        String rateLimitKey = clientIp + ":" + route.getId();

        return rateLimiterService.isAllowed(rateLimitKey, route.getRateLimitRequests(), route.getRateLimitWindowSeconds())
                .flatMap(rateResponse -> {
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(rateResponse.getCapacity()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(rateResponse.getRemainingTokens()));

                    if (!rateResponse.isAllowed()) {
                        metricsCollector.recordRateLimited();
                        log.warn("[RateLimiter EXCEEDED] IP {} rate limited on route {} (Limit: {} req/{}s)", 
                                clientIp, route.getId(), route.getRateLimitRequests(), route.getRateLimitWindowSeconds());
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return exchange.getResponse().writeWith(Mono.just(
                                exchange.getResponse().bufferFactory().wrap(
                                        "{\"error\": \"429 Too Many Requests\", \"message\": \"Rate limit exceeded (10 requests per minute limit). Please try again later.\"}".getBytes(StandardCharsets.UTF_8)
                                )
                        ));
                    }

                    // 3. Circuit Breaker Check
                    CircuitBreaker cb = circuitBreakerRegistry.getOrCreate(route.getId());
                    if (!cb.allowRequest()) {
                        metricsCollector.recordShortCircuited();
                        log.warn("[CircuitBreaker TRIPPED] Short-circuiting request for route: {}", route.getId());
                        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        exchange.getResponse().getHeaders().add("X-Circuit-Breaker-State", cb.getState().name());
                        return exchange.getResponse().writeWith(Mono.just(
                                exchange.getResponse().bufferFactory().wrap(
                                        "{\"error\": \"503 Service Unavailable\", \"message\": \"Circuit Breaker OPEN for service '%s'\"}".formatted(route.getId()).getBytes(StandardCharsets.UTF_8)
                                )
                        ));
                    }

                    // 4. Find healthy backend nodes for this route
                    List<BackendNode> healthyNodes = routeRegistry.getHealthyNodes(route);
                    if (healthyNodes.isEmpty()) {
                        log.error("[Gateway] No healthy backends available for route: {}", route.getId());
                        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return exchange.getResponse().writeWith(Mono.just(
                                exchange.getResponse().bufferFactory().wrap(
                                        "{\"error\": \"503 Service Unavailable\", \"message\": \"No healthy backend instances available\"}".getBytes(StandardCharsets.UTF_8)
                                )
                        ));
                    }

                    // 5. Dynamic L7 Load Balancing strategy selection
                    LoadBalancer strategy = loadBalancerFactory.getLoadBalancer(route.getLoadBalancerAlgorithm());
                    Optional<BackendNode> selectedNodeOpt = strategy.chooseNode(healthyNodes, exchange);

                    if (selectedNodeOpt.isEmpty()) {
                        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        return exchange.getResponse().setComplete();
                    }

                    BackendNode selectedNode = selectedNodeOpt.get();

                    // 6. Forward request asynchronously
                    return proxyHandler.forwardRequest(exchange, route, selectedNode, cb);
                });
    }

    private String extractClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }
}
