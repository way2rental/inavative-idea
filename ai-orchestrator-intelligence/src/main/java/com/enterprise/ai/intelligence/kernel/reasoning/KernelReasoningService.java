package com.enterprise.ai.intelligence.kernel.reasoning;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Reasoning Service for Axis AI Kernel.
 * 
 * Stage 8: Template-based reasoning using RAG context.
 * 
 * NO EXTERNAL LLM APIs - Uses templates, RAG context, and rules only.
 * 
 * This is our OWN reasoning engine - not calling external APIs.
 */
public interface KernelReasoningService {
    
    /**
     * Generate response using template-based reasoning.
     * 
     * @param compiledPrompt The compiled prompt with RAG context
     * @param reasoningPlan The reasoning plan
     * @param toolResult Tool execution result (if any)
     * @return Mono of reasoning output text
     */
    Mono<String> generateResponse(CompiledPrompt compiledPrompt, ReasoningPlan reasoningPlan, ScenarioResult toolResult);
}
