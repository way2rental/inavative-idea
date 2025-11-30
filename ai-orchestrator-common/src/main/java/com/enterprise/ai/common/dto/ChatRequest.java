package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for chat interactions.
 * Supports regular queries, confirmations, clarifications, and dry-run mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Query is required")
    private String query;

    private String sessionId;
    
    /**
     * Type of request (regular query or response to confirmation/clarification)
     */
    private RequestType requestType;
    
    /**
     * When responding to confirmation, indicates if user confirmed (yes/no)
     */
    private Boolean confirmed;
    
    /**
     * When responding to clarification, indicates selected option (1, 2, 3, etc.)
     */
    private Integer selectedOption;
    
    /**
     * Pending action details (passed back from confirmation flow)
     */
    private Map<String, Object> pendingActionParams;
    
    /**
     * Pending scenario (passed back from confirmation flow)
     */
    private String pendingScenario;

    /**
     * Dry-run mode flag.
     * When true, executes everything except calling real backend.
     * Returns what would have happened without making actual changes.
     */
    @Builder.Default
    private boolean dryRun = false;

    /**
     * Types of chat requests
     */
    public enum RequestType {
        QUERY,              // Normal user query
        CONFIRMATION,       // Response to confirmation request
        CLARIFICATION       // Response to clarification request
    }
    
    /**
     * Check if this is a confirmation response
     */
    public boolean isConfirmationResponse() {
        return RequestType.CONFIRMATION == requestType;
    }
    
    /**
     * Check if this is a clarification response
     */
    public boolean isClarificationResponse() {
        return RequestType.CLARIFICATION == requestType;
    }
}
