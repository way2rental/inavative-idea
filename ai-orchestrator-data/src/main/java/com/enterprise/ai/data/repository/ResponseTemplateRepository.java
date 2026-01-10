package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.ResponseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ResponseTemplate entity.
 */
@Repository
public interface ResponseTemplateRepository extends JpaRepository<ResponseTemplate, Long> {

    /**
     * Find all active templates for a scenario, ordered by priority (highest first).
     */
    @Query("SELECT rt FROM ResponseTemplate rt WHERE rt.scenarioCode = ?1 AND rt.active = true ORDER BY rt.priority DESC, rt.templateVersion DESC")
    List<ResponseTemplate> findByScenarioCodeAndActiveTrueOrderByPriorityDesc(String scenarioCode);

    /**
     * Find template by scenario code and response type.
     */
    @Query("SELECT rt FROM ResponseTemplate rt WHERE rt.scenarioCode = ?1 AND rt.responseType = ?2 AND rt.active = true ORDER BY rt.priority DESC, rt.templateVersion DESC")
    Optional<ResponseTemplate> findFirstByScenarioCodeAndResponseTypeAndActiveTrueOrderByPriorityDesc(String scenarioCode, String responseType);

    /**
     * Find all active templates, ordered by priority.
     */
    @Query("SELECT rt FROM ResponseTemplate rt WHERE rt.active = true ORDER BY rt.scenarioCode, rt.priority DESC")
    List<ResponseTemplate> findAllActiveOrderByPriorityDesc();

    /**
     * Find all templates for a scenario (active and inactive).
     */
    List<ResponseTemplate> findByScenarioCode(String scenarioCode);
}
