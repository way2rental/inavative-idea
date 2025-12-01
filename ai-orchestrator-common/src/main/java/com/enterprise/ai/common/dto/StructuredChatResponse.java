package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured Chat Response DTO per STRUCTURED_CHAT_RESPONSE_UPGRADE.md spec.
 * 
 * This DTO ensures all chat responses have a known, typed structure that the frontend
 * can render without guesswork or regex-based parsing.
 * 
 * Response Types:
 * - TEXT: Simple paragraph message
 * - BULLET: Bulleted list of items
 * - KV: Key-value pairs (card-style layout)
 * - TABLE: Tabular data with columns and rows
 * - MIXED: Combination of text and table
 * - FOLLOW_UP: Request for missing parameters
 * - ERROR: Error message with suggestions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredChatResponse {

    /**
     * Response type - determines UI rendering behavior.
     * MUST be one of: TEXT, BULLET, KV, TABLE, MIXED, FOLLOW_UP, ERROR
     */
    private ResponseType type;

    /**
     * Optional heading/title for the response.
     */
    private String title;

    /**
     * Confidence score of the response (0.0 to 1.0).
     */
    private Double confidence;

    /**
     * Type-specific payload containing the actual data.
     */
    private Map<String, Object> payload;

    /**
     * Optional footer/helper message.
     */
    private String footer;

    /**
     * Session ID for conversation continuity.
     */
    private String sessionId;

    /**
     * Detected scenario code (for auditing/debugging).
     */
    private String scenario;

    /**
     * Response types per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 4.
     */
    public enum ResponseType {
        TEXT,       // Simple text paragraph: payload.message
        BULLET,     // Bulleted list: payload.items[]
        KV,         // Key-value pairs: payload.{key: value}
        TABLE,      // Table: payload.columns[], payload.rows[][]
        MIXED,      // Mixed content: payload.text, payload.table
        FOLLOW_UP,  // Follow-up question: payload.missingParams[], payload.question
        ERROR       // Error: payload.message, payload.suggestions[]
    }

    // ============================================
    // FACTORY METHODS FOR EACH RESPONSE TYPE
    // ============================================

    /**
     * Create a TEXT response.
     */
    public static StructuredChatResponse text(String title, String message, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.TEXT)
                .title(title)
                .payload(Map.of("message", message))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create a TEXT response with confidence.
     */
    public static StructuredChatResponse text(String title, String message, String sessionId, Double confidence) {
        return StructuredChatResponse.builder()
                .type(ResponseType.TEXT)
                .title(title)
                .payload(Map.of("message", message))
                .sessionId(sessionId)
                .confidence(confidence)
                .build();
    }

    /**
     * Create a BULLET response.
     */
    public static StructuredChatResponse bullet(String title, List<String> items, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.BULLET)
                .title(title)
                .payload(Map.of("items", items))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create a KV (key-value) response.
     */
    public static StructuredChatResponse kv(String title, Map<String, Object> keyValues, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.KV)
                .title(title)
                .payload(keyValues)
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create a TABLE response.
     */
    public static StructuredChatResponse table(String title, List<String> columns, List<List<String>> rows, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.TABLE)
                .title(title)
                .payload(Map.of(
                        "columns", columns,
                        "rows", rows
                ))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create a MIXED response (text + table).
     */
    public static StructuredChatResponse mixed(String title, String text, List<String> columns, List<List<String>> rows, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.MIXED)
                .title(title)
                .payload(Map.of(
                        "text", text,
                        "table", Map.of(
                                "columns", columns,
                                "rows", rows
                        )
                ))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create a FOLLOW_UP response.
     */
    public static StructuredChatResponse followUp(List<String> missingParams, String question, String scenario, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.FOLLOW_UP)
                .scenario(scenario)
                .payload(Map.of(
                        "missingParams", missingParams,
                        "question", question
                ))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create an ERROR response.
     */
    public static StructuredChatResponse error(String message, List<String> suggestions, String sessionId) {
        return StructuredChatResponse.builder()
                .type(ResponseType.ERROR)
                .payload(Map.of(
                        "message", message,
                        "suggestions", suggestions != null ? suggestions : List.of()
                ))
                .sessionId(sessionId)
                .build();
    }

    /**
     * Create an ERROR response without suggestions.
     */
    public static StructuredChatResponse error(String message, String sessionId) {
        return error(message, List.of(
                "Check Transaction Status",
                "Account Summary",
                "Balance Inquiry"
        ), sessionId);
    }
}
