package com.enterprise.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ReasoningPlan - Structured, auditable planning output from Reasoning Planner.
 * 
 * This is the output of the Reasoning Planner (THE BRAIN) BEFORE any LLM call.
 * It represents WHAT must be done, not HOW to do it.
 * 
 * ALL decisions are deterministic and auditable.
 * NO LLM is called to create a ReasoningPlan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningPlan {

    /**
     * Detected intent/scenario code
     * Example: "FETCH_CREDIT_CARD_STATEMENT"
     */
    private String intent;

    /**
     * Capability decision: DIRECT_ANSWER, DATA_RETRIEVAL, TOOL_EXECUTION, COMPOSITE
     */
    private String capability;

    /**
     * Tools/services to execute (if capability is TOOL_EXECUTION)
     * Example: ["CARD_STATEMENT_SERVICE", "DB_QUERY"]
     */
    private List<String> tools;

    /**
     * Required context entities
     * Example: ["ACCOUNT_ID", "DATE_RANGE"]
     */
    private List<String> requiredContext;

    /**
     * Extracted concepts from query (canonical concept mapping)
     * Example: {"CREDIT_CARD": 0.94, "BILL_STATEMENT": 0.90}
     */
    private Map<String, Double> concepts;

    /**
     * Extracted parameters (from entity extraction)
     * Example: {"accountId": "ACC123456", "dateRange": "last_month"}
     */
    private Map<String, Object> parameters;

    /**
     * Missing parameters (if any)
     * Example: ["accountId"]
     */
    private List<String> missingParameters;

    /**
     * Response pattern to use for final output
     * Example: "STATEMENT_SUMMARY", "TABLE", "KV"
     */
    private String responsePattern;

    /**
     * Confidence score (0.0 to 1.0)
     */
    private Double confidence;

    /**
     * Reasoning explanation (auditable)
     * Example: "Detected credit card intent via embedding similarity (0.94)"
     */
    private String reasoning;

    /**
     * Query normalization result (original → normalized)
     */
    private String normalizedQuery;

    /**
     * Original raw query (preserved)
     */
    private String rawQuery;

    /**
     * Execution plan details (JSON string for flexibility)
     * Contains step-by-step execution instructions
     */
    private String executionPlan;

    /**
     * Allowed scenarios (RBAC-filtered)
     */
    private Set<String> allowedScenarios;

    /**
     * Session ID for context memory
     */
    private String sessionId;

    /**
     * User ID for auditing
     */
    private String userId;
}
