package com.enterprise.ai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO representing AI intent detection result from LLM.
 * Includes confidence scoring, ambiguity detection, and reasoning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentResult {

    /**
     * Detected scenario code (TXN_STATUS, FILE_STATUS, ACCOUNT_SUMMARY, AMBIGUOUS, UNKNOWN)
     */
    private String scenario;
    
    /**
     * Confidence score from 0.0 to 1.0
     * - 0.95+ : Perfect match
     * - 0.80-0.94 : Good match
     * - 0.60-0.79 : Moderate, might need confirmation
     * - 0.40-0.59 : Low confidence, likely ambiguous
     * - Below 0.40 : Unknown
     */
    private double confidence;
    
    /**
     * Extracted parameters from user query
     */
    private Map<String, Object> params;
    
    /**
     * List of required parameters that are missing
     */
    private List<String> missingParams;
    
    /**
     * When scenario is AMBIGUOUS, this contains possible matching scenarios
     */
    private List<String> possibleScenarios;
    
    /**
     * Brief explanation of why this intent was detected (for debugging/audit)
     */
    private String reasoning;
    
    /**
     * Check if the intent detection is confident enough for execution
     */
    public boolean isConfident() {
        return confidence >= 0.75;
    }
    
    /**
     * Check if the intent is ambiguous
     */
    public boolean isAmbiguous() {
        return "AMBIGUOUS".equals(scenario) || 
               (possibleScenarios != null && possibleScenarios.size() > 1);
    }
    
    /**
     * Check if all required parameters are present
     */
    public boolean hasAllRequiredParams() {
        return missingParams == null || missingParams.isEmpty();
    }
}
