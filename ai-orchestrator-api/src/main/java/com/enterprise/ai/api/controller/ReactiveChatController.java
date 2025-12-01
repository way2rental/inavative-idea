package com.enterprise.ai.api.controller;

import com.enterprise.ai.api.service.PerformanceLoggingService;
import com.enterprise.ai.api.service.ReactiveChatService;
import com.enterprise.ai.common.dto.ChatRequest;
import com.enterprise.ai.common.dto.ChatResponse;
import com.enterprise.ai.core.sse.SsePublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reactive Chat Controller with SSE streaming support.
 * Provides non-blocking endpoints for chat processing.
 * 
 * CHUNK 3 SSE STABILITY IMPLEMENTATION:
 * - ALWAYS emits 'start' event at stream beginning
 * - Emits 'message' events for each token/chunk
 * - ALWAYS emits 'done' event at end (even on error)
 * - On error, emits 'error' event with user-safe message
 * - NEVER leaves frontend in loading state
 * - 120 second timeout with graceful fallback
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/chat")
@RequiredArgsConstructor
@Tag(name = "Reactive Chat", description = "Non-blocking chat endpoints with SSE streaming")
public class ReactiveChatController {

    private final ReactiveChatService reactiveChatService;
    private final PerformanceLoggingService perfService;
    private final SsePublisherService ssePublisherService;
    
    private static final Duration MAX_STREAM_DURATION = Duration.ofSeconds(120);
    private static final String AUTH_ERROR_MESSAGE = "Authentication required. Please log in again.";
    private static final String GENERIC_ERROR_MESSAGE = "I apologize, but I encountered an issue processing your request. Please try again.";

    /**
     * Process chat request reactively (non-blocking).
     */
    @PostMapping
    @Operation(summary = "Process chat request (reactive)")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<ChatResponse>> processChat(@RequestBody ChatRequest request) {
        log.info("Reactive chat request received from user: {}", request.getUserId());
        
        return reactiveChatService.processChatReactive(request)
                .map(ResponseEntity::ok)
                .doOnSuccess(r -> log.debug("Reactive chat response sent"))
                .doOnError(e -> log.error("Reactive chat error: {}", e.getMessage()));
    }

    /**
     * Process chat request with SSE streaming response using ServerSentEvent wrapper.
     * 
     * CHUNK 3 SSE STABILITY CONTRACT:
     * 1. ALWAYS emit 'start' event first: event: start, data: "Processing your request..."
     * 2. For each token/chunk: event: message, data: "<token>"
     * 3. ALWAYS emit 'done' event at end: event: done
     * 4. On error: event: error, data: "User-safe message"
     * 5. NEVER send raw technical errors to frontend
     * 6. NEVER leave frontend in loading state
     *
     * IMPORTANT: Using ServerSentEvent wrapper ensures Spring Security doesn't interfere
     * with the streaming response after it's been committed.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process chat request with SSE streaming")
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<String>> processChatStreaming(@RequestBody ChatRequest request) {
        log.info("Streaming chat request received from user: {}", request.getUserId());
        
        // Verify authentication before starting stream
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.error("No authentication found for streaming request");
            // CHUNK 3: Emit proper event sequence even for auth errors
            return Flux.concat(
                Flux.just(ssePublisherService.createStartEvent("Checking authentication...")),
                Flux.just(ssePublisherService.createErrorEvent(AUTH_ERROR_MESSAGE)),
                Flux.just(ssePublisherService.createDoneEvent())
            );
        }

        AtomicBoolean streamCompleted = new AtomicBoolean(false);
        
        // CHUNK 3: Start with 'start' event, then stream, then always 'done'
        return Flux.concat(
                // 1. ALWAYS emit start event first
                Flux.just(ssePublisherService.createStartEvent("Processing your request...")),
                
                // 2. Process the chat with streaming, with intelligent event type detection
                reactiveChatService.processChatStreaming(request)
                        .map(chunk -> {
                            // Log chunk preview for debugging
                            String preview = chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk;
                            log.debug("Processing chunk (preview): {}", preview);

                            // Detect event type based on marker prefix
                            if (chunk.startsWith("[PROGRESS]")) {
                                log.debug("Detected PROGRESS event");
                                return ssePublisherService.createProgressEvent(chunk.substring(10)); // Remove marker
                            } else if (chunk.startsWith("[RESPONSE]")) {
                                log.debug("Detected RESPONSE event, length: {}", chunk.length() - 10);
                                return ssePublisherService.createResponseEvent(chunk.substring(10)); // Remove marker
                            } else {
                                log.debug("Detected MESSAGE event (fallback)");
                                return ssePublisherService.createMessageEvent(chunk);
                            }
                        })
                        .doOnSubscribe(s -> log.debug("Streaming started for user: {}", request.getUserId()))
                        .doOnComplete(() -> {
                            log.debug("Streaming completed for user: {}", request.getUserId());
                            streamCompleted.set(true);
                        })
                        .doOnError(e -> {
                            log.error("Streaming error for user {}: {}", request.getUserId(), e.getMessage());
                            streamCompleted.set(true);
                        })
                        .timeout(MAX_STREAM_DURATION)
                        .onErrorResume(e -> {
                            log.error("Fatal streaming error for user {}: {}", request.getUserId(), e.getMessage(), e);
                            streamCompleted.set(true);
                            // CHUNK 3: Use centralized error message helper from SsePublisherService
                            return Flux.just(ssePublisherService.createErrorEvent(
                                    ssePublisherService.getUserSafeErrorMessage(e)));
                        }),
                        
                // 3. ALWAYS emit done event at end (CHUNK 3 CRITICAL REQUIREMENT)
                Flux.defer(() -> {
                    log.debug("Emitting done event for user: {}", request.getUserId());
                    return Flux.just(ssePublisherService.createDoneEvent());
                })
        );
    }

    /**
     * SSE endpoint for keeping connection alive and receiving updates.
     */
    @GetMapping(value = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to session events (SSE)")
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<String>> subscribeToSessionEvents(@PathVariable String sessionId) {
        log.info("SSE subscription for session: {}", sessionId);
        
        // Keep-alive ping every 30 seconds
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("ping")
                        .build())
                .doOnSubscribe(s -> log.debug("SSE connection established for session: {}", sessionId))
                .doOnCancel(() -> log.debug("SSE connection closed for session: {}", sessionId));
    }

    /**
     * Get performance metrics for recent executions.
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get performance metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, PerformanceLoggingService.ExecutionMetrics>> getMetrics() {
        return ResponseEntity.ok(perfService.getAllMetrics());
    }

    /**
     * Get performance metrics for a specific execution.
     */
    @GetMapping("/metrics/{executionId}")
    @Operation(summary = "Get metrics for specific execution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PerformanceLoggingService.ExecutionMetrics> getExecutionMetrics(
            @PathVariable String executionId) {
        PerformanceLoggingService.ExecutionMetrics metrics = perfService.getMetrics(executionId);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }
}
