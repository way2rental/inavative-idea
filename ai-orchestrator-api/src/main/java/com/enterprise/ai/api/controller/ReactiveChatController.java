package com.enterprise.ai.api.controller;

import com.enterprise.ai.api.service.PerformanceLoggingService;
import com.enterprise.ai.api.service.ReactiveChatService;
import com.enterprise.ai.common.dto.ChatRequest;
import com.enterprise.ai.common.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Reactive Chat Controller with SSE streaming support.
 * Provides non-blocking endpoints for chat processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/chat")
@RequiredArgsConstructor
@Tag(name = "Reactive Chat", description = "Non-blocking chat endpoints with SSE streaming")
public class ReactiveChatController {

    private final ReactiveChatService reactiveChatService;
    private final PerformanceLoggingService perfService;

    /**
     * Process chat request reactively (non-blocking).
     */
    @PostMapping
    @Operation(summary = "Process chat request (reactive)")
    public Mono<ResponseEntity<ChatResponse>> processChat(@RequestBody ChatRequest request) {
        log.info("Reactive chat request received from user: {}", request.getUserId());
        
        return reactiveChatService.processChatReactive(request)
                .map(ResponseEntity::ok)
                .doOnSuccess(r -> log.debug("Reactive chat response sent"))
                .doOnError(e -> log.error("Reactive chat error: {}", e.getMessage()));
    }

    /**
     * Process chat request with SSE streaming response.
     * Returns response token by token for real-time UI updates.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process chat request with SSE streaming")
    public Flux<String> processChatStreaming(@RequestBody ChatRequest request) {
        log.info("Streaming chat request received from user: {}", request.getUserId());
        
        return reactiveChatService.processChatStreaming(request)
                .doOnSubscribe(s -> log.debug("Streaming started"))
                .doOnComplete(() -> log.debug("Streaming completed"))
                .doOnError(e -> log.error("Streaming error: {}", e.getMessage()));
    }

    /**
     * SSE endpoint for keeping connection alive and receiving updates.
     */
    @GetMapping(value = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to session events (SSE)")
    public Flux<String> subscribeToSessionEvents(@PathVariable String sessionId) {
        log.info("SSE subscription for session: {}", sessionId);
        
        // Keep-alive ping every 30 seconds
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> "ping")
                .doOnSubscribe(s -> log.debug("SSE connection established for session: {}", sessionId))
                .doOnCancel(() -> log.debug("SSE connection closed for session: {}", sessionId));
    }

    /**
     * Get performance metrics for recent executions.
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get performance metrics")
    public ResponseEntity<Map<String, PerformanceLoggingService.ExecutionMetrics>> getMetrics() {
        return ResponseEntity.ok(perfService.getAllMetrics());
    }

    /**
     * Get performance metrics for a specific execution.
     */
    @GetMapping("/metrics/{executionId}")
    @Operation(summary = "Get metrics for specific execution")
    public ResponseEntity<PerformanceLoggingService.ExecutionMetrics> getExecutionMetrics(
            @PathVariable String executionId) {
        PerformanceLoggingService.ExecutionMetrics metrics = perfService.getMetrics(executionId);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }
}
