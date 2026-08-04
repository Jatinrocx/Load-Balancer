package com.gateway.circuitbreaker;

public enum CircuitState {
    CLOSED,    // Normal traffic flow
    OPEN,      // Short-circuited / Requests immediately rejected
    HALF_OPEN  // Trial probe requests permitted to evaluate recovery
}
