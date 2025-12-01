package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for policy rules.
 * Enables runtime policy evaluation for access control.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_policies")
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique policy key
     */
    @Column(name = "policy_key", unique = true, nullable = false, length = 100)
    private String policyKey;

    /**
     * Policy name
     */
    @Column(name = "policy_name", length = 200)
    private String policyName;

    /**
     * Policy description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Rule expression (e.g., request.userId == resource.ownerId)
     */
    @Column(name = "rule_expression", columnDefinition = "TEXT")
    private String ruleExpression;

    /**
     * Action on rule failure (BLOCK, WARN, LOG)
     */
    @Column(name = "on_fail", length = 20)
    @Builder.Default
    private String onFail = "BLOCK";

    /**
     * Message to show on failure
     */
    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    /**
     * Scenarios this policy applies to (JSON array)
     */
    @Column(name = "applicable_scenarios", columnDefinition = "JSON")
    private String applicableScenarios;

    /**
     * Roles this policy applies to (JSON array)
     */
    @Column(name = "applicable_roles", columnDefinition = "JSON")
    private String applicableRoles;

    /**
     * Priority for policy evaluation order
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
