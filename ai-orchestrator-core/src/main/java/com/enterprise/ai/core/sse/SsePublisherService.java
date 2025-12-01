package com.enterprise.ai.core.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SSE Publisher Service.
 * Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md Section 10.
 * 
 * SSE Message Types:
 * - message: Standard token stream
 * - done: End of stream
 * - error: Stream failure
 * - followup: Follow-up question
 * - unknown: Unknown scenario
 * 
 * Stream duration limit: 120 seconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsePublisherService {

    private final ObjectMapper objectMapper;

    private static final Duration MAX_STREAM_DURATION = Duration.ofSeconds(120);

    /**
     * Create a streaming response from token flux.
     * Appends tokens token-by-token for ChatGPT-like experience.
     */
    public Flux<ServerSentEvent<String>> streamTokens(Flux<String> tokenFlux, String sessionId) {
        return tokenFlux
                .timeout(MAX_STREAM_DURATION)
                .map(token -> createMessageEvent(token))
                .concatWith(Flux.just(createDoneEvent()))
                .onErrorResume(e -> {
                    log.error("SSE stream error for session {}: {}", sessionId, e.getMessage());
                    return Flux.just(createErrorEvent("Something went wrong while processing your request."));
                });
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
