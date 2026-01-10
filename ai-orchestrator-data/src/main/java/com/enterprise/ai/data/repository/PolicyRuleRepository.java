package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PolicyRule entity operations.
 */
@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    /**
     * Find policy by unique key
     */
    Optional<PolicyRule> findByPolicyKey(String policyKey);

    /**
     * Check if policy key exists
     */
    boolean existsByPolicyKey(String policyKey);

    /**
     * Find all active policies
     */
    List<PolicyRule> findByActiveTrue();

    /**
     * Find active policies ordered by priority (descending)
     */
    List<PolicyRule> findByActiveTrueOrderByPriorityDesc();

    /**
     * Find all policies ordered by priority
     */
    List<PolicyRule> findAllByOrderByPriorityDesc();
}
