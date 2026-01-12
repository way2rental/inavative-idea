package com.enterprise.ai.intelligence.kernel;

import com.enterprise.ai.common.dto.KernelResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Axis AI Kernel - Central Orchestrator.
 * 
 * Implements the 10-stage reasoning-first pipeline:
 * 
 * 1. Query Normalization
 * 2. Concept Extraction
 * 3. Intent Hypothesis (RAG-backed)
 * 4. Capability Decision
 * 5. Execution Plan Creation
 * 6. Context Assembly (RAG)
 * 7. Prompt Compilation
 * 8. LLM Reasoning
 * 9. Post-Response Validation
 * 10. Final Response Shaping
 * 
 * ALL components are orchestrated here.
 * NO LLM calls outside of Stage 8.
 */
public interface AxisAiKernel {

    /**
     * Process user query through the complete 10-stage pipeline.
     * 
     * @param rawQuery Original user query
     * @param sessionContext Previous conversation context
     * @param allowedScenarios RBAC-filtered scenarios user can access
     * @param userId User ID for auditing
     * @param sessionId Session ID for context memory
     * @param userRoles User roles for RBAC
     * @return Mono of KernelResponse (final response after all stages)
     */
    Mono<KernelResponse> process(
            String rawQuery,
            String sessionContext,
            Set<String> allowedScenarios,
            String userId,
            String sessionId,
            List<String> userRoles
    );
}
