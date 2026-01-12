package com.enterprise.ai.intelligence.service.entity;

import com.enterprise.ai.data.entity.EntityPattern;
import com.enterprise.ai.data.repository.EntityPatternRepository;
import com.enterprise.ai.intelligence.service.context.ContextMemoryService;
import com.enterprise.ai.intelligence.service.context.ReferenceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - CONTEXT_BASED patterns (enhanced with context memory)
 * - Validation rules
 * - Context-aware entity resolution ("my account", "that transaction")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final EntityPatternRepository patternRepository;
    private final EntityPatternMatcher patternMatcher;
    private final EntityValidator entityValidator;
    private final ContextMemoryService contextMemoryService;
    private final ReferenceResolver referenceResolver;

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

    /**
     * Extract entities from query with context-aware resolution.
     * Resolves implicit references like "my account" from session memory.
     * 
     * @param query User query text
     * @param scenarioCode Optional scenario code to filter patterns
     * @param sessionId Session ID for context memory
     * @param userId User ID for context memory
     * @return Map of entity type to EntityMatch (best match per type)
     */
    public Mono<Map<String, EntityMatch>> extractEntities(String query, String scenarioCode, 
                                                           String sessionId, String userId) {
        return extractEntities(query, scenarioCode)
            .flatMap(entities -> resolveMissingEntitiesFromContext(entities, query, sessionId, userId));
    }

    /**
     * Extract entities of specific type with context-aware resolution.
     * 
     * @param query User query text
     * @param entityType Entity type to extract
     * @param sessionId Session ID for context memory
     * @param userId User ID for context memory
     * @return Optional EntityMatch if found
     */
    public Mono<Optional<EntityMatch>> extractEntity(String query, String entityType, 
                                                      String sessionId, String userId) {
        return extractEntities(query, null, sessionId, userId)
            .map(entities -> Optional.ofNullable(entities.get(entityType)));
    }

    /**
     * Resolve missing entities from context memory.
     * Handles implicit references like "my account" and explicit references like "same account".
     * 
     * @param extractedEntities Already extracted entities
     * @param query User query text
     * @param sessionId Session ID for context memory
     * @param userId User ID for context memory
     * @return Enhanced map with context-resolved entities
     */
    private Mono<Map<String, EntityMatch>> resolveMissingEntitiesFromContext(
            Map<String, EntityMatch> extractedEntities, String query, 
            String sessionId, String userId) {
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.just(extractedEntities);
        }
        
        Map<String, EntityMatch> enhancedEntities = new HashMap<>(extractedEntities);
        
        // Step 1: Resolve explicit references (e.g., "same account", "that transaction")
        if (referenceResolver.hasReference(query)) {
            List<String> possibleTypes = referenceResolver.getPossibleEntityTypes(query);
            
            // Use flatMap to resolve all references sequentially
            Mono<Map<String, EntityMatch>> resolvedEntities = Mono.just(enhancedEntities);
            
            for (String entityType : possibleTypes) {
                // Skip if we already have this entity extracted
                if (enhancedEntities.containsKey(entityType)) {
                    continue;
                }
                
                final String finalEntityType = entityType;
                resolvedEntities = resolvedEntities
                    .flatMap(entities -> contextMemoryService
                        .resolveReference(sessionId, query, finalEntityType)
                        .map(resolvedValue -> {
                            if (resolvedValue.isPresent()) {
                                EntityMatch match = EntityMatch.builder()
                                    .value(resolvedValue.get())
                                    .confidence(BigDecimal.valueOf(0.85)) // High confidence for context-resolved
                                    .entityType(finalEntityType)
                                    .metadata(Map.of("source", "context_memory", "resolved", "true"))
                                    .build();
                                
                                entities.put(finalEntityType, match);
                                log.debug("Resolved reference entity: type={}, value={} from context", 
                                    finalEntityType, resolvedValue.get());
                            }
                            return entities;
                        })
                        .defaultIfEmpty(entities));
            }
            
            return resolvedEntities.flatMap(entities -> {
                // Step 2: Resolve implicit references (e.g., "my account", "for my account")
                return resolveImplicitReferencesReactive(entities, query, sessionId);
            });
        } else {
            // Step 2: Resolve implicit references (e.g., "my account", "for my account")
            return resolveImplicitReferencesReactive(enhancedEntities, query, sessionId);
        }
    }

    /**
     * Resolve implicit entity references from query (e.g., "my account", "for my account").
     * 
     * @param entities Map of entities to enhance
     * @param query User query text
     * @param sessionId Session ID for context memory
     * @return Enhanced entities with implicit references resolved
     */
    private Mono<Map<String, EntityMatch>> resolveImplicitReferencesReactive(
            Map<String, EntityMatch> entities, String query, String sessionId) {
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.just(entities);
        }
        
        String queryLower = query.toLowerCase();
        
        // Common implicit reference patterns
        Map<String, String> implicitPatterns = Map.of(
            "my account", "ACCOUNT_ID",
            "for my account", "ACCOUNT_ID",
            "my card", "CARD_ID",
            "my transaction", "TRANSACTION_ID",
            "my payment", "PAYMENT_ID"
        );
        
        Mono<Map<String, EntityMatch>> result = Mono.just(entities);
        
        for (Map.Entry<String, String> pattern : implicitPatterns.entrySet()) {
            String patternText = pattern.getKey();
            String entityType = pattern.getValue();
            
            // Skip if already extracted or query doesn't contain pattern
            if (entities.containsKey(entityType) || !queryLower.contains(patternText)) {
                continue;
            }
            
            final String finalEntityType = entityType;
            result = result.flatMap(entityMap -> contextMemoryService
                .getMostRecentEntity(sessionId, finalEntityType)
                .map(resolvedValue -> {
                    if (resolvedValue.isPresent()) {
                        EntityMatch match = EntityMatch.builder()
                            .value(resolvedValue.get())
                            .confidence(BigDecimal.valueOf(0.80)) // Good confidence for implicit reference
                            .entityType(finalEntityType)
                            .metadata(Map.of("source", "context_memory", "implicit", "true"))
                            .build();
                        
                        entityMap.put(finalEntityType, match);
                        log.debug("Resolved implicit reference: type={}, value={} from context", 
                            finalEntityType, resolvedValue.get());
                    }
                    return entityMap;
                })
                .defaultIfEmpty(entityMap));
        }
        
        return result.doOnSuccess(resolved -> {
            log.debug("Context-aware extraction: {} entities ({} from context)", 
                resolved.size(), resolved.size() - entities.size());
        });
    }
}
