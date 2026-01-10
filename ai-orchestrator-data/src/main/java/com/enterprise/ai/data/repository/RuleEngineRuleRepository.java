package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.RuleEngineRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RuleEngineRule entity operations.
 */
@Repository
public interface RuleEngineRuleRepository extends JpaRepository<RuleEngineRule, Long> {

    /**
     * Find rule by unique code
     */
    Optional<RuleEngineRule> findByRuleCode(String ruleCode);

    /**
     * Find active rules ordered by priority (descending - highest first)
     */
    @Query("SELECT r FROM RuleEngineRule r WHERE r.active = true ORDER BY r.priority DESC")
    List<RuleEngineRule> findAllActiveOrderByPriorityDesc();

    /**
     * Find rules by scenario code
     */
    List<RuleEngineRule> findByScenarioCode(String scenarioCode);

    /**
     * Find active rules by scenario code
     */
    List<RuleEngineRule> findByScenarioCodeAndActiveTrue(String scenarioCode);

    /**
     * Find active general rules (scenarioCode is null)
     */
    List<RuleEngineRule> findByScenarioCodeIsNullAndActiveTrue();

    /**
     * Find rules by condition type
     */
    List<RuleEngineRule> findByConditionTypeAndActiveTrue(String conditionType);
}
