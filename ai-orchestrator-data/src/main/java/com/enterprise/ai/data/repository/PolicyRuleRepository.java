package com.enterprise.ai.data.repository;

import com.enterprise.ai.data.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for policy rule management.
 */
@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    /**
     * Find by policy key
     */
    Optional<PolicyRule> findByPolicyKey(String policyKey);

    /**
     * Find all active policies
     */
    List<PolicyRule> findByActiveTrue();

    /**
     * Find policies containing a scenario code
     */
    List<PolicyRule> findByApplicableScenariosContaining(String scenarioCode);

    /**
     * Find policies containing a role
     */
    List<PolicyRule> findByApplicableRolesContaining(String role);

    /**
     * Find active policies ordered by priority
     */
    List<PolicyRule> findByActiveTrueOrderByPriorityDesc();

    /**
     * Check if policy exists by key
     */
    boolean existsByPolicyKey(String policyKey);
}
