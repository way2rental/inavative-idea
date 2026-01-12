package com.enterprise.ai.intelligence.kernel.llm;

import com.enterprise.ai.common.dto.CompiledPrompt;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reasoning Engine Implementation - Stage 8.
 * 
 * RAG-powered template-based reasoning engine (like ChatGPT for our domain).
 * 
 * This is our OWN reasoning engine - NO external LLM calls.
 * NO external dependencies - NO repository calls, NO service calls, NO database calls.
 * 
 * Uses ONLY data passed from previous stages:
 * - CompiledPrompt (with system instructions, RAG context sections)
 * - RAG context (Domain Documents, Response Patterns)
 * - ReasoningPlan (intent, capability, query)
 * - ScenarioResult (tool execution results)
 * 
 * Strategy:
 * 1. For DIRECT_ANSWER: Use RAG context (domain documents) + response patterns
 * 2. For TOOL_EXECUTION: Format tool results using RAG context + response patterns  
 * 3. For COMPOSITE: Combine tool results + RAG context using response patterns
 */
@Slf4j
@Service
public class KernelLlmServiceImpl implements KernelLlmService {

    @Override
    public Mono<String> generateResponse(
            CompiledPrompt compiledPrompt,
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            ScenarioResult toolResult) {
        
        return Mono.fromCallable(() -> {
            try {
                String capability = reasoningPlan.getCapability();
                String scenarioCode = reasoningPlan.getIntent();
                
                log.debug("Stage 8: Generating response using RAG-powered reasoning (capability: {}, scenario: {})", 
                        capability, scenarioCode);
                
                // Strategy based on capability
                switch (capability != null ? capability : "DIRECT_ANSWER") {
                    case "DIRECT_ANSWER":
                        return generateDirectAnswer(reasoningPlan, ragContext, compiledPrompt);
                    
                    case "TOOL_EXECUTION":
                    case "DATA_RETRIEVAL":
                        return generateToolBasedResponse(reasoningPlan, ragContext, toolResult, compiledPrompt);
                    
                    case "COMPOSITE":
                        return generateCompositeResponse(reasoningPlan, ragContext, toolResult, compiledPrompt);
                    
                    default:
                        return generateDirectAnswer(reasoningPlan, ragContext, compiledPrompt);
                }
            } catch (Exception e) {
                log.error("Error in reasoning engine: {}", e.getMessage(), e);
                return "I apologize, but I encountered an error processing your request. Please try again.";
            }
        })
        .doOnNext(response -> log.debug("Stage 8: Generated response (length: {})", response != null ? response.length() : 0))
        .onErrorResume(error -> {
            log.error("Reasoning engine failed: {}", error.getMessage(), error);
            return Mono.just("I apologize, but I encountered an error processing your request. Please try again.");
        });
    }
    
    /**
     * Generate direct answer using RAG context (domain documents) + response patterns.
     */
    private String generateDirectAnswer(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext, 
                                       CompiledPrompt compiledPrompt) {
        String query = reasoningPlan.getRawQuery();
        
        // Get relevant domain documents from RAG context
        List<RagRetrievalResult> domainDocs = ragContext.stream()
                .filter(r -> "DOMAIN_DOCUMENT".equals(r.getSourceType()))
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(3) // Top 3 most relevant
                .collect(Collectors.toList());
        
        // Get response patterns from RAG context (templates)
        List<RagRetrievalResult> responsePatterns = ragContext.stream()
                .filter(r -> "RESPONSE_PATTERN".equals(r.getSourceType()))
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(1) // Use top pattern
                .collect(Collectors.toList());
        
        // Use response pattern template if available
        if (!responsePatterns.isEmpty()) {
            String templateContent = responsePatterns.get(0).getContent();
            return processTemplate(templateContent, domainDocs, null, query, compiledPrompt);
        }
        
        // Fallback: Generate response from RAG context directly
        return generateResponseFromRag(domainDocs, query, compiledPrompt);
    }
    
    /**
     * Generate response from tool results using RAG context + response patterns.
     */
    private String generateToolBasedResponse(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext,
                                           ScenarioResult toolResult, CompiledPrompt compiledPrompt) {
        String query = reasoningPlan.getRawQuery();
        
        // Get response patterns from RAG context (templates)
        List<RagRetrievalResult> responsePatterns = ragContext.stream()
                .filter(r -> "RESPONSE_PATTERN".equals(r.getSourceType()))
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(1) // Use top pattern
                .collect(Collectors.toList());
        
        // Get relevant domain documents for context
        List<RagRetrievalResult> domainDocs = ragContext.stream()
                .filter(r -> "DOMAIN_DOCUMENT".equals(r.getSourceType()))
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(2)
                .collect(Collectors.toList());
        
        // Use response pattern template if available
        if (!responsePatterns.isEmpty()) {
            String templateContent = responsePatterns.get(0).getContent();
            return processTemplate(templateContent, domainDocs, toolResult, query, compiledPrompt);
        }
        
        // Fallback: Format tool result using response patterns
        return formatToolResultWithPatterns(toolResult, responsePatterns, domainDocs, query);
    }
    
