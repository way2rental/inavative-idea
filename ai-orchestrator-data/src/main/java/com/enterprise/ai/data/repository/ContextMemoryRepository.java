package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ContextMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ContextMemory entity operations.
 */
@Repository
public interface ContextMemoryRepository extends JpaRepository<ContextMemory, Long> {

    /**
     * Find memory by session ID
     */
    List<ContextMemory> findBySessionId(String sessionId);

    /**
     * Find memory by session ID and entity type
     */
    List<ContextMemory> findBySessionIdAndEntityType(String sessionId, String entityType);

    /**
     * Find memory by user ID
     */
    List<ContextMemory> findByUserId(String userId);

    /**
     * Find memory by session ID and entity type and entity value
     */
    Optional<ContextMemory> findBySessionIdAndEntityTypeAndEntityValue(String sessionId, String entityType, String entityValue);

    /**
     * Find recent memory by session ID (last N records)
     */
    @Query("SELECT c FROM ContextMemory c WHERE c.sessionId = ?1 ORDER BY c.mentionedAt DESC")
    List<ContextMemory> findRecentBySessionId(String sessionId);

    /**
     * Find memory by entity type and value (across all sessions for a user)
     */
    List<ContextMemory> findByUserIdAndEntityTypeAndEntityValue(String userId, String entityType, String entityValue);

    /**
     * Delete old memory entries (cleanup)
     */
    void deleteByMentionedAtBefore(Instant cutoff);
}
