package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for dynamic follow-up question groups.
 * Supports runtime configuration of follow-up questions per scenario.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_followup_groups")
public class FollowUpGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique group key (e.g., TXN_FOLLOWUPS)
     */
    @Column(name = "group_key", unique = true, nullable = false, length = 100)
    private String groupKey;

    /**
     * Human-readable description
     */
    @Column(length = 500)
    private String description;

    /**
     * Associated scenario codes (JSON array)
     */
    @Column(name = "scenario_codes", columnDefinition = "JSON")
    private String scenarioCodes;

    /**
     * Questions configuration as JSON array
     * Each question has: key, question, type, required, validationPattern
     */
    @Column(name = "questions", columnDefinition = "JSON")
    private String questions;

    /**
     * Order in which questions should be asked
     */
    @Column(name = "question_order", columnDefinition = "JSON")
    private String questionOrder;

    /**
     * Whether this group is active
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
