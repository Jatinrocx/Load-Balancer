package com.gateway.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mock")
public class MockBackendController {

    @GetMapping("/users/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> getUser(
            @PathVariable String id,
            @RequestHeader Map<String, String> headers) {
        log.info("[Mock Backend] Received GET request for User ID: {}", id);
        return Mono.just(ResponseEntity.ok(Map.of(
                "userId", id,
                "name", "Jane Doe",
                "role", "Senior Software Engineer",
                "servedByMockNode", "mock-node-8080",
                "requestHeadersReceived", headers
        )));
    }

    @PostMapping("/users")
    public Mono<ResponseEntity<Map<String, Object>>> createUser(@RequestBody Map<String, Object> body) {
        log.info("[Mock Backend] Received POST request to create user: {}", body);
        return Mono.just(ResponseEntity.status(201).body(Map.of(
                "status", "CREATED",
                "user", body
        )));
    }
}
