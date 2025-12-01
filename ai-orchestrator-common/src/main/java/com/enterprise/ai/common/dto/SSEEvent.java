package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Enterprise SSE Event DTO for streaming responses.
 * Provides structured event format with type, data, and metadata.
 * 
 * Event Types:
 * - INTENT: Intent detection result
 * - PROGRESS: Processing progress update
 * - DATA: Scenario execution data
 * - RESPONSE: Formatted response chunk
 * - ERROR: Error information
 * - COMPLETE: Stream completion signal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSEEvent {

    /**
     * Event type for client-side handling
     */
    private EventType type;

    /**
     * Main content/data of the event
     */
    private String data;

    /**
     * Event sequence number for ordering
     */
    private Integer sequence;

    /**
     * Timestamp when event was generated
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Event metadata
     */
    private EventMeta meta;

    /**
     * Types of SSE events
     */
    public enum EventType {
        INTENT,      // Intent detection completed
        PROGRESS,    // Progress update
        DATA,        // Raw data from scenario execution
        RESPONSE,    // Formatted response chunk
        FOLLOWUP,    // Follow-up question needed
        CONFIRM,     // Confirmation needed
        CLARIFY,     // Clarification needed
        ERROR,       // Error occurred
        COMPLETE     // Stream completed
    }

    /**
     * Event metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventMeta {
        private String executionId;
        private String sessionId;
        private String scenario;
        private Double confidence;
        private String reasoning;
        private Long executionTimeMs;
        private Integer tokenCount;
        private Map<String, Object> additionalData;
    }

    /**
     * Create a progress event
     */
    public static SSEEvent progress(String message, int sequence) {
        return SSEEvent.builder()
                .type(EventType.PROGRESS)
                .data(message)
                .sequence(sequence)
                .build();
    }

    /**
     * Create an intent event
     */
    public static SSEEvent intent(String scenario, double confidence, String reasoning, int sequence) {
        return SSEEvent.builder()
                .type(EventType.INTENT)
                .data(scenario)
                .sequence(sequence)
                .meta(EventMeta.builder()
                        .scenario(scenario)
                        .confidence(confidence)
                        .reasoning(reasoning)
                        .build())
                .build();
    }

    /**
     * Create a response chunk event
     */
    public static SSEEvent response(String chunk, int sequence) {
        return SSEEvent.builder()
                .type(EventType.RESPONSE)
                .data(chunk)
                .sequence(sequence)
                .build();
    }

    /**
     * Create an error event
     */
    public static SSEEvent error(String message, int sequence) {
        return SSEEvent.builder()
                .type(EventType.ERROR)
                .data(message)
                .sequence(sequence)
                .build();
    }

    /**
     * Create a complete event
     */
    public static SSEEvent complete(String sessionId, String executionId, long executionTimeMs, int sequence) {
        return SSEEvent.builder()
                .type(EventType.COMPLETE)
                .data("Stream completed")
                .sequence(sequence)
                .meta(EventMeta.builder()
                        .sessionId(sessionId)
                        .executionId(executionId)
                        .executionTimeMs(executionTimeMs)
                        .build())
                .build();
    }

    /**
     * Create a follow-up event
     */
    public static SSEEvent followup(String question, String scenario, java.util.List<String> missingParams, int sequence) {
        return SSEEvent.builder()
                .type(EventType.FOLLOWUP)
                .data(question)
                .sequence(sequence)
                .meta(EventMeta.builder()
                        .scenario(scenario)
                        .additionalData(Map.of("missingParams", missingParams))
                        .build())
                .build();
    }

    /**
     * Create a confirmation event
     */
    public static SSEEvent confirm(String question, String scenario, Map<String, Object> params, int sequence) {
        return SSEEvent.builder()
                .type(EventType.CONFIRM)
                .data(question)
                .sequence(sequence)
                .meta(EventMeta.builder()
                        .scenario(scenario)
                        .additionalData(params)
                        .build())
                .build();
    }

    /**
     * Create a clarification event
     */
    public static SSEEvent clarify(String question, java.util.List<String> options, int sequence) {
        return SSEEvent.builder()
                .type(EventType.CLARIFY)
                .data(question)
                .sequence(sequence)
                .meta(EventMeta.builder()
                        .additionalData(Map.of("options", options))
                        .build())
                .build();
    }
}
