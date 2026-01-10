package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for dynamic prompt template management.
 * Supports versioning, testing, and runtime configuration.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_prompt_templates")
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique prompt key (e.g., INTENT_DETECTION, RESPONSE_FORMAT)
     */
    @Column(name = "prompt_key", unique = true, nullable = false, length = 100)
    private String promptKey;

    /**
     * Category for grouping prompts: SYSTEM, USER, INTENT, FORMAT, FOLLOWUP
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * System prompt that sets the AI's behavior
     */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /**
     * User template with placeholders like {{query}}, {{context}}
     */
    @Column(name = "user_template", columnDefinition = "TEXT")
    private String userTemplate;

    /**
     * Expected response format: TEXT, JSON, MARKDOWN
     */
    @Column(name = "response_format", length = 20)
    @Builder.Default
    private String responseFormat = "TEXT";

    /**
     * Temperature for LLM (0.0-2.0)
     */
    @Column(name = "temperature")
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * Maximum tokens for response
     */
    @Column(name = "max_tokens")
    @Builder.Default
    private Integer maxTokens = 1024;

    /**
     * Current version number
     */
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /**
     * Whether this prompt is enabled
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Version history as JSON array
     */
    @Column(name = "version_history", columnDefinition = "JSON")
    private String versionHistory;

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

    /**
     * Last updated by user
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
