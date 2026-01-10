package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.EntityPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for EntityPattern entity operations.
 */
@Repository
public interface EntityPatternRepository extends JpaRepository<EntityPattern, Long> {

    /**
     * Find patterns by entity type
     */
    List<EntityPattern> findByEntityType(String entityType);

    /**
     * Find active patterns by entity type, ordered by priority (descending - highest first)
     */
    @Query("SELECT e FROM EntityPattern e WHERE e.entityType = ?1 AND e.active = true ORDER BY e.priority DESC")
    List<EntityPattern> findActiveByEntityTypeOrderByPriorityDesc(String entityType);

    /**
     * Find patterns by pattern type
     */
    List<EntityPattern> findByPatternType(String patternType);

    /**
     * Find active patterns by pattern type
     */
    List<EntityPattern> findByPatternTypeAndActiveTrue(String patternType);

    /**
     * Find all active patterns, ordered by priority
     */
    @Query("SELECT e FROM EntityPattern e WHERE e.active = true ORDER BY e.priority DESC")
    List<EntityPattern> findAllActiveOrderByPriorityDesc();

    /**
     * Find pattern by entity type and pattern type
     */
    Optional<EntityPattern> findByEntityTypeAndPatternType(String entityType, String patternType);

    /**
     * Check if pattern exists for entity type
     */
    boolean existsByEntityType(String entityType);
}
