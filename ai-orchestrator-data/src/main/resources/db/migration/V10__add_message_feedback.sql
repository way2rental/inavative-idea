-- V10__add_message_feedback.sql
-- Create message_feedback table for storing user reactions to AI responses
-- Used for model improvement and admin monitoring

CREATE TABLE IF NOT EXISTS message_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100),
    message_id BIGINT,
    user_id VARCHAR(100),
    feedback_type VARCHAR(20) NOT NULL,
    comment VARCHAR(1000),
    scenario_code VARCHAR(100),
    user_query VARCHAR(2000),
    ai_response VARCHAR(4000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_feedback_session (session_id),
    INDEX idx_feedback_user (user_id),
    INDEX idx_feedback_type (feedback_type),
    INDEX idx_feedback_scenario (scenario_code),
    INDEX idx_feedback_created (created_at)
);
