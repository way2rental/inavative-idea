package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for chat sessions.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionId(String sessionId);

    List<ChatSession> findByUserId(String userId);

    boolean existsBySessionId(String sessionId);

    // Filter by userId
    Page<ChatSession> findByUserIdContainingIgnoreCase(String userId, Pageable pageable);

    // Filter by sessionId
    Page<ChatSession> findBySessionIdContainingIgnoreCase(String sessionId, Pageable pageable);

    // Combined filter query
    @Query("SELECT cs FROM ChatSession cs WHERE " +
           "(:userId IS NULL OR LOWER(cs.userId) LIKE LOWER(CONCAT('%', :userId, '%'))) AND " +
           "(:sessionId IS NULL OR LOWER(cs.sessionId) LIKE LOWER(CONCAT('%', :sessionId, '%')))")
    Page<ChatSession> findByFilters(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            Pageable pageable
    );
}
