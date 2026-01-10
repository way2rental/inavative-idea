package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.FollowUpTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FollowUpTemplate entity operations.
 */
@Repository
public interface FollowUpTemplateRepository extends JpaRepository<FollowUpTemplate, Long> {

    /**
     * Find templates by scenario code
     */
    List<FollowUpTemplate> findByScenarioCode(String scenarioCode);

    /**
     * Find active templates by scenario code, ordered by priority
     */
    @Query("SELECT f FROM FollowUpTemplate f WHERE f.scenarioCode = ?1 AND f.active = true ORDER BY f.priority DESC")
    List<FollowUpTemplate> findByScenarioCodeAndActiveTrueOrderByPriorityDesc(String scenarioCode);

    /**
     * Find template by scenario code and param name
     */
    Optional<FollowUpTemplate> findByScenarioCodeAndParamName(String scenarioCode, String paramName);

    /**
     * Find active templates by scenario code and param name
     */
    List<FollowUpTemplate> findByScenarioCodeAndParamNameAndActiveTrue(String scenarioCode, String paramName);

    /**
     * Find all active templates
     */
    List<FollowUpTemplate> findByActiveTrue();
}
