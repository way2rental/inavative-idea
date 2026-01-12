package com.enterprise.ai.intelligence.kernel.planner;

import com.enterprise.ai.common.dto.ReasoningPlan;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Domain Language Model (DLM) - THE BRAIN of Axis AI Kernel.
 * 
 * This is the Domain Language Model (DLM) - our OWN understanding system for banking domain.
 * 
 * DLM Responsibilities (Language Understanding ONLY):
 * - Intent Identification (maps user query to scenario code)
 * - Concept Extraction (identifies banking concepts: CREDIT_CARD, ACCOUNT, etc.)
 * - Entity Extraction (extracts structured parameters: ACCOUNT_ID, DATE_RANGE, etc.)
 * - Ambiguity Scoring (determines confidence level)
 * - Query Normalization (preprocessing)
 * 
 * DLM Output (Structured JSON-like):
 * - Intent (scenario code) with confidence score
 * - Concepts (Map<ConceptCode, ConfidenceScore>)
 * - Entities (Map<EntityType, EntityValue>)
 * - Ambiguity score (0.0-1.0)
 * - Capability decision (DIRECT_ANSWER, DATA_RETRIEVAL, TOOL_EXECUTION, COMPOSITE)
 * - Execution plan
 * 
 * OUTPUT: ReasoningPlan (structured, auditable, deterministic JSON-like structure)
 * 
 * IMPORTANT:
 * - DLM is a SIGNAL GENERATOR, NOT a decision maker
 * - DLM NEVER formats responses
 * - DLM NEVER calls external services
 * - DLM NEVER accesses databases directly (uses injected services)
 * - DLM outputs structured data ONLY (ReasoningPlan DTO)
 * 
 * Response generation happens in ResponseShaper (Stage 9), NOT in DLM.
 */
public interface ReasoningPlanner {

    /**
     * Create a reasoning plan from user query.
     * This is the FIRST step before any LLM call.
     * 
     * @param rawQuery Original user query (preserved)
     * @param sessionContext Previous conversation context
     * @param allowedScenarios RBAC-filtered scenarios user can access
     * @param sessionId Session ID for context memory
     * @param userId User ID for auditing
     * @return Mono of ReasoningPlan
     */
    Mono<ReasoningPlan> createPlan(
            String rawQuery,
            String sessionContext,
            Set<String> allowedScenarios,
            String sessionId,
            String userId
    );
}
