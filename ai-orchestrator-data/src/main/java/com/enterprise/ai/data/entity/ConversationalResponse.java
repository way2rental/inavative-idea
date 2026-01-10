package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for conversational responses (catch-all layer).
 * Handles greetings, thanks, goodbye, unknown queries.
 * 
 * NO HARDCODING - All responses come from database.
 * Responses use Freemarker templates with variables from SystemConfig.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_conversational_responses")
public class ConversationalResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Intent type: GREETING, THANKS, GOODBYE, UNKNOWN, HELP
     */
    @Column(name = "intent_type", nullable = false, length = 50)
    private String intentType;

    /**
     * User query patterns as JSON array that trigger this response
     * Example: ["hi", "hello", "hey", "good morning"]
     */
    @Column(name = "user_query_patterns", columnDefinition = "JSON")
    private String userQueryPatterns;

    /**
     * Response template (Freemarker format)
     * Variables: {{assistantName}}, {{orgName}}, {{scenarioCode}}, etc.
     */
    @Column(name = "response_template", nullable = false, columnDefinition = "TEXT")
    private String responseTemplate;

    /**
     * Suggested actions as JSON array
     * Example: ["Check account balance", "View transactions"]
     */
    @Column(name = "suggested_actions", columnDefinition = "JSON")
    private String suggestedActions;

    /**
     * Is this response context-aware?
     */
    @Column(name = "context_aware")
    @Builder.Default
    private Boolean contextAware = false;

    /**
     * Priority for matching (higher = more important)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this response active?
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
}
