package com.enterprise.ai.intelligence.service.concept;

import com.enterprise.ai.data.entity.BankingConcept;
import com.enterprise.ai.data.repository.BankingConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Banking Concept Service.
 * 
 * Provides banking domain concept matching and extraction.
 * Maps user terms to canonical banking concepts using concept dictionary.
 * 
 * Example:
 * - "CC" → CREDIT_CARD
 * - "balance" → ACCOUNT_BALANCE
 * - "credit card statement" → CREDIT_CARD + STATEMENT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingConceptService {

    private final BankingConceptRepository conceptRepository;

    /**
     * Extract concepts from query text.
     * 
     * @param query User query text
     * @return Map of concept code to confidence score (0.0-1.0)
     */
    @Cacheable(value = "bankingConcepts", key = "#query")
    public Map<String, Double> extractConcepts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Map.of();
        }

        try {
            // Get all active concepts
            List<BankingConcept> concepts = conceptRepository.findAllActiveOrderByPriorityDesc();
            
            Map<String, Double> extractedConcepts = new HashMap<>();
            String queryLower = query.toLowerCase();
            
            // Split query into words and phrases
            List<String> queryTerms = extractTerms(query);
            
            // Match each concept against query
            for (BankingConcept concept : concepts) {
                double score = calculateMatchScore(concept, queryLower, queryTerms);
                if (score > 0.0) {
                    // If we already have this concept, keep the higher score
                    Double existingScore = extractedConcepts.get(concept.getConceptCode());
                    if (existingScore == null || score > existingScore) {
                        extractedConcepts.put(concept.getConceptCode(), score);
                    }
                }
            }
            
            log.debug("Extracted {} concepts from query: {}", extractedConcepts.size(), extractedConcepts.keySet());
            return extractedConcepts;
            
        } catch (Exception e) {
            log.error("Error extracting concepts: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * Extract terms from query (words and phrases).
     */
    private List<String> extractTerms(String query) {
        List<String> terms = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        // Add full query
        terms.add(queryLower);
        
        // Add individual words
        String[] words = queryLower.split("\\s+");
        terms.addAll(List.of(words));
        
        // Add 2-word phrases
        for (int i = 0; i < words.length - 1; i++) {
            terms.add(words[i] + " " + words[i + 1]);
        }
        
        // Add 3-word phrases (for "credit card statement", etc.)
        for (int i = 0; i < words.length - 2; i++) {
            terms.add(words[i] + " " + words[i + 1] + " " + words[i + 2]);
        }
        
        return terms;
    }

    /**
     * Calculate match score for a concept against query.
     */
    private double calculateMatchScore(BankingConcept concept, String queryLower, List<String> queryTerms) {
        double score = 0.0;
        
        // Exact match on concept code (highest score)
        if (queryLower.contains(concept.getConceptCode().toLowerCase())) {
            score = Math.max(score, 0.95);
        }
        
        // Exact match on concept name
        if (concept.getConceptName() != null) {
            String conceptNameLower = concept.getConceptName().toLowerCase();
            if (queryLower.contains(conceptNameLower)) {
                score = Math.max(score, 0.90);
            }
        }
        
        // Match on synonyms
        List<String> synonyms = concept.getSynonymsList();
        for (String synonym : synonyms) {
            String synonymLower = synonym.toLowerCase();
            
            // Exact match on synonym
            if (queryTerms.contains(synonymLower)) {
                score = Math.max(score, 0.85);
            }
            
            // Partial match (synonym contains query term or vice versa)
            for (String term : queryTerms) {
                if (term.contains(synonymLower) || synonymLower.contains(term)) {
                    score = Math.max(score, 0.70);
                }
            }
        }
        
        // Boost score based on priority
        if (score > 0.0 && concept.getPriority() != null) {
            double priorityBoost = Math.min(concept.getPriority() / 100.0, 0.1);
            score = Math.min(score + priorityBoost, 1.0);
        }
        
        return score;
    }

    /**
     * Get concept by code.
     */
    public Optional<BankingConcept> getConcept(String conceptCode) {
        return conceptRepository.findByConceptCode(conceptCode);
    }

    /**
     * Get child concepts (for hierarchies).
     */
    public List<BankingConcept> getChildConcepts(String parentConceptCode) {
        return conceptRepository.findByParentConceptCodeAndActiveTrue(parentConceptCode);
    }

    /**
     * Get concepts by type.
     */
    public List<BankingConcept> getConceptsByType(String conceptType) {
        return conceptRepository.findByConceptTypeAndActiveTrueOrderByPriorityDesc(conceptType);
    }

    /**
     * Find concept that matches a term.
     */
    public Optional<BankingConcept> findMatchingConcept(String term) {
        List<BankingConcept> concepts = conceptRepository.findAllActiveOrderByPriorityDesc();
        return concepts.stream()
                .filter(c -> c.matches(term))
                .findFirst();
    }
}
