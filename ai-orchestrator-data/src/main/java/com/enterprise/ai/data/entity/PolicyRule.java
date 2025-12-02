package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for policy rule management.
 * Defines business rules for controlling AI behavior and access.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_policy_rules")
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique policy key (e.g., MAX_QUERY_LENGTH, RATE_LIMIT)
     */
    @Column(name = "policy_key", unique = true, nullable = false, length = 100)
    private String policyKey;

    /**
     * Human-readable policy name
     */
    @Column(name = "policy_name", length = 200)
    private String policyName;

    /**
     * Policy description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Rule expression in SpEL or JSON format
     * Examples: 
     * - "#{query.length() < 1000}"
     * - "#{user.role == 'ADMIN' || scenario.securityLevel != 'HIGH'}"
     */
    @Column(name = "rule_expression", columnDefinition = "TEXT")
    private String ruleExpression;

    /**
     * Action when rule fails: BLOCK, WARN, LOG
     */
    @Column(name = "on_fail", length = 20)
    @Builder.Default
    private String onFail = "BLOCK";

    /**
     * Message to show when rule fails
     */
    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    /**
     * JSON array of scenario codes this policy applies to
     * If empty/null, applies to all scenarios
     */
    @Column(name = "applicable_scenarios", columnDefinition = "JSON")
    private String applicableScenarios;

    /**
     * JSON array of roles this policy applies to
     * If empty/null, applies to all roles
     */
    @Column(name = "applicable_roles", columnDefinition = "JSON")
    private String applicableRoles;

    /**
     * Priority for rule evaluation (higher = first)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether this policy is active
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Created by user
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
