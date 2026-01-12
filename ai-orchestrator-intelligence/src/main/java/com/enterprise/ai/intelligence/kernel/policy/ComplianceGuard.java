package com.enterprise.ai.intelligence.kernel.policy;

import com.enterprise.ai.common.dto.ComplianceResult;
import com.enterprise.ai.common.dto.ReasoningPlan;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Policy & Compliance Guard - Bank-Grade Safety.
 * 
 * Runs BOTH PRE-LLM and POST-LLM validation.
 * 
 * PRE-LLM:
 * - Intent allow-listing
 * - Role & authorization checks
 * - Data access constraints
 * 
 * POST-LLM:
 * - Hallucination detection
 * - Compliance phrasing enforcement
 * - Sensitive data masking
 * - Risk classification
 * 
 * NO RESPONSE leaves the kernel without policy approval.
 */
public interface ComplianceGuard {

    /**
     * Validate BEFORE LLM call (PRE-LLM).
     * 
     * @param reasoningPlan Output from Reasoning Planner
     * @param userId User ID
     * @param userRoles User roles
     * @param query Original user query
     * @return Mono of ComplianceResult
     */
    Mono<ComplianceResult> validatePreLlm(
            ReasoningPlan reasoningPlan,
            String userId,
            List<String> userRoles,
            String query
    );

    /**
     * Validate AFTER LLM call (POST-LLM).
     * 
     * @param llmResponse Raw LLM response
     * @param reasoningPlan Original reasoning plan
     * @param userId User ID
     * @param userRoles User roles
     * @return Mono of ComplianceResult with masked data if needed
     */
    Mono<ComplianceResult> validatePostLlm(
            String llmResponse,
            ReasoningPlan reasoningPlan,
            String userId,
            List<String> userRoles
    );
}
