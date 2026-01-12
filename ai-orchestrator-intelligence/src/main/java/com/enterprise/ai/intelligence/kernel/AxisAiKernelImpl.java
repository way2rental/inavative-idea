package com.enterprise.ai.intelligence.kernel;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ComplianceResult;
import com.enterprise.ai.common.dto.KernelResponse;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.kernel.policy.ComplianceGuard;
import com.enterprise.ai.intelligence.kernel.planner.ReasoningPlanner;
import com.enterprise.ai.intelligence.kernel.prompt.PromptCompiler;
import com.enterprise.ai.intelligence.kernel.rag.RagEngine;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import com.enterprise.ai.intelligence.kernel.response.ResponseShaper;
import com.enterprise.ai.intelligence.kernel.tool.ToolDispatcher;
import com.enterprise.ai.intelligence.kernel.llm.KernelLlmService;
import com.enterprise.ai.intelligence.kernel.memory.MemoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Axis AI Kernel Implementation - DLM-Based Architecture.
 * 
 * Orchestrates the complete DLM (Domain Language Model) pipeline.
 * 
 * Pipeline Flow:
 * 1-5. DLM (ReasoningPlanner) - Intent, Concepts, Entities, Ambiguity (Structured Understanding)
 * 6. Context Assembly (RAG) - Domain Documents, Response Patterns
 * 7. Tool Execution (if needed) - Data Retrieval/Actions
 * 8. Post-Response Validation - Compliance Check
 * 9. Response Generation & Formatting (ResponseShaper) - Template-based, Deterministic
 * 
 * NO external LLM calls - DLM outputs structured data, ResponseShaper generates responses.
 * 
 * REUSES all existing components and kernel components.
 * This is the CENTRAL ORCHESTRATOR - all AI logic flows through here.
 * 
 * Generic RAG-based architecture - all knowledge comes from database via RAG Engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AxisAiKernelImpl implements AxisAiKernel {

    private final ReasoningPlanner reasoningPlanner; // DLM (Domain Language Model)
    private final RagEngine ragEngine;
    private final PromptCompiler promptCompiler; // Optional - kept for audit/logging
    private final ComplianceGuard complianceGuard;
    private final ToolDispatcher toolDispatcher;
    @Deprecated // No longer used - response generation moved to ResponseShaper (Stage 9)
    private final KernelLlmService kernelLlmService; // Kept for backward compatibility, not used
    private final MemoryManager memoryManager;
    private final ResponseShaper responseShaper;

    @Override
    public Mono<KernelResponse> process(
            String rawQuery,
            String sessionContext,
            Set<String> allowedScenarios,
            String userId,
            String sessionId,
            List<String> userRoles) {
        
        log.debug("Processing query through DLM pipeline: {}", rawQuery);
        
        // Stage 1-5: DLM (Domain Language Model) = ReasoningPlanner
        // Outputs structured understanding: Intent, Concepts, Entities, Ambiguity, Confidence
        return reasoningPlanner.createPlan(rawQuery, sessionContext, allowedScenarios, sessionId, userId)
                .flatMap(reasoningPlan -> {
                    log.debug("DLM (Stage 1-5) completed: intent={}, capability={}, confidence={}", 
                            reasoningPlan.getIntent(), reasoningPlan.getCapability(), reasoningPlan.getConfidence());
                    
                    // PRE-LLM Compliance Check
                    return complianceGuard.validatePreLlm(reasoningPlan, userId, userRoles, rawQuery)
                            .flatMap(preCompliance -> {
                                if (preCompliance.getBlocked()) {
                                    log.warn("Request blocked by PRE-LLM compliance: {}", preCompliance.getBlockingMessage());
                                    return Mono.just(buildBlockedResponse(reasoningPlan, preCompliance));
                                }
                                
                                // Stage 6: Context Assembly (RAG)
                                return retrieveRagContext(reasoningPlan, allowedScenarios)
                                        .collectList()
                                        .flatMap(ragContext -> {
                                            log.debug("Stage 6 completed: retrieved {} RAG results", ragContext.size());
                                            
                                            // Stage 7: Tool Execution (if needed)
                                            Mono<ScenarioResult> toolResultMono = Mono.empty();
                                            if (shouldExecuteTools(reasoningPlan)) {
                                                toolResultMono = toolDispatcher.execute(reasoningPlan, userId, sessionId);
                                            }
                                            
                                            return toolResultMono.defaultIfEmpty(
                                                    ScenarioResult.builder()
                                                            .scenario(reasoningPlan.getIntent())
                                                            .success(true)
                                                            .data(Collections.emptyMap())
                                                            .build()
                                            )
                                            .flatMap(toolResult -> {
                                                log.debug("Stage 7 completed: tool execution done (success={})", toolResult.isSuccess());
                                                
                                                // Stage 8: Post-Response Validation (validate before generating response)
                                                // Generate a temporary response preview for validation
                                                // Note: We'll validate the final response after generation in Stage 9
                                                
                                                // Stage 9: Response Generation & Formatting (ResponseShaper)
                                                // Uses DLM output (ReasoningPlan) + RAG context + Tool results
                                                // Template-based, deterministic response generation
                                                log.debug("Stage 9: Generating response using DLM + RAG + Templates (capability: {})", 
                                                        reasoningPlan.getCapability());
                                                
                                                return responseShaper.generateResponse(reasoningPlan, ragContext, toolResult)
                                                        .flatMap(generatedResponse -> {
                                                            log.debug("Stage 9 completed: response generated (length={})", 
                                                                    generatedResponse != null ? generatedResponse.length() : 0);
                                                            
                                                            // Post-generation validation (validate the actual generated response)
                                                            return complianceGuard.validatePostLlm(
                                                                    generatedResponse, reasoningPlan, userId, userRoles
                                                            )
                                                            .flatMap(postCompliance -> {
                                                                if (postCompliance.getBlocked()) {
                                                                    log.warn("Response blocked by POST-GENERATION compliance");
                                                                    return Mono.just(buildBlockedResponse(reasoningPlan, postCompliance));
                                                                }
                                                                
                                                                log.debug("Post-generation validation passed");
                                                                
                                                                // Apply data masking if needed (compliance guard already checked)
                                                                String finalResponse = postCompliance.getMaskedData() != null && 
                                                                        !postCompliance.getMaskedData().isEmpty()
                                                                        ? applyDataMasking(generatedResponse, postCompliance.getMaskedData())
                                                                        : generatedResponse;
                                                                
                                                                // Store entities in memory for future reference resolution
                                                                storeEntitiesInMemory(reasoningPlan, toolResult, sessionId, userId)
                                                                        .subscribe(
                                                                                null,
                                                                                error -> log.warn("Failed to store entities in memory: {}", error.getMessage())
                                                                        );
                                                                
                                                                // Build compiled prompt for audit (optional - for logging)
                                                                CompiledPrompt compiledPrompt = buildCompiledPromptForAudit(
                                                                        reasoningPlan, ragContext, sessionContext, preCompliance);
                                                                
                                                                // Convert RagRetrievalResult list to Map list for KernelResponse
                                                                List<Map<String, Object>> ragContextMap = ragContext.stream()
                                                                        .map(result -> {
                                                                            Map<String, Object> map = new HashMap<>();
                                                                            map.put("sourceType", result.getSourceType());
                                                                            map.put("content", result.getContent());
                                                                            map.put("relevanceScore", result.getRelevanceScore());
                                                                            map.put("metadata", result.getMetadata());
                                                                            map.put("sourceId", result.getSourceId());
                                                                            return map;
                                                                        })
                                                                        .collect(Collectors.toList());
                                                                
                                                                return Mono.just(KernelResponse.builder()
                                                                        .responseText(finalResponse)
                                                                        .reasoningPlan(reasoningPlan)
                                                                        .ragContext(ragContextMap)
                                                                        .compiledPrompt(compiledPrompt)
                                                                        .llmResponse(generatedResponse) // Store generated response for audit
                                                                        .complianceResult(postCompliance)
                                                                        .toolResult(toolResult)
                                                                        .responseType(reasoningPlan.getCapability())
                                                                        .confidence(reasoningPlan.getConfidence())
                                                                        .blocked(false)
                                                                        .metadata(buildMetadata(reasoningPlan, ragContext, postCompliance))
                                                                        .build());
                                                            });
                                                        });
                                            });
                                        });
                            });
                })
                .doOnSuccess(response -> log.info("DLM pipeline completed successfully"))
                .doOnError(error -> log.error("DLM pipeline failed", error));
    }
    
    /**
     * Stage 6: Retrieve RAG context.
     */
    private Flux<RagRetrievalResult> retrieveRagContext(ReasoningPlan reasoningPlan, Set<String> allowedScenarios) {
        String query = reasoningPlan.getRawQuery();
        
        // Retrieve all knowledge types
        Flux<RagRetrievalResult> domainDocs = ragEngine.retrieveDomainDocuments(query, null, 5);
        Flux<RagRetrievalResult> intentDefs = ragEngine.retrieveIntentDefinitions(query, allowedScenarios, 5);
        Flux<RagRetrievalResult> toolDefs = ragEngine.retrieveToolDefinitions(query, null, 5);
        
        Flux<RagRetrievalResult> responsePatterns = Flux.empty();
        if (reasoningPlan.getIntent() != null && !"UNKNOWN".equals(reasoningPlan.getIntent())) {
            responsePatterns = ragEngine.retrieveResponsePatterns(reasoningPlan.getIntent(), null, 3);
        }
        
        return Flux.merge(domainDocs, intentDefs, toolDefs, responsePatterns);
    }
    
    /**
     * Build execution context for prompt compilation.
     */
    private Map<String, Object> buildExecutionContext(ReasoningPlan reasoningPlan) {
        Map<String, Object> context = new HashMap<>();
        context.put("intent", reasoningPlan.getIntent());
        context.put("capability", reasoningPlan.getCapability());
        context.put("parameters", reasoningPlan.getParameters());
        context.put("tools", reasoningPlan.getTools());
        context.put("concepts", reasoningPlan.getConcepts());
        return context;
    }
    
    /**
     * Extract policy constraints from compliance result.
     */
    private List<String> extractPolicyConstraints(ComplianceResult complianceResult) {
        if (complianceResult.getAppliedPolicies() != null) {
            return complianceResult.getAppliedPolicies();
        }
        return Collections.emptyList();
    }
    
    /**
     * Check if tools should be executed.
     */
    private boolean shouldExecuteTools(ReasoningPlan reasoningPlan) {
        String capability = reasoningPlan.getCapability();
        return "TOOL_EXECUTION".equals(capability) || 
               "COMPOSITE".equals(capability) ||
               "DATA_RETRIEVAL".equals(capability);
    }
    
    
    /**
     * Build blocked response.
     */
    private KernelResponse buildBlockedResponse(ReasoningPlan reasoningPlan, ComplianceResult complianceResult) {
        return KernelResponse.builder()
                .responseText(complianceResult.getBlockingMessage() != null 
                        ? complianceResult.getBlockingMessage() 
                        : "Request blocked by policy")
                .reasoningPlan(reasoningPlan)
                .complianceResult(complianceResult)
                .blocked(true)
                .responseType(reasoningPlan.getCapability())
                .confidence(reasoningPlan.getConfidence())
                .metadata(Map.of("blockedReason", complianceResult.getViolations() != null 
                        ? complianceResult.getViolations() 
                        : Collections.emptyList()))
                .build();
    }
    
    /**
     * Store entities in memory for future reference resolution.
     */
    private Mono<Void> storeEntitiesInMemory(ReasoningPlan reasoningPlan, ScenarioResult toolResult, 
                                             String sessionId, String userId) {
        if (sessionId == null || userId == null) {
            return Mono.empty(); // Cannot store without session/user context
        }
        
        return Mono.fromCallable(() -> {
            try {
                // Extract entities from reasoning plan parameters
                if (reasoningPlan.getParameters() != null) {
                    for (Map.Entry<String, Object> entry : reasoningPlan.getParameters().entrySet()) {
                        String paramName = entry.getKey();
                        Object paramValue = entry.getValue();
                        
                        // Store relevant entity types (ACCOUNT_ID, TRANSACTION_ID, etc.)
                        if (paramValue != null && isEntityType(paramName)) {
                            String entityValue = paramValue.toString();
                            Map<String, Object> metadata = Map.of(
                                    "source", "reasoning_plan",
                                    "paramName", paramName
                            );
                            memoryManager.storeEntity(sessionId, userId, paramName.toUpperCase(), 
                                    entityValue, metadata)
                                    .subscribe();
                        }
                    }
                }
                
                // Extract entities from tool result data
                if (toolResult != null && toolResult.getData() != null) {
                    extractEntitiesFromData(toolResult.getData(), sessionId, userId);
                }
            } catch (Exception e) {
                log.warn("Error storing entities in memory: {}", e.getMessage());
            }
            return null;
        }).then();
    }
    
    /**
     * Check if parameter name represents an entity type.
     */
    private boolean isEntityType(String paramName) {
        String upper = paramName.toUpperCase();
        return upper.contains("ID") || upper.contains("ACCOUNT") || upper.contains("TRANSACTION") ||
               upper.contains("CARD") || upper.contains("CUSTOMER") || upper.contains("DATE");
    }
    
    /**
     * Extract entities from tool result data.
     */
    private void extractEntitiesFromData(Map<String, Object> data, String sessionId, String userId) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value != null && isEntityType(key)) {
                String entityValue = value.toString();
                Map<String, Object> metadata = Map.of(
                        "source", "tool_result",
                        "fieldName", key
                );
                memoryManager.storeEntity(sessionId, userId, key.toUpperCase(), 
                        entityValue, metadata)
                        .subscribe();
            }
        }
    }
    
    /**
     * Apply data masking based on compliance guard results.
     */
    private String applyDataMasking(String text, Map<String, String> maskedData) {
        if (maskedData == null || maskedData.isEmpty()) {
            return text;
        }
        
        String maskedText = text;
        for (Map.Entry<String, String> entry : maskedData.entrySet()) {
            maskedText = maskedText.replace(entry.getKey(), entry.getValue());
        }
        
        return maskedText;
    }
    
    /**
     * Build metadata for response.
     */
    private Map<String, Object> buildMetadata(ReasoningPlan reasoningPlan, 
                                              List<RagRetrievalResult> ragContext, 
                                              ComplianceResult complianceResult) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("intent", reasoningPlan.getIntent());
        metadata.put("capability", reasoningPlan.getCapability());
        metadata.put("confidence", reasoningPlan.getConfidence());
        metadata.put("ragContextCount", ragContext != null ? ragContext.size() : 0);
        metadata.put("complianceRiskLevel", complianceResult.getRiskLevel());
        metadata.put("processedAt", System.currentTimeMillis());
        return metadata;
    }
    
    /**
     * Build compiled prompt for audit/logging purposes only.
     * This is optional - kept for backward compatibility with KernelResponse DTO.
     */
    private CompiledPrompt buildCompiledPromptForAudit(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            String sessionContext,
            ComplianceResult preCompliance) {
        try {
            return promptCompiler.compile(
                    reasoningPlan,
                    ragContext,
                    sessionContext,
                    buildExecutionContext(reasoningPlan),
                    extractPolicyConstraints(preCompliance)
            ).block(); // Block since we're building for audit only
        } catch (Exception e) {
            log.warn("Failed to build compiled prompt for audit: {}", e.getMessage());
            // Return minimal compiled prompt for backward compatibility
            return CompiledPrompt.builder()
                    .intent(reasoningPlan.getIntent())
                    .capability(reasoningPlan.getCapability())
                    .userQuery(reasoningPlan.getRawQuery())
                    .promptText("DLM-based response generation (no prompt compilation needed)")
                    .build();
        }
    }
}
