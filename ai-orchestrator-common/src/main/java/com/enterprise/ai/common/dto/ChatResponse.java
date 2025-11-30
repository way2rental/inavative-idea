package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat interactions.
 * Supports multiple interaction types: direct response, follow-up, confirmation, clarification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String sessionId;
    private String message;
    
    /**
     * Type of response interaction
     */
    private ResponseType responseType;
    
    /**
     * True if the bot is waiting for user input (follow-up, confirmation, or clarification)
     */
    private boolean followUpRequired;
    
    /**
     * List of missing parameters (when responseType is FOLLOW_UP)
     */
    private List<String> missingParams;
    
    /**
     * Detected scenario
     */
    private String scenario;
    
    /**
     * Possible scenarios when intent is ambiguous (when responseType is CLARIFICATION)
     */
    private List<String> possibleScenarios;
    
    /**
     * Confidence score of intent detection
     */
    private Double confidence;
    
    /**
     * Pending action that needs confirmation (when responseType is CONFIRMATION)
     */
    private PendingAction pendingAction;
    
    private ResponseMeta meta;

    /**
     * Types of response interactions
     */
    public enum ResponseType {
        DIRECT,         // Direct answer, no follow-up needed
        FOLLOW_UP,      // Missing parameters, asking for more info
        CONFIRMATION,   // Asking user to confirm before execution
        CLARIFICATION,  // Ambiguous query, asking for clarification
        ERROR           // Error occurred
    }

    /**
     * Represents an action pending user confirmation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingAction {
        private String scenario;
        private Map<String, Object> params;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMeta {
        private List<String> sources;
        private String executionId;
        private Double intentConfidence;
        private String intentReasoning;
        private Map<String, Object> additionalInfo;
    }
}
