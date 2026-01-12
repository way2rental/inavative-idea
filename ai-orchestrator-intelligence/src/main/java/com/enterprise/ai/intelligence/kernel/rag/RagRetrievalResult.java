package com.enterprise.ai.intelligence.kernel.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * RAG Retrieval Result - Represents retrieved knowledge from RAG Engine.
 * 
 * Used by all retrievers (Domain, Intent, Tool, Response Pattern).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrievalResult {

    /**
     * Retrieval source type: DOMAIN_DOCUMENT, INTENT_DEFINITION, TOOL_DEFINITION, RESPONSE_PATTERN
     */
    private String sourceType;

    /**
     * Retrieved content (document text, scenario definition, tool definition, template)
     */
    private String content;

    /**
     * Relevance score (0.0 to 1.0)
     */
    private Double relevanceScore;

    /**
     * Metadata (source-specific information)
     * Example: {"category": "FAQ", "title": "Credit Card Billing"}
     */
    private Map<String, Object> metadata;

    /**
     * Source identifier (document ID, scenario code, tool name, template ID)
     */
    private String sourceId;
}
