package com.gateway.circuitbreaker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerConfig {
    @Builder.Default
    private int slidingWindowSize = 4; // 4 requests sliding window
    @Builder.Default
    private float failureRateThresholdPercentage = 50.0f; // 50% (2 failures trips circuit)
    @Builder.Default
    private long waitDurationInOpenStateMs = 5000; // 5 seconds in OPEN state
    @Builder.Default
    private int permittedHalfOpenCalls = 2;
}
