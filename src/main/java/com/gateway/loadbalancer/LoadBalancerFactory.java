package com.gateway.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoadBalancerFactory {

    private final Map<String, LoadBalancer> strategies = new ConcurrentHashMap<>();
    private final LoadBalancer defaultStrategy;

    public LoadBalancerFactory(List<LoadBalancer> loadBalancerList) {
        LoadBalancer defaultLb = null;
        for (LoadBalancer lb : loadBalancerList) {
            String algoName = lb.getAlgorithmName().toUpperCase();
            strategies.put(algoName, lb);
            log.info("[LoadBalancerFactory] Registered algorithm strategy: {}", algoName);
            if ("ROUND_ROBIN".equals(algoName)) {
                defaultLb = lb;
            }
        }
        this.defaultStrategy = defaultLb != null ? defaultLb : loadBalancerList.get(0);
    }

    public LoadBalancer getLoadBalancer(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            return defaultStrategy;
        }
        return strategies.getOrDefault(algorithmName.toUpperCase(), defaultStrategy);
    }
}
