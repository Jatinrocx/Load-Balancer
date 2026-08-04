package com.gateway.proxy;

import com.gateway.circuitbreaker.CircuitBreaker;
import com.gateway.metrics.MetricsCollector;
import com.gateway.model.BackendNode;
import com.gateway.model.RouteConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

@Slf4j
@Component
public class ProxyHandler {

    private final WebClient webClient;
    private final MetricsCollector metricsCollector;

    private static final Set<String> DISALLOWED_HEADERS = Set.of(
            "host", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade"
    );

    public ProxyHandler(WebClient.Builder webClientBuilder, MetricsCollector metricsCollector) {
        this.webClient = webClientBuilder.build();
        this.metricsCollector = metricsCollector;
    }

    public Mono<Void> forwardRequest(ServerWebExchange exchange, RouteConfig route, BackendNode targetNode, CircuitBreaker cb) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String incomingPath = request.getURI().getRawPath();
        String targetPath = computeTargetPath(incomingPath, route);
        String queryString = request.getURI().getRawQuery();

        String destinationUrl = targetNode.getUri() + targetPath + (queryString != null ? "?" + queryString : "");
        URI destinationUri = URI.create(destinationUrl);

        HttpMethod httpMethod = request.getMethod();
        log.info("[Proxy] Forwarding {} {} -> {} (Node: {})", httpMethod, incomingPath, destinationUri, targetNode.getId());

        targetNode.incrementConnections();

        return webClient.method(httpMethod)
                .uri(destinationUri)
                .headers(headers -> copyHeaders(request.getHeaders(), headers))
                .body(request.getBody(), DataBuffer.class)
                .exchangeToMono(clientResponse -> {
                    response.setStatusCode(clientResponse.statusCode());

                    clientResponse.headers().asHttpHeaders().forEach((headerName, headerValues) -> {
                        if (!DISALLOWED_HEADERS.contains(headerName.toLowerCase())) {
                            response.getHeaders().addAll(headerName, headerValues);
                        }
                    });

                    response.getHeaders().add("X-Gateway-Node-Id", targetNode.getId());
                    response.getHeaders().add("X-Circuit-Breaker-State", cb.getState().name());

                    if (clientResponse.statusCode().is5xxServerError()) {
                        cb.onFailure();
                        targetNode.recordFailure(3);
                        metricsCollector.recordFailure();
                    } else {
                        cb.onSuccess();
                        targetNode.recordSuccess();
                        metricsCollector.recordSuccess();
                    }

                    return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                })
                .doOnSuccess(v -> targetNode.decrementConnections())
                .doOnError(error -> {
                    log.error("[Proxy Error] Failed to forward request to {}: {}", destinationUrl, error.getMessage());
                    cb.onFailure();
                    targetNode.recordFailure(3);
                    metricsCollector.recordFailure();
                    targetNode.decrementConnections();
                });
    }

    private String computeTargetPath(String incomingPath, RouteConfig route) {
        if (!route.isStripPrefix()) {
            return incomingPath;
        }
        String prefix = route.getPathPattern().replace("/**", "").replace("/*", "");
        if (incomingPath.startsWith(prefix)) {
            String stripped = incomingPath.substring(prefix.length());
            return stripped.isEmpty() ? "/" : stripped;
        }
        return incomingPath;
    }

    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        source.forEach((headerName, headerValues) -> {
            if (!DISALLOWED_HEADERS.contains(headerName.toLowerCase())) {
                target.addAll(headerName, headerValues);
            }
        });
    }
}
