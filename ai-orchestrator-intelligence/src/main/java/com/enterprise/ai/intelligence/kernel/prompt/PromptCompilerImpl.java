package com.enterprise.ai.intelligence.kernel.prompt;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.data.service.SystemConfigService;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt Compiler Implementation.
 * 
 * Transforms Intent + Context + Rules → Compiled Prompt.
 * 
 * REUSES:
 * - SystemConfigService for branding/config
 * - ConfigCacheService for scenario details
 * - DynamicPromptBuilder logic (adapted for ReasoningPlan + RAG)
 * 
 * NO HARDCODING - All values from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptCompilerImpl implements PromptCompiler {

    private final SystemConfigService systemConfigService;
    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<CompiledPrompt> compile(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            String conversationContext,
            Map<String, Object> executionContext,
            List<String> policyConstraints) {
        
        return Mono.fromCallable(() -> {
            try {
                log.debug("Compiling prompt for intent: {}, capability: {}", 
                        reasoningPlan.getIntent(), reasoningPlan.getCapability());

                // Build system instructions
                String systemInstructions = buildSystemInstructions(reasoningPlan);

                // Build RAG context sections
                Map<String, String> ragSections = buildRagContextSections(ragContext);

                // Build format requirements
                String formatRequirement = buildFormatRequirement(reasoningPlan);

                // Build policy constraints section
                String policySection = buildPolicySection(policyConstraints);

                // Build final prompt text
                String promptText = assemblePrompt(
                        reasoningPlan,
                        systemInstructions,
                        ragSections,
                        formatRequirement,
                        policySection,
                        conversationContext,
                        executionContext
                );

                // Build compilation metadata
                Map<String, Object> compilationMetadata = buildCompilationMetadata(
                        reasoningPlan, ragContext, policyConstraints
                );

                return CompiledPrompt.builder()
                        .promptText(promptText)
                        .scenarioCode(reasoningPlan.getIntent())
                        .intent(reasoningPlan.getIntent())
                        .capability(reasoningPlan.getCapability())
                        .ragContextSections(ragSections)
                        .appliedPolicies(policyConstraints != null ? policyConstraints : Collections.emptyList())
                        .formatRequirement(formatRequirement)
                        .systemInstructions(systemInstructions)
                        .userQuery(reasoningPlan.getRawQuery())
                        .conversationContext(conversationContext)
                        .executionContext(executionContext != null ? executionContext : Collections.emptyMap())
                        .compilationMetadata(compilationMetadata)
                        .build();

            } catch (Exception e) {
                log.error("Failed to compile prompt: {}", e.getMessage(), e);
                throw new RuntimeException("Prompt compilation failed", e);
            }
        });
    }

    /**
     * Build system instructions (role, personality, capabilities).
     */
    private String buildSystemInstructions(ReasoningPlan reasoningPlan) {
        String assistantName = systemConfigService.getAssistantName();
        String assistantFullName = systemConfigService.getAssistantFullName();
        String orgName = systemConfigService.getOrgName();

        StringBuilder instructions = new StringBuilder();
        instructions.append("═══════════════════════════════════════════════════════════════════════════════\n");
        instructions.append(String.format("YOU ARE %s (%s) - %s'S BANKING ASSISTANT\n", 
                assistantName, assistantFullName, orgName.toUpperCase()));
        instructions.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        instructions.append("YOUR CORE IDENTITY:\n");
        instructions.append("- You are a friendly, professional banking assistant\n");
        instructions.append("- You work for ").append(orgName).append("\n");
        instructions.append("- You help business customers with their banking needs\n");
        instructions.append("- You are polite, efficient, and always helpful\n\n");

        instructions.append("YOUR PERSONALITY TRAITS:\n");
        instructions.append("✨ Warm and welcoming (but professional)\n");
        instructions.append("✨ Patient with unclear requests\n");
        instructions.append("✨ Proactive in suggesting relevant services\n");
        instructions.append("✨ Remembers context from previous messages\n\n");

        // Add capability-specific instructions
        if (reasoningPlan.getCapability() != null) {
            instructions.append("CURRENT TASK CAPABILITY: ").append(reasoningPlan.getCapability()).append("\n");
            switch (reasoningPlan.getCapability()) {
                case "DIRECT_ANSWER":
                    instructions.append("Provide a direct answer using domain knowledge.\n");
                    break;
                case "DATA_RETRIEVAL":
                    instructions.append("Format retrieved data into a helpful response.\n");
                    break;
                case "TOOL_EXECUTION":
                    instructions.append("Present tool execution results clearly.\n");
                    break;
                case "COMPOSITE":
                    instructions.append("Combine multiple information sources.\n");
                    break;
            }
            instructions.append("\n");
        }

        return instructions.toString();
    }

    /**
     * Build RAG context sections from retrieval results.
     */
    private Map<String, String> buildRagContextSections(List<RagRetrievalResult> ragContext) {
        Map<String, String> sections = new HashMap<>();
        
        if (ragContext == null || ragContext.isEmpty()) {
            return sections;
        }

        // Group by source type
        Map<String, List<RagRetrievalResult>> grouped = ragContext.stream()
                .collect(Collectors.groupingBy(RagRetrievalResult::getSourceType));

        // Domain Documents
        if (grouped.containsKey("DOMAIN_DOCUMENT")) {
            StringBuilder domainSection = new StringBuilder();
            domainSection.append("═══════════════════════════════════════════════════════════════\n");
            domainSection.append("DOMAIN KNOWLEDGE (Use this for accurate information):\n");
            domainSection.append("═══════════════════════════════════════════════════════════════\n");
            for (RagRetrievalResult result : grouped.get("DOMAIN_DOCUMENT")) {
                domainSection.append(result.getContent()).append("\n\n");
            }
            sections.put("DOMAIN_DOCUMENT", domainSection.toString());
        }

        // Intent Definitions
        if (grouped.containsKey("INTENT_DEFINITION")) {
            StringBuilder intentSection = new StringBuilder();
            intentSection.append("═══════════════════════════════════════════════════════════════\n");
            intentSection.append("AVAILABLE INTENTS (What the system can understand):\n");
            intentSection.append("═══════════════════════════════════════════════════════════════\n");
            for (RagRetrievalResult result : grouped.get("INTENT_DEFINITION")) {
                intentSection.append(result.getContent()).append("\n");
            }
            sections.put("INTENT_DEFINITION", intentSection.toString());
        }

        // Tool Definitions
        if (grouped.containsKey("TOOL_DEFINITION")) {
            StringBuilder toolSection = new StringBuilder();
            toolSection.append("═══════════════════════════════════════════════════════════════\n");
            toolSection.append("AVAILABLE TOOLS (What the system can do):\n");
            toolSection.append("═══════════════════════════════════════════════════════════════\n");
            for (RagRetrievalResult result : grouped.get("TOOL_DEFINITION")) {
                toolSection.append(result.getContent()).append("\n");
            }
            sections.put("TOOL_DEFINITION", toolSection.toString());
        }

        // Response Patterns
        if (grouped.containsKey("RESPONSE_PATTERN")) {
            StringBuilder patternSection = new StringBuilder();
            patternSection.append("═══════════════════════════════════════════════════════════════\n");
            patternSection.append("RESPONSE FORMAT (Structure your output like this):\n");
            patternSection.append("═══════════════════════════════════════════════════════════════\n");
            for (RagRetrievalResult result : grouped.get("RESPONSE_PATTERN")) {
                patternSection.append(result.getContent()).append("\n\n");
            }
            sections.put("RESPONSE_PATTERN", patternSection.toString());
        }

        return sections;
    }

    /**
     * Build format requirements based on reasoning plan.
     */
    private String buildFormatRequirement(ReasoningPlan reasoningPlan) {
        StringBuilder format = new StringBuilder();
        
        if (reasoningPlan.getResponsePattern() != null) {
            format.append("RESPONSE PATTERN: ").append(reasoningPlan.getResponsePattern()).append("\n");
            
            switch (reasoningPlan.getResponsePattern().toUpperCase()) {
                case "TEXT":
                    format.append("Provide a natural language response.\n");
                    break;
                case "TABLE":
                    format.append("Format data as a table with clear headers.\n");
                    break;
                case "KV":
                    format.append("Present information as key-value pairs.\n");
                    break;
                case "MIXED":
                    format.append("Combine text explanation with structured data.\n");
                    break;
            }
        }

        // Add scenario-specific format if available
        if (reasoningPlan.getIntent() != null) {
            configCacheService.getScenarioByCode(reasoningPlan.getIntent())
                    .ifPresent(scenario -> {
                        if (scenario.getLlmPromptTemplate() != null && !scenario.getLlmPromptTemplate().isEmpty()) {
                            format.append("\nSCENARIO-SPECIFIC FORMAT:\n");
                            format.append(scenario.getLlmPromptTemplate()).append("\n");
                        }
                    });
        }

        return format.toString();
    }

    /**
     * Build policy constraints section.
     */
    private String buildPolicySection(List<String> policyConstraints) {
        if (policyConstraints == null || policyConstraints.isEmpty()) {
            return "";
        }

        StringBuilder policy = new StringBuilder();
        policy.append("═══════════════════════════════════════════════════════════════\n");
        policy.append("POLICY CONSTRAINTS (MUST FOLLOW):\n");
        policy.append("═══════════════════════════════════════════════════════════════\n");
        for (String constraint : policyConstraints) {
            policy.append("⚠ ").append(constraint).append("\n");
        }
        policy.append("\n");

        return policy.toString();
    }

    /**
     * Assemble final prompt text.
     */
    private String assemblePrompt(
            ReasoningPlan reasoningPlan,
            String systemInstructions,
            Map<String, String> ragSections,
            String formatRequirement,
            String policySection,
            String conversationContext,
            Map<String, Object> executionContext) {
        
        StringBuilder prompt = new StringBuilder();

        // System instructions
        prompt.append(systemInstructions);

        // RAG context sections (in priority order)
        if (ragSections.containsKey("DOMAIN_DOCUMENT")) {
            prompt.append(ragSections.get("DOMAIN_DOCUMENT")).append("\n");
        }
        if (ragSections.containsKey("INTENT_DEFINITION")) {
            prompt.append(ragSections.get("INTENT_DEFINITION")).append("\n");
        }
        if (ragSections.containsKey("TOOL_DEFINITION")) {
            prompt.append(ragSections.get("TOOL_DEFINITION")).append("\n");
        }

        // Policy constraints
        if (!policySection.isEmpty()) {
            prompt.append(policySection);
        }

        // Conversation context
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("CONVERSATION HISTORY:\n");
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        if (conversationContext != null && !conversationContext.isBlank()) {
            prompt.append(conversationContext).append("\n");
        } else {
            prompt.append("(This is the start of a new conversation)\n");
        }
        prompt.append("\n");

        // Execution context (parameters, data)
        if (executionContext != null && !executionContext.isEmpty()) {
            prompt.append("═══════════════════════════════════════════════════════════════\n");
            prompt.append("EXECUTION CONTEXT:\n");
            prompt.append("═══════════════════════════════════════════════════════════════\n");
            try {
                prompt.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(executionContext)).append("\n\n");
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize execution context: {}", e.getMessage());
                prompt.append(executionContext.toString()).append("\n\n");
            }
        }

        // User query
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("CUSTOMER'S QUERY:\n");
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("\"").append(reasoningPlan.getRawQuery()).append("\"\n\n");

        // Format requirements
        if (!formatRequirement.isEmpty()) {
            prompt.append("═══════════════════════════════════════════════════════════════\n");
            prompt.append("OUTPUT FORMAT REQUIREMENTS:\n");
            prompt.append("═══════════════════════════════════════════════════════════════\n");
            prompt.append(formatRequirement).append("\n");
        }

        // Response patterns (last, as they guide output structure)
        if (ragSections.containsKey("RESPONSE_PATTERN")) {
            prompt.append("\n").append(ragSections.get("RESPONSE_PATTERN"));
        }

        // Final task instruction
        prompt.append("\n═══════════════════════════════════════════════════════════════\n");
        prompt.append("TASK:\n");
        prompt.append("═══════════════════════════════════════════════════════════════\n");
        prompt.append("Based on the context above, provide a helpful, accurate response to the customer's query.\n");
        prompt.append("Be friendly, professional, and concise.\n");

        return prompt.toString();
    }

    /**
     * Build compilation metadata for audit/debugging.
     */
    private Map<String, Object> buildCompilationMetadata(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            List<String> policyConstraints) {
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("intent", reasoningPlan.getIntent());
        metadata.put("capability", reasoningPlan.getCapability());
        metadata.put("confidence", reasoningPlan.getConfidence());
        metadata.put("ragContextCount", ragContext != null ? ragContext.size() : 0);
        metadata.put("policyConstraintCount", policyConstraints != null ? policyConstraints.size() : 0);
        metadata.put("compiledAt", System.currentTimeMillis());
        
        return metadata;
    }
}
