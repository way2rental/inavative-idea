package com.enterprise.ai.intelligence.service.entity;

import com.enterprise.ai.data.entity.EntityPattern;
import com.enterprise.ai.data.repository.EntityPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for extracting entities from user queries.
 * Uses DB-driven entity patterns for extraction.
 * 
 * NO HARDCODING - All patterns from database.
 * 
 * Supports:
 * - REGEX patterns
 * - NER_MODEL patterns (future)
 * - CONTEXT_BASED patterns (future)
 * - Validation rules
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final EntityPatternRepository patternRepository;
    private final EntityPatternMatcher patternMatcher;
    private final EntityValidator entityValidator;

    /**
     * Extract entities from query.
     * 
     * @param query User query text
     * @param scenarioCode Optional scenario code to filter patterns
     * @return Map of entity type to EntityMatch (best match per type)
     */
    public Mono<Map<String, EntityMatch>> extractEntities(String query, String scenarioCode) {
        return Mono.fromCallable(() -> {
            try {
                // Get all active patterns, ordered by priority
                List<EntityPattern> patterns = patternRepository.findAllActiveOrderByPriorityDesc();
                
                // Filter by scenario if provided (can be enhanced later with scenario-specific patterns)
                // For now, extract all entities regardless of scenario
                
                Map<String, EntityMatch> extractedEntities = new HashMap<>();
                
                // Try each pattern
                for (EntityPattern pattern : patterns) {
                    // Skip if we already have a match for this entity type with higher confidence
                    EntityMatch existingMatch = extractedEntities.get(pattern.getEntityType());
                    if (existingMatch != null && existingMatch.getConfidence().compareTo(BigDecimal.valueOf(0.8)) >= 0) {
                        continue; // Skip if we already have a high-confidence match
                    }
                    
                    // Try to match pattern
                    Optional<EntityMatch> match = patternMatcher.match(pattern, query);
                    
                    if (match.isPresent()) {
                        EntityMatch entityMatch = match.get();
                        
                        // Validate extracted entity
                        if (entityValidator.validate(pattern, entityMatch.getValue())) {
                            // If we have an existing match, keep the one with higher confidence
                            if (existingMatch == null || 
                                entityMatch.getConfidence().compareTo(existingMatch.getConfidence()) > 0) {
                                extractedEntities.put(pattern.getEntityType(), entityMatch);
                                log.debug("Extracted entity: type={}, value={}, confidence={}", 
                                    pattern.getEntityType(), entityMatch.getValue(), entityMatch.getConfidence());
                            }
                        } else {
                            log.debug("Entity value {} failed validation for pattern {}", 
                                entityMatch.getValue(), pattern.getId());
                        }
                    }
                }
                
                log.info("Extracted {} entities from query: {}", extractedEntities.size(), 
                    extractedEntities.keySet());
                
                return extractedEntities;
            } catch (Exception e) {
                log.error("Error extracting entities: {}", e.getMessage(), e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Extract entities of specific type.
     * 
     * @param query User query text
     * @param entityType Entity type to extract
     * @return Optional EntityMatch if found
     */
    public Mono<Optional<EntityMatch>> extractEntity(String query, String entityType) {
        return extractEntities(query, (String) null)
            .map(entities -> Optional.ofNullable(entities.get(entityType)));
    }

    /**
     * Extract all entities of specific types.
     * 
     * @param query User query text
     * @param entityTypes List of entity types to extract
     * @return Map of entity type to EntityMatch
     */
    public Mono<Map<String, EntityMatch>> extractEntities(String query, List<String> entityTypes) {
        return extractEntities(query, (String) null)
            .map(entities -> entities.entrySet().stream()
                .filter(e -> entityTypes.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
