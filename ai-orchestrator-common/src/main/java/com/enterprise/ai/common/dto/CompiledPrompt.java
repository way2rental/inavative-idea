package com.enterprise.ai.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Compiled Prompt - Final prompt artifact ready for LLM.
 * 
 * OUTPUT of Prompt Compiler (Stage 7 of reasoning-first pipeline).
 * 
 * This is the result of compiling:
 * - Intent (from ReasoningPlan)
 * - Context (from RAG Engine, Memory Manager)
 * - Rules (Policy & Compliance, Response Patterns)
 * 
 * Into a single, formatted, policy-compliant prompt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompiledPrompt {

    /**
     * Final compiled prompt text (ready for LLM)
     */
    private String promptText;

    /**
     * Scenario code (if applicable)
     */
    private String scenarioCode;

    /**
     * Intent from ReasoningPlan
     */
    private String intent;

    /**
     * Capability from ReasoningPlan
     */
    private String capability;

    /**
     * RAG context sections (Domain, Intent, Tool, Response Pattern)
     */
    private Map<String, String> ragContextSections;

    /**
     * Policy constraints applied (for audit)
     */
    private List<String> appliedPolicies;

    /**
     * Format requirements (for LLM output control)
     */
    private String formatRequirement;

    /**
     * System instructions (role, behavior)
     */
    private String systemInstructions;

    /**
     * User query (preserved original)
     */
    private String userQuery;

    /**
     * Conversation context
     */
    private String conversationContext;

    /**
     * Execution context (parameters, data)
     */
    private Map<String, Object> executionContext;

    /**
     * Compilation metadata (for debugging/audit)
     */
    private Map<String, Object> compilationMetadata;
}
