package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for chat sessions.
 * Includes pending state for follow-up context retention.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true, length = 100)
    private String sessionId;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /**
     * Pending scenario code when awaiting user input for missing parameters.
     * Used to remember what scenario we were processing when we asked for more info.
     */
    @Column(name = "pending_scenario", length = 100)
    private String pendingScenario;

    /**
     * Pending missing parameters (comma-separated) when awaiting user input.
     * Example: "accountId,currency"
     */
    @Column(name = "pending_params", length = 500)
    private String pendingParams;

    /**
     * Already collected parameters (JSON format) for the pending scenario.
     * Example: {"userId": "123"}
     */
    @Column(name = "collected_params", length = 2000)
    private String collectedParams;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        lastActivityAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = Instant.now();
    }

    /**
     * Check if session has a pending follow-up scenario.
     */
    public boolean hasPendingFollowUp() {
        return pendingScenario != null && !pendingScenario.isEmpty();
    }

    /**
     * Clear the pending follow-up state.
     */
    public void clearPendingState() {
        this.pendingScenario = null;
        this.pendingParams = null;
        this.collectedParams = null;
    }
}
