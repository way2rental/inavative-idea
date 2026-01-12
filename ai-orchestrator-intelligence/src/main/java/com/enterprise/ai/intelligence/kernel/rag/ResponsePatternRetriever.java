package com.enterprise.ai.intelligence.kernel.rag;

import com.enterprise.ai.data.entity.ResponseTemplate;
import com.enterprise.ai.data.repository.ResponseTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Response Pattern Retriever for RAG Engine.
 * 
 * Retrieves response patterns from ResponseTemplate.
 * Shows how answers must be structured (compliance language, UX consistency rules).
 * 
 * REUSES existing components:
 * - ResponseTemplateRepository
 * - ResponseTemplate entity (already has template_content, response_type, conditions)
 * 
 * NO HARDCODING - All response patterns from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponsePatternRetriever {

    private final ResponseTemplateRepository templateRepository;

    /**
     * Retrieve response patterns relevant to scenario.
     * 
     * @param scenarioCode Scenario code
     * @param responseType Optional response type to filter
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    public Flux<RagRetrievalResult> retrieve(String scenarioCode, String responseType, int maxResults) {
        return Mono.fromCallable(() -> {
            try {
                // Get templates for scenario
                List<ResponseTemplate> templates;
                if (responseType != null && !responseType.isEmpty()) {
                    // Use the existing method and filter by response type
                    templates = templateRepository.findByScenarioCodeAndActiveTrueOrderByPriorityDesc(scenarioCode)
                            .stream()
                            .filter(t -> responseType.equals(t.getResponseType()))
                            .collect(Collectors.toList());
                } else {
                    templates = templateRepository.findByScenarioCodeAndActiveTrueOrderByPriorityDesc(scenarioCode);
                }
                
                // Sort by priority (descending) and limit
                return templates.stream()
                        .sorted((a, b) -> Integer.compare(
                                b.getPriority() != null ? b.getPriority() : 0,
                                a.getPriority() != null ? a.getPriority() : 0))
                        .limit(maxResults)
                        .map(this::toRetrievalResult)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to retrieve response patterns for scenario {}: {}", scenarioCode, e.getMessage(), e);
                return Collections.<RagRetrievalResult>emptyList();
            }
        })
        .flatMapMany(Flux::fromIterable);
    }
    
    /**
     * Convert ResponseTemplate to RagRetrievalResult.
     */
    private RagRetrievalResult toRetrievalResult(ResponseTemplate template) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("scenarioCode", template.getScenarioCode());
        metadata.put("responseType", template.getResponseType());
        metadata.put("priority", template.getPriority());
        metadata.put("templateVersion", template.getTemplateVersion());
        
        return RagRetrievalResult.builder()
                .sourceType("RESPONSE_PATTERN")
                .content(template.getTemplateContent())
                .relevanceScore(1.0) // All patterns for a scenario are equally relevant
                .metadata(metadata)
                .sourceId(template.getId().toString())
                .build();
    }
}
