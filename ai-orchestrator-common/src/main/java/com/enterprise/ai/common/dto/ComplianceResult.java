package com.enterprise.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Compliance Result - Policy & Compliance validation result.
 * 
 * Used by Policy & Compliance Guard (PRE-LLM and POST-LLM validation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceResult {

    /**
     * Whether the request/response is compliant
     */
    private Boolean compliant;

    /**
     * Whether the request/response was blocked
     */
    private Boolean blocked;

    /**
     * Compliance violations (if any)
     */
    private List<Violation> violations;

    /**
     * Applied policies (for audit)
     */
    private List<String> appliedPolicies;

    /**
     * Risk level: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String riskLevel;

    /**
     * Blocking message (if blocked)
     */
    private String blockingMessage;

    /**
     * Masked data (for sensitive data masking in POST-LLM)
     */
    private Map<String, String> maskedData;

    /**
     * Metadata (for audit/debugging)
     */
    private Map<String, Object> metadata;

    /**
     * Violation details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Violation {
        private String policyKey;
        private String policyName;
        private String message;
        private String severity; // BLOCK, WARN, LOG
    }
}
