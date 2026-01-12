package com.enterprise.ai.intelligence.kernel.rag;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

/**
 * RAG Engine - System Knowledge Base.
 * 
 * RAG = RETRIEVABLE SYSTEM KNOWLEDGE
 * 
 * Retrieves knowledge from FOUR classes:
 * 1. Domain Documents (FAQs, Policies, Product definitions, SOPs)
 * 2. Intent Definitions (Allowed user intents, Required entities, Constraints)
 * 3. Tool Definitions (What the system CAN DO - DB queries, API calls, Business actions)
 * 4. Response Patterns (Output structure, Compliance language, UX consistency rules)
 * 
 * All knowledge is:
 * - Stored
 * - Versioned
 * - Retrievable
 * - Configurable via DB
 */
public interface RagEngine {

    /**
     * Retrieve domain documents relevant to query.
     * 
     * @param query User query
     * @param categories Optional categories to filter (FAQ, POLICY, PRODUCT, SOP)
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    Flux<RagRetrievalResult> retrieveDomainDocuments(String query, List<String> categories, int maxResults);

    /**
     * Retrieve intent definitions relevant to query.
     * 
     * @param query User query
     * @param allowedScenarios RBAC-filtered scenarios
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    Flux<RagRetrievalResult> retrieveIntentDefinitions(String query, Set<String> allowedScenarios, int maxResults);

    /**
     * Retrieve tool definitions relevant to query.
     * 
     * @param query User query
     * @param executionTypes Optional execution types to filter
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    Flux<RagRetrievalResult> retrieveToolDefinitions(String query, List<String> executionTypes, int maxResults);

    /**
     * Retrieve response patterns relevant to scenario.
     * 
     * @param scenarioCode Scenario code
     * @param responseType Optional response type to filter
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    Flux<RagRetrievalResult> retrieveResponsePatterns(String scenarioCode, String responseType, int maxResults);

    /**
     * Retrieve all knowledge types relevant to query (comprehensive retrieval).
     * 
     * @param query User query
     * @param allowedScenarios RBAC-filtered scenarios
     * @param maxResultsPerType Maximum results per knowledge type
     * @return Flux of RagRetrievalResult
     */
    Flux<RagRetrievalResult> retrieveAll(String query, Set<String> allowedScenarios, int maxResultsPerType);
}
