package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ConversationalResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConversationalResponse entity operations.
 */
@Repository
public interface ConversationalResponseRepository extends JpaRepository<ConversationalResponse, Long> {

    /**
     * Find response by intent type
     */
    Optional<ConversationalResponse> findByIntentType(String intentType);

    /**
     * Find active responses ordered by priority (descending - highest first)
     */
    @Query("SELECT c FROM ConversationalResponse c WHERE c.active = true ORDER BY c.priority DESC")
    List<ConversationalResponse> findAllActiveOrderByPriorityDesc();

    /**
     * Find responses by intent type
     */
    List<ConversationalResponse> findByIntentTypeAndActiveTrue(String intentType);
}
