package com.enterprise.ai.intelligence.kernel.llm;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reasoning Engine for Axis AI Kernel - Stage 8.
 * 
 * RAG-powered template-based reasoning engine (like ChatGPT for our domain).
 * 
 * This is NOT an LLM wrapper.
 * This is NOT calling external APIs.
 * 
 * This is our OWN reasoning engine that:
 * - Uses RAG context for domain knowledge
 * - Uses templates for response generation
 * - Works deterministically and auditably
 * - Is domain-specific (banking)
 * 
 * Architecture:
 * - Takes CompiledPrompt (intent + RAG context + rules)
 * - Selects relevant RAG context
 * - Uses response patterns/templates
 * - Generates domain-specific responses
 */
public interface KernelLlmService {
    
    /**
     * Generate response using RAG-powered template reasoning.
     * 
     * This is our OWN reasoning engine - no external LLM calls.
     * Uses RAG context + templates to generate domain-specific responses.
     * 
     * @param compiledPrompt The compiled prompt with RAG context
     * @param reasoningPlan The reasoning plan
     * @param ragContext The RAG retrieval results
     * @param toolResult Tool execution result (if any)
     * @return Mono of generated response text
     */
    Mono<String> generateResponse(
            CompiledPrompt compiledPrompt,
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            ScenarioResult toolResult
    );
}
