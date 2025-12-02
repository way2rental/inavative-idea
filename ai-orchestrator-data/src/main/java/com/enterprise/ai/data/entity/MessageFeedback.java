package com.enterprise.ai.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for storing user feedback on AI responses.
 * Used for model improvement and quality monitoring.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_feedback")
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "user_id", length = 100)
    private String userId;

    /**
     * Feedback type: 'like' or 'dislike'
     */
    @Column(name = "feedback_type", length = 20)
    private String feedbackType;

    /**
     * Optional comment from user explaining the feedback
     */
    @Column(name = "comment", length = 1000)
    private String comment;

    /**
     * The scenario that was executed (for analytics)
     */
    @Column(name = "scenario_code", length = 100)
    private String scenarioCode;

    /**
     * The user's original query
     */
    @Column(name = "user_query", length = 2000)
    private String userQuery;

    /**
     * The AI's response (truncated to 4000 characters for storage).
     * The FeedbackController automatically truncates longer responses.
     */
    @Column(name = "ai_response", length = 4000)
    private String aiResponse;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
