package com.enterprise.ai.intelligence.kernel.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RAG Engine Implementation.
 * 
 * Orchestrates all 4 retrievers:
 * 1. DomainRetriever - Domain documents (FAQs, Policies, Products, SOPs)
 * 2. IntentRetriever - Intent definitions (from scenarios)
 * 3. ToolRetriever - Tool definitions (from scenario execution config)
 * 4. ResponsePatternRetriever - Response patterns (from ResponseTemplate)
 * 
 * REUSES existing components and retrievers.
 * NO HARDCODING - All knowledge from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEngineImpl implements RagEngine {

    private final DomainRetriever domainRetriever;
    private final IntentRetriever intentRetriever;
    private final ToolRetriever toolRetriever;
    private final ResponsePatternRetriever responsePatternRetriever;

    @Override
    public Flux<RagRetrievalResult> retrieveDomainDocuments(String query, List<String> categories, int maxResults) {
        log.debug("Retrieving domain documents for query: {}", query);
        return domainRetriever.retrieve(query, categories, maxResults);
    }

    @Override
    public Flux<RagRetrievalResult> retrieveIntentDefinitions(String query, Set<String> allowedScenarios, int maxResults) {
        log.debug("Retrieving intent definitions for query: {}", query);
        return intentRetriever.retrieve(query, allowedScenarios, maxResults);
    }

    @Override
    public Flux<RagRetrievalResult> retrieveToolDefinitions(String query, List<String> executionTypes, int maxResults) {
        log.debug("Retrieving tool definitions for query: {}", query);
        return toolRetriever.retrieve(query, executionTypes, maxResults);
    }

    @Override
    public Flux<RagRetrievalResult> retrieveResponsePatterns(String scenarioCode, String responseType, int maxResults) {
        log.debug("Retrieving response patterns for scenario: {}", scenarioCode);
        return responsePatternRetriever.retrieve(scenarioCode, responseType, maxResults);
    }

    @Override
    public Flux<RagRetrievalResult> retrieveAll(String query, Set<String> allowedScenarios, int maxResultsPerType) {
        log.debug("Retrieving all knowledge types for query: {}", query);
        
        // Retrieve from all sources in parallel and merge
        Flux<RagRetrievalResult> domainDocs = retrieveDomainDocuments(query, null, maxResultsPerType);
        Flux<RagRetrievalResult> intentDefs = retrieveIntentDefinitions(query, allowedScenarios, maxResultsPerType);
        Flux<RagRetrievalResult> toolDefs = retrieveToolDefinitions(query, null, maxResultsPerType);
        
        // Note: Response patterns need scenario code, so we skip it in "retrieveAll"
        // Response patterns are retrieved per-scenario, not per-query
        
        return Flux.merge(domainDocs, intentDefs, toolDefs)
                .doOnComplete(() -> log.debug("RAG retrieval completed for query: {}", query));
    }
}
