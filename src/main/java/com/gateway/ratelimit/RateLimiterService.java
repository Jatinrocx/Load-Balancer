package com.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimiterLuaScript;

    // Fallback in-memory token buckets if Redis is down/unreachable
    private final Map<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    public Mono<RateLimiterResponse> isAllowed(String key, int capacity, int periodSeconds) {
        String tokensKey = "rate_limiter:" + key + ":tokens";
        String timestampKey = "rate_limiter:" + key + ":timestamp";
        long currentTimestamp = Instant.now().getEpochSecond();

        List<String> keys = List.of(tokensKey, timestampKey);

        return redisTemplate.execute(
                rateLimiterLuaScript,
                keys,
                List.of(String.valueOf(capacity), String.valueOf(periodSeconds), String.valueOf(currentTimestamp), "1")
        )
        .next()
        .map(resultList -> {
            Long allowedFlag = ((Number) resultList.get(0)).longValue();
            Long remainingTokens = ((Number) resultList.get(1)).longValue();
            Long bucketCapacity = ((Number) resultList.get(2)).longValue();

            return RateLimiterResponse.builder()
                    .allowed(allowedFlag == 1)
                    .remainingTokens(remainingTokens)
                    .capacity(bucketCapacity)
                    .build();
        })
        .onErrorResume(error -> {
            log.warn("[RateLimiter] Redis connection error (using in-memory fallback): {}", error.getMessage());
            return Mono.just(evaluateInMemoryFallback(key, capacity, periodSeconds));
        });
    }

    private synchronized RateLimiterResponse evaluateInMemoryFallback(String key, int capacity, int periodSeconds) {
        long now = Instant.now().getEpochSecond();
        InMemoryBucket bucket = inMemoryBuckets.computeIfAbsent(key, k -> new InMemoryBucket(capacity, now));

        // Refill tokens proportionally to elapsed seconds in period
        long delta = Math.max(0, now - bucket.lastRefreshed);
        double tokensToAdd = ((double) delta * capacity) / periodSeconds;
        bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
        bucket.lastRefreshed = now;

        boolean allowed = bucket.tokens >= 1.0;
        if (allowed) {
            bucket.tokens -= 1.0;
        }

        return RateLimiterResponse.builder()
                .allowed(allowed)
                .remainingTokens((long) Math.floor(bucket.tokens))
                .capacity(capacity)
                .build();
    }

    private static class InMemoryBucket {
        double tokens;
        long lastRefreshed;

        InMemoryBucket(long capacity, long now) {
            this.tokens = capacity;
            this.lastRefreshed = now;
        }
    }
}
