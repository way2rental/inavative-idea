package com.enterprise.ai.core.sse;

import com.enterprise.ai.common.dto.StructuredChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE Publisher Service.
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md Section 10.
 * Per PRODUCTION CLOSURE TASKS CHUNK 3 - SSE STABILITY.
 * 
 * SSE Event Types (STRICT CONTRACT):
 * - start: Stream initialization event
 * - message: Standard token stream
 * - done: End of stream (ALWAYS emitted)
 * - error: Stream failure (user-safe message)
 * - followup: Follow-up question
 * - unknown: Unknown scenario with suggestions
 * 
 * STABILITY RULES:
 * - ALWAYS emit 'start' at stream beginning
 * - ALWAYS emit 'done' at stream end (even on error)
 * - NEVER leave frontend in loading state
 * - NEVER expose technical error details to user
 * 
 * Stream duration limit: 120 seconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsePublisherService {

    private final ObjectMapper objectMapper;

    private static final Duration MAX_STREAM_DURATION = Duration.ofSeconds(120);
    private static final String DEFAULT_ERROR_MESSAGE = "I apologize, but I encountered an issue processing your request. Please try again.";
    private static final String TIMEOUT_MESSAGE = "Your request is taking longer than expected. Please try again with a simpler query.";

    /**
     * Get user-safe error message from exception.
     * CHUNK 3: Never expose technical error details to frontend.
     * 
     * @param e The exception that occurred
     * @return User-friendly error message
     */
    public String getUserSafeErrorMessage(Throwable e) {
        if (e instanceof java.util.concurrent.TimeoutException) {
            return TIMEOUT_MESSAGE;
        }
        return DEFAULT_ERROR_MESSAGE;
    }

    /**
     * Create a streaming response from token flux with guaranteed stability.
     * 
     * CHUNK 3 STABILITY RULES:
     * 1. ALWAYS emit 'start' event first
     * 2. Append tokens token-by-token for ChatGPT-like experience
     * 3. ALWAYS emit 'done' event at end (even on error)
     * 4. On error, emit 'error' event with user-safe message
     * 5. Timeout after 120 seconds with user-friendly message
     */
    public Flux<ServerSentEvent<String>> streamTokens(Flux<String> tokenFlux, String sessionId) {
        AtomicBoolean hasEmittedStart = new AtomicBoolean(false);
        AtomicBoolean hasCompleted = new AtomicBoolean(false);
        
        return Flux.concat(
                // 1. ALWAYS emit start event first
                Flux.just(createStartEvent("Processing your request...")),
                
                // 2. Stream tokens as message events
                tokenFlux
                        .timeout(MAX_STREAM_DURATION)
                        .map(token -> createMessageEvent(token))
                        .doOnNext(event -> hasEmittedStart.set(true))
                        .doOnComplete(() -> hasCompleted.set(true))
                        .onErrorResume(e -> {
                            log.error("SSE stream error for session {}: {}", sessionId, e.getMessage());
                            hasCompleted.set(true);
                            
                            // Emit error event followed by done event (to not leave UI hanging)
                            return Flux.just(
                                    createErrorEvent(getUserSafeErrorMessage(e))
                            );
                        }),
                        
                // 3. ALWAYS emit done event at end
                Flux.defer(() -> {
                    if (!hasCompleted.get()) {
                        hasCompleted.set(true);
                    }
                    return Flux.just(createDoneEvent());
                })
        );
    }
    
    /**
     * Create a stable response stream with proper event lifecycle.
     * Ensures frontend never hangs in loading state.
     * 
     * @param contentMono Single content response to stream
     * @param sessionId Session identifier for logging
     * @return Stable SSE stream with start/message/done events
     */
    public Flux<ServerSentEvent<String>> streamSingleResponse(Mono<String> contentMono, String sessionId) {
        return Flux.concat(
                Flux.just(createStartEvent("Processing your request...")),
                contentMono
                        .timeout(MAX_STREAM_DURATION)
                        .map(content -> createMessageEvent(content))
                        .flux()
                        .onErrorResume(e -> {
                            log.error("SSE single response error for session {}: {}", sessionId, e.getMessage());
                            return Flux.just(createErrorEvent(getUserSafeErrorMessage(e)));
                        }),
                Flux.just(createDoneEvent())
        );
    }

    /**
     * Create a start event.
     * CHUNK 3 REQUIREMENT: ALWAYS emit at stream beginning.
     * Per spec: {"event": "start", "data": "Processing your request..."}
     */
    public ServerSentEvent<String> createStartEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("start")
                .data(message)
                .build();
    }

    /**
     * Create a standard message event.
     * Per spec: {"event": "message", "data": "Your transaction"}
     */
    public ServerSentEvent<String> createMessageEvent(String data) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(data)
                .build();
    }

    /**
     * Create a done event.
     * Per spec: {"event": "done"}
     */
    public ServerSentEvent<String> createDoneEvent() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("")
                .build();
    }

    /**
     * Create an error event.
     * Per spec: {"event": "error", "data": "Something went wrong..."}
     */
    public ServerSentEvent<String> createErrorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(message)
                .build();
    }

    /**
     * Create a follow-up event.
     * Per spec: {"event": "followup", "data": {...}}
     */
    public ServerSentEvent<String> createFollowUpEvent(String scenario, List<String> missingParams, String question) {
        try {
            FollowUpPayload payload = FollowUpPayload.builder()
                    .scenario(scenario)
                    .missingParams(missingParams)
                    .question(question)
                    .build();
            return ServerSentEvent.<String>builder()
                    .event("followup")
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create followup event: {}", e.getMessage());
            return createMessageEvent(question);
        }
    }

    /**
     * Create an unknown scenario event.
     * Per spec: Shows smart suggestions when scenario is UNKNOWN.
     */
    public ServerSentEvent<String> createUnknownEvent(String message, List<String> options) {
        try {
            UnknownPayload payload = UnknownPayload.builder()
                    .message(message)
                    .options(options)
                    .build();
            return ServerSentEvent.<String>builder()
                    .event("unknown")
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create unknown event: {}", e.getMessage());
            return createMessageEvent(message);
        }
    }

    /**
     * Create a progress event for long-running operations.
     */
    public ServerSentEvent<String> createProgressEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("progress")
                .data(message)
                .build();
    }

    /**
     * Create a session event to send session ID to frontend.
     * Frontend must capture this and use for subsequent requests.
     */
    public ServerSentEvent<String> createSessionEvent(String sessionId) {
        return ServerSentEvent.<String>builder()
                .event("session")
                .data(sessionId)
                .build();
    }

    /**
     * Create a response event for structured JSON payload.
     * This is used for the final formatted response from the LLM.
     * Per spec: {"event": "response", "data": "{\"type\":\"TABLE\",...}"}
     */
    public ServerSentEvent<String> createResponseEvent(String jsonData) {
        return ServerSentEvent.<String>builder()
                .event("response")
                .data(jsonData)
                .build();
    }

    /**
     * Create a data event with structured response.
     */
    public ServerSentEvent<String> createDataEvent(Map<String, Object> data) {
        try {
            return ServerSentEvent.<String>builder()
                    .event("data")
                    .data(objectMapper.writeValueAsString(data))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create data event: {}", e.getMessage());
            return createErrorEvent("Failed to process data");
        }
    }

    /**
     * Default unknown scenario options.
     * Per spec: Show smart suggestions for unknown queries.
     */
    public List<String> getDefaultUnknownOptions() {
        return List.of(
                "Check Transaction Status",
                "Check File Status",
                "Account Summary",
                "Balance Inquiry"
        );
    }

    /**
     * Default unknown message.
     */
    public String getDefaultUnknownMessage() {
        return "I didn't understand this yet. Here are some things I can help with:";
    }

    // ============================================
    // STRUCTURED RESPONSE SUPPORT (per STRUCTURED_CHAT_RESPONSE_UPGRADE.md)
    // ============================================

    /**
     * Create a 'response' event with structured JSON payload.
     * Per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 5.5:
     * - Phase 1: Status messages only (start, progress)
     * - Phase 2: ONE SINGLE JSON chunk as final response
     * 
     * This emits the final structured response as a single JSON object.
     * Frontend must parse this and render by type.
     */
    public ServerSentEvent<String> createStructuredResponseEvent(StructuredChatResponse response) {
        if (response == null) {
            log.warn("Null response passed to createStructuredResponseEvent, returning error event");
            return createErrorEvent("Empty response received");
        }
        try {
            return ServerSentEvent.<String>builder()
                    .event("response")
                    .data(objectMapper.writeValueAsString(response))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create structured response event: {}", e.getMessage());
            return createErrorEvent("Failed to format response");
        }
    }

    /**
     * Stream with TWO PHASE MODEL per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 5.5:
     * - Phase 1: Status messages (start, progress events)
     * - Phase 2: ONE final structured JSON response
     * 
     * @param statusMessages Progress/status messages to stream first
     * @param finalResponse The final structured response
     * @param sessionId Session identifier
     * @return Stable SSE stream with proper event lifecycle
     */
    public Flux<ServerSentEvent<String>> streamStructuredResponse(
            List<String> statusMessages,
            StructuredChatResponse finalResponse,
            String sessionId) {
        
        // Phase 1: Start event
        Flux<ServerSentEvent<String>> startFlux = Flux.just(createStartEvent("Processing your request..."));
        
        // Phase 1: Status/progress messages
        Flux<ServerSentEvent<String>> statusFlux = Flux.fromIterable(statusMessages)
                .delayElements(Duration.ofMillis(300))
                .map(this::createProgressEvent);
        
        // Phase 2: Single final structured response
        Flux<ServerSentEvent<String>> responseFlux = Flux.just(createStructuredResponseEvent(finalResponse));
        
        // Done event
        Flux<ServerSentEvent<String>> doneFlux = Flux.just(createDoneEvent());
        
        return Flux.concat(startFlux, statusFlux, responseFlux, doneFlux)
                .timeout(MAX_STREAM_DURATION)
                .onErrorResume(e -> {
                    log.error("SSE structured response error for session {}: {}", sessionId, e.getMessage());
                    return Flux.concat(
                            Flux.just(createStructuredResponseEvent(
                                    StructuredChatResponse.error(getUserSafeErrorMessage(e), sessionId)
                            )),
                            Flux.just(createDoneEvent())
                    );
                });
    }

    /**
     * Stream with reactive status and structured final response.
     * 
     * @param statusFlux Reactive flux of status messages
     * @param finalResponseMono Reactive mono of the final structured response
     * @param sessionId Session identifier
     * @return Stable SSE stream
     */
    public Flux<ServerSentEvent<String>> streamStructuredResponseReactive(
            Flux<String> statusFlux,
            Mono<StructuredChatResponse> finalResponseMono,
            String sessionId) {
        
        AtomicBoolean hasCompleted = new AtomicBoolean(false);
        
        return Flux.concat(
                // Start event
                Flux.just(createStartEvent("Processing your request...")),
                
                // Status/progress messages
                statusFlux
                        .map(this::createProgressEvent)
                        .onErrorResume(e -> {
                            log.warn("Status stream error for session {}: {}", sessionId, e.getMessage());
                            return Flux.empty();
                        }),
                
                // Final structured response
                finalResponseMono
                        .timeout(MAX_STREAM_DURATION)
                        .map(this::createStructuredResponseEvent)
                        .flux()
                        .doOnComplete(() -> hasCompleted.set(true))
                        .onErrorResume(e -> {
                            log.error("Final response error for session {}: {}", sessionId, e.getMessage());
                            hasCompleted.set(true);
                            return Flux.just(createStructuredResponseEvent(
                                    StructuredChatResponse.error(getUserSafeErrorMessage(e), sessionId)
                            ));
                        }),
                
                // Done event
                Flux.defer(() -> {
                    hasCompleted.set(true);
                    return Flux.just(createDoneEvent());
                })
        );
    }

    /**
     * Follow-up event payload.
     */
    @Data
    @Builder
    public static class FollowUpPayload {
        private String scenario;
        private List<String> missingParams;
        private String question;
    }

    /**
     * Unknown event payload.
     */
    @Data
    @Builder
    public static class UnknownPayload {
        private String message;
        private List<String> options;
    }
}
