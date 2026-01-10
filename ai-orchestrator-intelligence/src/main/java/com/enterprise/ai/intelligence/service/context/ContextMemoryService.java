package com.enterprise.ai.intelligence.service.context;

import com.enterprise.ai.data.entity.ContextMemory;
import com.enterprise.ai.data.repository.ContextMemoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing context memory (session-based entity tracking).
 * Enables coreference resolution ("same account", "that transaction").
 * 
 * NO HARDCODING - All entity types from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextMemoryService {

    private final ContextMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Store an entity in context memory.
     * 
     * @param sessionId Session ID
     * @param userId User ID
     * @param entityType Entity type (DB-driven)
     * @param entityValue Entity value
     * @param metadata Additional metadata
     */
    public Mono<Void> storeEntity(String sessionId, String userId, String entityType, 
                                  String entityValue, Map<String, Object> metadata) {
        return Mono.fromCallable(() -> {
            try {
                // Check if entity already exists in this session
                Optional<ContextMemory> existing = memoryRepository
                    .findBySessionIdAndEntityTypeAndEntityValue(sessionId, entityType, entityValue);
                
                if (existing.isPresent()) {
                    // Update existing: increment reference count, update last referenced
                    ContextMemory memory = existing.get();
                    memory.setLastReferenced(Instant.now());
                    memory.setReferenceCount(memory.getReferenceCount() + 1);
                    
                    // Update metadata if provided
                    if (metadata != null && !metadata.isEmpty()) {
                        String metadataJson = objectMapper.writeValueAsString(metadata);
                        memory.setEntityMetadata(metadataJson);
                    }
                    
                    memoryRepository.save(memory);
                    log.debug("Updated context memory: session={}, type={}, value={}, count={}", 
                        sessionId, entityType, entityValue, memory.getReferenceCount());
                } else {
                    // Create new memory entry
                    String metadataJson = metadata != null && !metadata.isEmpty() 
                        ? objectMapper.writeValueAsString(metadata) 
                        : null;
                    
                    ContextMemory memory = ContextMemory.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .entityType(entityType)
                        .entityValue(entityValue)
                        .entityMetadata(metadataJson)
                        .mentionedAt(Instant.now())
                        .lastReferenced(Instant.now())
                        .referenceCount(1)
                        .build();
                    
                    memoryRepository.save(memory);
                    log.debug("Stored new context memory: session={}, type={}, value={}", 
                        sessionId, entityType, entityValue);
                }
                
                return null;
            } catch (Exception e) {
                log.error("Error storing entity in context memory: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to store context memory", e);
            }
        })
        .then();
    }

    /**
     * Resolve a reference (e.g., "same account", "that transaction").
     * 
     * @param sessionId Session ID
     * @param query User query
     * @param entityType Entity type to resolve
     * @return Optional entity value if reference found
     */
    public Mono<Optional<String>> resolveReference(String sessionId, String query, String entityType) {
        return Mono.fromCallable(() -> {
            try {
                // Check for reference words
                String queryLower = query.toLowerCase();
                boolean hasReference = queryLower.matches(".*\\b(same|that|this|it|them|the)\\s+(account|transaction|payment|transfer|amount|date|id).*") ||
                                     queryLower.contains("same " + entityType.toLowerCase()) ||
                                     queryLower.contains("that " + entityType.toLowerCase()) ||
                                     queryLower.contains("this " + entityType.toLowerCase());
                
                if (!hasReference) {
                    return Optional.empty();
                }
                
                // Get most recent entity of this type from context
                List<ContextMemory> memories = memoryRepository.findBySessionIdAndEntityType(sessionId, entityType);
                
                if (memories.isEmpty()) {
                    log.debug("No context memory found for reference: session={}, type={}", sessionId, entityType);
                    return Optional.empty();
                }
                
                // Sort by last referenced (most recent first), then by reference count
                memories.sort((a, b) -> {
                    int timeCompare = b.getLastReferenced().compareTo(a.getLastReferenced());
                    if (timeCompare != 0) return timeCompare;
                    return b.getReferenceCount().compareTo(a.getReferenceCount());
                });
                
                ContextMemory mostRecent = memories.get(0);
                
                // Update last referenced
                mostRecent.setLastReferenced(Instant.now());
                mostRecent.setReferenceCount(mostRecent.getReferenceCount() + 1);
                memoryRepository.save(mostRecent);
                
                log.debug("Resolved reference: session={}, type={}, value={}", 
                    sessionId, entityType, mostRecent.getEntityValue());
                
                return Optional.of(mostRecent.getEntityValue());
            } catch (Exception e) {
                log.error("Error resolving reference: {}", e.getMessage(), e);
                return Optional.empty();
            }
        });
    }

    /**
     * Get all entities of a specific type from context.
     * 
     * @param sessionId Session ID
     * @param entityType Entity type
     * @return List of entity values
     */
    public Mono<List<String>> getEntitiesByType(String sessionId, String entityType) {
        return Mono.fromCallable(() -> {
            List<ContextMemory> memories = memoryRepository.findBySessionIdAndEntityType(sessionId, entityType);
            return memories.stream()
                .map(ContextMemory::getEntityValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        });
    }

    /**
     * Get most recent entity of a specific type.
     * 
     * @param sessionId Session ID
     * @param entityType Entity type
     * @return Optional entity value
     */
    public Mono<Optional<String>> getMostRecentEntity(String sessionId, String entityType) {
        return Mono.fromCallable(() -> {
            List<ContextMemory> memories = memoryRepository.findRecentBySessionId(sessionId);
            
            return memories.stream()
                .filter(m -> entityType.equals(m.getEntityType()))
                .findFirst()
                .map(ContextMemory::getEntityValue);
        });
    }

    /**
     * Clear context memory for a session.
     * 
     * @param sessionId Session ID
     */
    public Mono<Void> clearContext(String sessionId) {
        return Mono.fromRunnable(() -> {
            List<ContextMemory> memories = memoryRepository.findBySessionId(sessionId);
            memoryRepository.deleteAll(memories);
            log.info("Cleared context memory for session: {}", sessionId);
        });
    }

    /**
     * Cleanup old context memory (older than specified days).
     * 
     * @param daysOld Number of days
     */
    public Mono<Void> cleanupOldMemory(int daysOld) {
        return Mono.fromRunnable(() -> {
            Instant cutoff = Instant.now().minusSeconds(daysOld * 24L * 60 * 60);
            memoryRepository.deleteByMentionedAtBefore(cutoff);
            log.info("Cleaned up context memory older than {} days", daysOld);
        });
    }
}
