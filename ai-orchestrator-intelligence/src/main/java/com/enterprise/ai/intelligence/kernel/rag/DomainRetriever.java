package com.enterprise.ai.intelligence.kernel.rag;

import com.enterprise.ai.data.entity.DomainDocument;
import com.enterprise.ai.data.repository.DomainDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain Document Retriever for RAG Engine.
 * 
 * Retrieves domain documents (FAQs, Policies, Product definitions, SOPs).
 * Uses keyword-based similarity search (generic approach).
 * 
 * REUSES existing components:
 * - DomainDocumentRepository
 * - DomainDocument entity
 * 
 * NO HARDCODING - All documents from database.
 * NO Spring AI dependencies - uses simple keyword matching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainRetriever {

    private final DomainDocumentRepository documentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Retrieve domain documents relevant to query.
     * 
     * @param query User query
     * @param categories Optional categories to filter (FAQ, POLICY, PRODUCT, SOP)
     * @param maxResults Maximum number of results
     * @return Flux of RagRetrievalResult
     */
    public Flux<RagRetrievalResult> retrieve(String query, List<String> categories, int maxResults) {
        return Mono.fromCallable(() -> {
            try {
                // Get all active documents
                List<DomainDocument> documents = documentRepository.findByActiveTrue();
                
                // Filter by category if specified
                if (categories != null && !categories.isEmpty()) {
                    documents = documents.stream()
                            .filter(doc -> doc.getCategory() != null && categories.contains(doc.getCategory()))
                            .collect(Collectors.toList());
                }
                
                // Score documents based on query match (keyword-based)
                List<ScoredDocument> scoredDocs = new ArrayList<>();
                String queryLower = query.toLowerCase();
                
                for (DomainDocument document : documents) {
                    double score = scoreDocument(queryLower, document);
                    if (score > 0.0) {
                        scoredDocs.add(new ScoredDocument(document, score));
                    }
                }
                
                // Sort by score (descending) and limit
                return scoredDocs.stream()
                        .sorted((a, b) -> Double.compare(b.score, a.score))
                        .limit(maxResults)
                        .map(scored -> toRetrievalResult(scored.document, scored.score))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to retrieve domain documents: {}", e.getMessage(), e);
                return Collections.<RagRetrievalResult>emptyList();
            }
        })
        .flatMapMany(Flux::fromIterable);
    }
    
    /**
     * Score document based on query match (keyword-based).
     * Uses title, content, category, tags.
     */
    private double scoreDocument(String queryLower, DomainDocument document) {
        double score = 0.0;
        
        // Match against title
        if (document.getTitle() != null) {
            String titleLower = document.getTitle().toLowerCase();
            if (titleLower.contains(queryLower)) {
                score += 0.5;
            }
            // Check individual words
            String[] queryWords = queryLower.split("\\s+");
            for (String word : queryWords) {
                if (word.length() > 2 && titleLower.contains(word)) {
                    score += 0.1;
                }
            }
        }
        
        // Match against content
        if (document.getContent() != null) {
            String contentLower = document.getContent().toLowerCase();
            if (contentLower.contains(queryLower)) {
                score += 0.3;
            }
            // Check individual words
            String[] queryWords = queryLower.split("\\s+");
            for (String word : queryWords) {
                if (word.length() > 2 && contentLower.contains(word)) {
                    score += 0.05;
                }
            }
        }
        
        // Match against category
        if (document.getCategory() != null && queryLower.contains(document.getCategory().toLowerCase())) {
            score += 0.2;
        }
        
        // Match against tags (if stored as JSON or comma-separated)
        if (document.getTags() != null) {
            String tagsLower = document.getTags().toLowerCase();
            if (tagsLower.contains(queryLower)) {
                score += 0.2;
            }
        }
        
        // Normalize score to 0.0-1.0 range
        return Math.min(score, 1.0);
    }
    
    /**
     * Convert DomainDocument to RagRetrievalResult.
     */
    private RagRetrievalResult toRetrievalResult(DomainDocument document, double score) {
        StringBuilder content = new StringBuilder();
        content.append("Title: ").append(document.getTitle() != null ? document.getTitle() : "").append("\n");
        if (document.getCategory() != null) {
            content.append("Category: ").append(document.getCategory()).append("\n");
        }
        if (document.getContent() != null) {
            content.append("Content: ").append(document.getContent()).append("\n");
        }
        if (document.getTags() != null) {
            content.append("Tags: ").append(document.getTags()).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", document.getId());
        metadata.put("category", document.getCategory());
        metadata.put("sourceType", "DOMAIN_DOCUMENT");
        
        return RagRetrievalResult.builder()
                .sourceType("DOMAIN_DOCUMENT")
                .content(content.toString())
                .relevanceScore(score)
                .metadata(metadata)
                .sourceId(document.getId() != null ? document.getId().toString() : null)
                .build();
    }
    
    /**
     * Helper class for scoring documents.
     */
    private static class ScoredDocument {
        final DomainDocument document;
        final double score;
        
        ScoredDocument(DomainDocument document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}
