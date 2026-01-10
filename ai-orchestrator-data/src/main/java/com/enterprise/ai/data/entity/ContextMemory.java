package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for context memory (session-based).
 * Tracks entities mentioned in conversation for reference resolution.
 * 
 * NO HARDCODING - All entity types come from database.
 * Used for resolving references like "same account", "that transaction".
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_context_memory")
public class ContextMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Session ID
     */
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    /**
     * User ID
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * Entity type (DB-driven - e.g., "ACCOUNT_ID", "TRANSACTION_ID", "DATE", etc.)
     * NOT hardcoded - comes from ai_entity_patterns
     */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * Actual entity value (e.g., "ACC001", "TXN123")
     */
    @Column(name = "entity_value", length = 500)
    private String entityValue;

    /**
     * Additional context metadata as JSON
     */
    @Column(name = "entity_metadata", columnDefinition = "JSON")
    private String entityMetadata;

    /**
     * When this entity was first mentioned
     */
    @Column(name = "mentioned_at")
    @Builder.Default
    private Instant mentionedAt = Instant.now();

    /**
     * When this entity was last referenced
     */
    @Column(name = "last_referenced")
    private Instant lastReferenced;

    /**
     * Number of times this entity was referenced
     */
    @Column(name = "reference_count")
    @Builder.Default
    private Integer referenceCount = 1;
}
