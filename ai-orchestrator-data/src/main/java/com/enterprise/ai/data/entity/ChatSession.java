package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for chat sessions.
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

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        lastActivityAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = Instant.now();
    }
}
