package com.gateway.circuitbreaker;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CircuitBreakerRegistry {

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.builder().build();

    public CircuitBreaker getOrCreate(String routeId) {
        return circuitBreakers.computeIfAbsent(routeId, id -> new CircuitBreaker(id, defaultConfig));
    }

    public Collection<CircuitBreaker> getAllCircuitBreakers() {
        return circuitBreakers.values();
    }
}
