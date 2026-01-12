package com.enterprise.ai.intelligence.kernel.response;

import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.intelligence.kernel.rag.RagRetrievalResult;
import com.enterprise.ai.intelligence.service.formatting.ResponseFormatterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Response Generator & Shaper Implementation - Stage 10.
 * 
 * DLM-Based Response Generation (Template-Driven, Deterministic).
 * 
 * Strategy:
 * 1. Use RAG context (response patterns) as templates
 * 2. Use ReasoningPlan (DLM output) for intent/context
 * 3. Use Tool results for data
 * 4. Generate response using template processing (simple string replacement)
 * 5. Format final response using ResponseFormatterService
 * 
 * NO external calls - uses only data passed from previous stages.
 * NO text generation - pure template-based processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseShaperImpl implements ResponseShaper {

    private final ResponseFormatterService responseFormatterService;

    @Override
    public Mono<String> generateResponse(
            ReasoningPlan reasoningPlan,
            List<RagRetrievalResult> ragContext,
            ScenarioResult toolResult) {
        
        return Mono.fromCallable(() -> {
            try {
                String capability = reasoningPlan.getCapability();
                String scenarioCode = reasoningPlan.getIntent();
                String query = reasoningPlan.getRawQuery();
                
                log.debug("Stage 10: Generating response using DLM output + RAG + Templates (capability: {}, scenario: {})", 
                        capability, scenarioCode);
                
                // Strategy based on capability
                String generatedResponse;
                switch (capability != null ? capability : "DIRECT_ANSWER") {
                    case "DIRECT_ANSWER":
                        generatedResponse = generateDirectAnswer(reasoningPlan, ragContext);
                        break;
                    
                    case "TOOL_EXECUTION":
                    case "DATA_RETRIEVAL":
                        generatedResponse = generateToolBasedResponse(reasoningPlan, ragContext, toolResult);
                        break;
                    
                    case "COMPOSITE":
                        generatedResponse = generateCompositeResponse(reasoningPlan, ragContext, toolResult);
                        break;
                    
                    default:
                        generatedResponse = generateDirectAnswer(reasoningPlan, ragContext);
                }
                
                // Format the generated response using ResponseFormatterService
                // This ensures proper JSON structure for frontend compatibility
                return formatFinalResponse(scenarioCode, generatedResponse, toolResult, query);
                
            } catch (Exception e) {
                log.error("Response generation failed: {}", e.getMessage(), e);
                return "I apologize, but I encountered an error processing your request. Please try again.";
            }
        })
        .doOnNext(response -> log.debug("Stage 10: Response generated and formatted (length: {})", 
                response != null ? response.length() : 0))
        .onErrorResume(error -> {
            log.error("Response generation failed: {}", error.getMessage(), error);
            return Mono.just("I apologize, but I encountered an error processing your request. Please try again.");
        });
    }
    
    /**
     * Generate direct answer using RAG context (domain documents) + response patterns.
     */
    private String generateDirectAnswer(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext) {
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
            return processTemplate(templateContent, domainDocs, null, query);
        }
        
        // Fallback: Generate response from RAG context directly
        return generateResponseFromRag(domainDocs, query);
    }
    
    /**
     * Generate response from tool results using RAG context + response patterns.
     */
    private String generateToolBasedResponse(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext,
                                           ScenarioResult toolResult) {
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
            return processTemplate(templateContent, domainDocs, toolResult, query);
        }
        
        // Fallback: Format tool result using response patterns
        return formatToolResultWithPatterns(toolResult, responsePatterns, domainDocs, query);
    }
    
    /**
     * Generate composite response combining multiple sources.
     */
    private String generateCompositeResponse(ReasoningPlan reasoningPlan, List<RagRetrievalResult> ragContext,
                                           ScenarioResult toolResult) {
        // Similar to tool-based but combines multiple sources
        return generateToolBasedResponse(reasoningPlan, ragContext, toolResult);
    }
    
    /**
     * Process template with RAG context and tool results.
     * Uses simple string replacement (no external template engine).
     */
    private String processTemplate(String templateContent, List<RagRetrievalResult> domainDocs, 
                                  ScenarioResult toolResult, String query) {
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
            
            return result;
            
        } catch (Exception e) {
            log.warn("Template processing failed: {}", e.getMessage());
            return generateResponseFromRag(domainDocs, query);
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
    private String generateResponseFromRag(List<RagRetrievalResult> domainDocs, String query) {
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
    
    /**
     * Format final response using ResponseFormatterService.
     * This ensures proper JSON structure for frontend compatibility.
     */
    private String formatFinalResponse(String scenarioCode, String generatedResponse, 
                                     ScenarioResult toolResult, String userQuery) {
        try {
            // Create ScenarioResult for formatting
            ScenarioResult resultForFormatting = ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(true)
                    .data(toolResult != null && toolResult.getData() != null 
                            ? toolResult.getData() 
                            : Map.of("response", generatedResponse))
                    .build();
            
            // Use ResponseFormatterService to format the response (returns JSON for UI)
            // This is a blocking call, but we're already in Mono.fromCallable
            return responseFormatterService.formatResponse(scenarioCode, resultForFormatting, userQuery)
                    .block(); // Block since we're in fromCallable
                    
        } catch (Exception e) {
            log.warn("Response formatting failed, using generated response as-is: {}", e.getMessage());
            return generatedResponse;
        }
    }
}
