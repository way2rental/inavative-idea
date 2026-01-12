package com.enterprise.ai.intelligence.kernel.prompt;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Prompt Compiler - Transforms Intent + Context + Rules → Compiled Prompt.
 * 
 * Stage 7 of reasoning-first pipeline.
 * 
 * Responsibilities:
 * - Assembles RAG context (Domain, Intent, Tool, Response Pattern)
 * - Injects policy constraints
 * - Applies format requirements
 * - Compiles final prompt artifact
 * 
 * OUTPUT: CompiledPrompt (structured, policy-compliant, ready for LLM)
 */
public interface PromptCompiler {

    /**
     * Compile a prompt from reasoning plan, RAG context, and rules.
     * 
     * @param reasoningPlan Output from Reasoning Planner
     * @param ragContext RAG retrieval results (Domain, Intent, Tool, Response Pattern)
     * @param conversationContext Previous conversation history
     * @param executionContext Execution data (parameters, retrieved data)
     * @param policyConstraints Policy rules to enforce
     * @return Mono of CompiledPrompt
     */
    Mono<CompiledPrompt> compile(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            String conversationContext,
            Map<String, Object> executionContext,
            List<String> policyConstraints
    );
}
