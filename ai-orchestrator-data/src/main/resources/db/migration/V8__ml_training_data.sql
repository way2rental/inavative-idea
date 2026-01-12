-- =====================================================================
-- ML Training Data Configuration
-- Version 8.0.0 - Training Data Collection for Auto-Learning
-- =====================================================================
-- NO HARDCODING - All training data stored in database
-- Supports auto-learning and continuous model improvement
-- =====================================================================

-- ============================================
-- TRAINING DATA TABLE
-- ============================================
-- Store labeled training data for ML model training
-- Collected from user interactions and admin labeling
CREATE TABLE IF NOT EXISTS ai_training_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_query TEXT NOT NULL,
    session_context TEXT,
    scenario_code VARCHAR(100),      -- Labeled scenario
    entities JSON,                   -- Labeled entities
    params JSON,                     -- Labeled parameters
    predicted_scenario VARCHAR(100), -- What system predicted
    predicted_confidence DECIMAL(3,2),
    user_feedback BOOLEAN,           -- TRUE = correct, FALSE = incorrect, NULL = no feedback
    feedback_notes TEXT,
    labeled_by VARCHAR(100),         -- Admin/user who labeled
    labeled_at TIMESTAMP,
    used_for_training BOOLEAN DEFAULT FALSE,
    training_batch_id VARCHAR(100),  -- Which training batch used this
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario (scenario_code),
    INDEX idx_used_for_training (used_for_training),
    INDEX idx_labeled_at (labeled_at),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE SET NULL
);
