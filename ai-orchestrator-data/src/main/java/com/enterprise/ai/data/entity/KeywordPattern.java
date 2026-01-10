package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for keyword matching patterns.
 * Used by the keyword-based fallback layer for simple matching.
 * 
 * NO HARDCODING - All keywords come from database.
 * Keywords can be added/updated via Admin Panel.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_keyword_patterns")
public class KeywordPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Scenario code this keyword belongs to
     */
    @Column(name = "scenario_code", nullable = false, length = 100)
    private String scenarioCode;

    /**
     * Keyword/phrase
     */
    @Column(name = "keyword", nullable = false, length = 255)
    private String keyword;

    /**
     * Keyword weight for scoring (0.0 to 1.0)
     * Higher weight = more important
     */
    @Column(name = "weight")
    @Builder.Default
    private Double weight = 1.0;

    /**
     * Synonyms as JSON array
     */
    @Column(name = "synonyms", columnDefinition = "JSON")
    private String synonyms;

    /**
     * Context hints as JSON (when this keyword is more relevant)
     * Example: {"previous_scenario": "ACCOUNT_BALANCE", "has_params": ["accountId"]}
     */
    @Column(name = "context_hints", columnDefinition = "JSON")
    private String contextHints;

    /**
     * Is this keyword active?
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Relationship to scenario (using scenario_code as foreign key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_code", referencedColumnName = "scenario_code", insertable = false, updatable = false)
    private AiScenario scenario;
}
