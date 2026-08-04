package com.gateway;

import com.gateway.ratelimit.RateLimiterResponse;
import com.gateway.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private RedisScript<List> luaScript;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(ReactiveStringRedisTemplate.class);
        luaScript = Mockito.mock(RedisScript.class);
        rateLimiterService = new RateLimiterService(redisTemplate, luaScript);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRateLimiterAllowedWhenTokensAvailable() {
        Mockito.when(redisTemplate.execute(Mockito.eq(luaScript), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(Flux.just(List.of(1L, 9L, 10L)));

        RateLimiterResponse response = rateLimiterService.isAllowed("user-1", 10, 60).block();

        assertNotNull(response);
        assertTrue(response.isAllowed());
        assertEquals(9, response.getRemainingTokens());
        assertEquals(10, response.getCapacity());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRateLimiterRejectedWhenExceeded() {
        Mockito.when(redisTemplate.execute(Mockito.eq(luaScript), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(Flux.just(List.of(0L, 0L, 10L)));

        RateLimiterResponse response = rateLimiterService.isAllowed("user-1", 10, 60).block();

        assertNotNull(response);
        assertFalse(response.isAllowed());
        assertEquals(0, response.getRemainingTokens());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFallbackInMemoryWhenRedisFails() {
        Mockito.when(redisTemplate.execute(Mockito.eq(luaScript), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis unavailable")));

        // Capacity of 2 tokens in a 60 second window
        RateLimiterResponse first = rateLimiterService.isAllowed("fallback-client", 2, 60).block();
        RateLimiterResponse second = rateLimiterService.isAllowed("fallback-client", 2, 60).block();
        RateLimiterResponse third = rateLimiterService.isAllowed("fallback-client", 2, 60).block();

        assertTrue(first.isAllowed());
        assertTrue(second.isAllowed());
        assertFalse(third.isAllowed()); // 3rd request should be blocked by fallback memory bucket
    }
}
