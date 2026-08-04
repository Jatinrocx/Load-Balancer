package com.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public RedisScript<List> rateLimiterLuaScript() {
        ClassPathResource resource = new ClassPathResource("scripts/request_rate_limiter.lua");
        return RedisScript.of(resource, List.class);
    }
}