    /**
     * Generate composite response combining multiple sources.
     */
    private String generateCompositeResponse(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext,
                                           ScenarioResult toolResult, CompiledPrompt compiledPrompt) {
        // Similar to tool-based but combines multiple sources
        return generateToolBasedResponse(reasoningPlan, ragContext, toolResult, compiledPrompt);
    }
    
    /**
     * Process template with RAG context and tool results.
     * Uses simple string replacement (no external template engine).
     */
    private String processTemplate(String templateContent, List<RagRetrievalResult> domainDocs, 
                                  ScenarioResult toolResult, String query, CompiledPrompt compiledPrompt) {
        try {
            String result = templateContent;
            
            // Replace user query placeholder
            if (query != null) {
                result = result.replace("{{userQuery}}", query)
                              .replace("${userQuery}", query);
            }
            
            // Replace RAG context placeholder
            if (domainDocs != null && !domainDocs.isEmpty()) {
                StringBuilder ragContextText = new StringBuilder();
                for (RagRetrievalResult doc : domainDocs) {
                    ragContextText.append(doc.getContent()).append("\n\n");
                }
                String ragText = ragContextText.toString().trim();
                result = result.replace("{{ragContext}}", ragText)
                              .replace("${ragContext}", ragText);
            }
            
            // Replace tool result data placeholder
            if (toolResult != null && toolResult.getData() != null) {
                String dataStr = formatToolData(toolResult.getData());
                result = result.replace("{{data}}", dataStr)
                              .replace("${data}", dataStr)
                              .replace("{{toolResult}}", dataStr)
                              .replace("${toolResult}", dataStr);
            }
            
            // If compiled prompt has RAG sections, use them
            if (compiledPrompt != null && compiledPrompt.getRagContextSections() != null) {
                Map<String, String> sections = compiledPrompt.getRagContextSections();
                
                // Replace domain document section
                if (sections.containsKey("DOMAIN_DOCUMENT")) {
                    result = result.replace("{{domainDocument}}", sections.get("DOMAIN_DOCUMENT"))
                                  .replace("${domainDocument}", sections.get("DOMAIN_DOCUMENT"));
                }
                
                // Replace response pattern section
                if (sections.containsKey("RESPONSE_PATTERN")) {
                    result = result.replace("{{responsePattern}}", sections.get("RESPONSE_PATTERN"))
                                  .replace("${responsePattern}", sections.get("RESPONSE_PATTERN"));
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.warn("Template processing failed: {}", e.getMessage());
            return generateResponseFromRag(domainDocs, query, compiledPrompt);
        }
    }
    
    /**
     * Format tool data as string for template replacement.
     */
    private String formatToolData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // If data has a "data" key (common pattern)
        if (data.containsKey("data")) {
            Object dataValue = data.get("data");
            if (dataValue instanceof List) {
                // Format list
                List<?> list = (List<?>) dataValue;
                for (int i = 0; i < list.size(); i++) {
                    sb.append((i + 1)).append(". ").append(list.get(i)).append("\n");
                }
            } else if (dataValue instanceof Map) {
                // Format map as key-value pairs
                Map<?, ?> map = (Map<?, ?>) dataValue;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            } else {
                sb.append(dataValue);
            }
        } else {
            // Format entire map as key-value pairs
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Generate response directly from RAG context (fallback).
     */
    private String generateResponseFromRag(List<RagRetrievalResult> domainDocs, String query, 
                                         CompiledPrompt compiledPrompt) {
        if (domainDocs == null || domainDocs.isEmpty()) {
            return "I apologize, but I couldn't find relevant information to answer your question. Please try rephrasing your query.";
        }
        
        // Combine top RAG results
        StringBuilder response = new StringBuilder();
        response.append("Based on the available information:\n\n");
        
        for (int i = 0; i < Math.min(domainDocs.size(), 3); i++) {
            RagRetrievalResult doc = domainDocs.get(i);
            response.append(doc.getContent()).append("\n\n");
        }
        
        return response.toString().trim();
    }
    
    /**
     * Format tool result using response patterns (fallback).
     */
    private String formatToolResultWithPatterns(ScenarioResult toolResult, List<RagRetrievalResult> responsePatterns,
                                              List<RagRetrievalResult> domainDocs, String query) {
        if (toolResult == null || !toolResult.isSuccess()) {
            return "I apologize, but the requested operation could not be completed.";
        }
        
        // Use first response pattern if available
        if (!responsePatterns.isEmpty()) {
            String pattern = responsePatterns.get(0).getContent();
            // Simple pattern replacement
            return pattern.replace("{{data}}", toolResult.getData() != null ? formatToolData(toolResult.getData()) : "")
                         .replace("${data}", toolResult.getData() != null ? formatToolData(toolResult.getData()) : "");
        }
        
        // Fallback: Format tool result data
        if (toolResult.getData() != null && !toolResult.getData().isEmpty()) {
            return "Here is the requested information:\n" + formatToolData(toolResult.getData());
        }
        
        return "Operation completed successfully.";
    }
}