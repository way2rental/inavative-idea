package com.enterprise.ai.intelligence.kernel.memory;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Memory Manager for Axis AI Kernel.
 * 
 * Manages conversational and contextual memory.
 * 
 * Responsibilities:
 * - Short-term context (current session)
 * - Long-term references (summaries, preferences)
 * - Reference resolution ("same account", "that transaction")
 * 
 * Memory is:
 * - Structured
 * - Queryable
 * - Auditable
 */
public interface MemoryManager {
    
    /**
     * Store an entity in context memory.
     * 
     * @param sessionId Session ID
     * @param userId User ID
     * @param entityType Entity type (e.g., "ACCOUNT_ID", "TRANSACTION_ID")
     * @param entityValue Entity value
     * @param metadata Additional metadata
     * @return Mono of void
     */
    Mono<Void> storeEntity(String sessionId, String userId, String entityType, 
                          String entityValue, Map<String, Object> metadata);
    
    /**
     * Resolve a reference (e.g., "same account", "that transaction").
     * 
     * @param sessionId Session ID
     * @param query User query
     * @param entityType Entity type to resolve
     * @return Mono of Optional entity value if reference found
     */
    Mono<Optional<String>> resolveReference(String sessionId, String query, String entityType);
    
    /**
     * Get most recent entity of a type from session memory.
     * 
     * @param sessionId Session ID
     * @param entityType Entity type
     * @return Mono of Optional entity value
     */
    Mono<Optional<String>> getMostRecentEntity(String sessionId, String entityType);
    
    /**
     * Clear context memory for a session.
     * 
     * @param sessionId Session ID
     * @return Mono of void
     */
    Mono<Void> clearContext(String sessionId);
}
