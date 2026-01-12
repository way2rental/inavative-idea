package com.enterprise.ai.intelligence.kernel.response;

import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Response Generator & Shaper for Axis AI Kernel - Stage 10.
 * 
 * DLM-Based Response Generation:
 * - Uses DLM output (ReasoningPlan) + RAG context + Tool results
 * - Generates responses using templates (deterministic, configurable)
 * - NO text generation - pure template processing
 * 
 * Responsibilities:
 * - Generate responses from templates (using RAG context + ReasoningPlan + Tool results)
 * - Apply structure (JSON / sections)
 * - Apply UX rules
 * - Ensure frontend compatibility
 * 
 * This is the ONLY place where responses are generated.
 * DLM (ReasoningPlanner) only outputs structured data - ResponseShaper generates responses.
 */
public interface ResponseShaper {
    
    /**
     * Generate and shape response using DLM output + RAG context + Tool results.
     * 
     * This method generates responses deterministically using:
     * - ReasoningPlan (DLM output: intent, concepts, entities)
     * - RAG context (domain documents, response patterns)
     * - Tool results (execution data)
     * - Templates (from database)
     * 
     * @param reasoningPlan The DLM output (structured understanding)
     * @param ragContext RAG retrieval results (domain documents, response patterns)
     * @param toolResult Tool execution result (if any)
     * @return Mono of generated and formatted response text
     */
    Mono<String> generateResponse(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            ScenarioResult toolResult
    );
}
