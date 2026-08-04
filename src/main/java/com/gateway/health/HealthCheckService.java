package com.gateway.health;

import com.gateway.model.BackendNode;
import com.gateway.model.NodeStatus;
import com.gateway.routing.RouteRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final RouteRegistry routeRegistry;
    private final WebClient.Builder webClientBuilder;

    private static final int FAILURE_THRESHOLD_TO_DOWN = 3;
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    @Scheduled(fixedRate = 5000)
    public void performActiveHealthChecks() {
        WebClient client = webClientBuilder.build();

        Flux.fromIterable(routeRegistry.getAllRoutes())
                .flatMap(route -> Flux.fromIterable(route.getNodes()))
                .distinct(BackendNode::getId)
                .flatMap(node -> checkNodeHealth(client, node))
                .subscribe();
    }

    private Mono<Void> checkNodeHealth(WebClient client, BackendNode node) {
        String healthUrl = node.getUri() + "/";

        return client.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .timeout(HEALTH_TIMEOUT)
                .doOnSuccess(entity -> {
                    node.setLastChecked(Instant.now());
                    if (node.getStatus() != NodeStatus.UP) {
                        log.info("[HealthCheck] Node {} ({}) recovered! Status transition: {} -> UP",
                                node.getId(), node.getUri(), node.getStatus());
                    }
                    node.recordSuccess();
                })
                .doOnError(error -> {
                    node.setLastChecked(Instant.now());
                    NodeStatus previousStatus = node.getStatus();
                    node.recordFailure(FAILURE_THRESHOLD_TO_DOWN);

                    if (node.getStatus() == NodeStatus.DOWN && previousStatus != NodeStatus.DOWN) {
                        log.warn("[HealthCheck ALERT] Node {} ({}) marked DOWN after consecutive failures. Error: {}",
                                node.getId(), node.getUri(), error.getMessage());
                    }
                })
                .then();
    }
}
