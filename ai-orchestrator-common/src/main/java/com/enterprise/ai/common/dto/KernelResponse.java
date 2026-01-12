package com.enterprise.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Kernel Response - Final output from Axis AI Kernel (10-stage pipeline).
 * 
 * Contains all information about the processed request and response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KernelResponse {

    /**
     * Final response text (after Stage 10: Response Shaping)
     */
    private String responseText;

    /**
     * Reasoning plan (from Stage 1-5)
     */
    private ReasoningPlan reasoningPlan;

    /**
     * RAG context used (from Stage 6)
     * Stored as generic list to avoid circular dependency (RagRetrievalResult is in intelligence module)
     */
    private java.util.List<java.util.Map<String, Object>> ragContext;

    /**
     * Compiled prompt (from Stage 7)
     */
    private CompiledPrompt compiledPrompt;

    /**
     * LLM response (from Stage 8)
     */
    private String llmResponse;

    /**
     * Post-LLM compliance result (from Stage 9)
     */
    private ComplianceResult complianceResult;

    /**
     * Tool execution result (if tools were executed)
     */
    private ScenarioResult toolResult;

    /**
     * Response type: DIRECT_ANSWER, DATA_RETRIEVAL, TOOL_EXECUTION, COMPOSITE
     */
    private String responseType;

    /**
     * Confidence score (0.0 to 1.0)
     */
    private Double confidence;

    /**
     * Execution metadata (for audit/debugging)
     */
    private Map<String, Object> metadata;

    /**
     * Whether response was blocked by compliance
     */
    private Boolean blocked;

    /**
     * Error message (if any)
     */
    private String errorMessage;
}
