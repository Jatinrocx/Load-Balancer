package com.gateway.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimiterResponse {
    private boolean allowed;
    private long remainingTokens;
    private long capacity;
}
