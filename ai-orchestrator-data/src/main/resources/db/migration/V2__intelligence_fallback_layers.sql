-- =====================================================================
-- Intelligence Fallback Layers Configuration
-- Version 2.0.0 - Fully DB-Driven Fallback Architecture
-- =====================================================================
-- NO HARDCODING - All fallback layer configuration from database
-- Fully configurable and manageable from Admin Panel
-- =====================================================================

-- ============================================
-- 1. FALLBACK LAYER CONFIGURATION
-- ============================================
CREATE TABLE IF NOT EXISTS ai_fallback_layers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    layer_code VARCHAR(50) NOT NULL UNIQUE,
    layer_name VARCHAR(255) NOT NULL,
    layer_type VARCHAR(50) NOT NULL, -- ML, EMBEDDING, RULES, KEYWORDS, CONVERSATIONAL
    priority INT NOT NULL,            -- Execution order (1=highest, 5=lowest)
    enabled BOOLEAN DEFAULT TRUE,
    confidence_threshold DECIMAL(3,2) DEFAULT 0.85, -- Minimum confidence to accept
    max_uncertainty DECIMAL(3,2) DEFAULT 0.10,      -- Maximum uncertainty allowed
    timeout_ms INT DEFAULT 5000,
    description TEXT,
    config_json JSON,                 -- Layer-specific configuration
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_layer_type (layer_type),
    INDEX idx_priority_enabled (priority, enabled),
    INDEX idx_layer_code (layer_code)
);

-- ============================================
-- 2. ML MODEL CONFIGURATION
-- ============================================
CREATE TABLE IF NOT EXISTS ai_ml_models (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_type VARCHAR(50) NOT NULL,  -- INTENT, NER, COREFERENCE, DISAMBIGUATION, FORMATTING
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    model_path VARCHAR(500) NOT NULL,  -- Path to ONNX model file
    model_size_bytes BIGINT,
    provider VARCHAR(50),              -- ONNX, HUGGINGFACE, DJL
    format VARCHAR(20),                -- ONNX, TORCH, TENSORFLOW
    quantization VARCHAR(20),          -- FP32, FP16, INT8
    accuracy_metrics JSON,             -- Test accuracy, F1 score, etc.
    training_date DATE,
    training_examples_count INT,
    active BOOLEAN DEFAULT TRUE,
    enabled BOOLEAN DEFAULT TRUE,
    fallback_layer_id BIGINT,          -- Which layer uses this model
    config_json JSON,                  -- Model-specific configuration
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_type_version (model_type, model_version),
    INDEX idx_model_type_active (model_type, active),
    INDEX idx_fallback_layer (fallback_layer_id),
    FOREIGN KEY (fallback_layer_id) REFERENCES ai_fallback_layers(id) ON DELETE SET NULL
);

-- ============================================
-- 3. SCENARIO EMBEDDINGS (Pre-computed)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_scenario_embeddings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) NOT NULL UNIQUE,
    embedding_vector JSON NOT NULL,    -- 384-dim array from all-MiniLM-L6-v2
    embedding_model VARCHAR(100),      -- Model used to generate embeddings
    trigger_phrases JSON,              -- Alternative keywords/phrases
    training_examples JSON,            -- Example queries for this scenario
    semantic_tags JSON,                -- Domain tags for context
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario (scenario_code),
    INDEX idx_active (active),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE CASCADE
);

-- ============================================
-- 4. ENTITY PATTERNS (DB-Driven NER)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_entity_patterns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type VARCHAR(50) NOT NULL,  -- ACCOUNT_ID, TRANSACTION_ID, AMOUNT, DATE, etc.
    pattern_type VARCHAR(20) NOT NULL, -- REGEX, NER_MODEL, CONTEXT_BASED, VALIDATION
    pattern_definition TEXT NOT NULL,  -- Regex pattern or model path
    display_name VARCHAR(255),         -- Human-readable name
    description TEXT,
    validation_rule TEXT,              -- Additional validation logic (JSON)
    examples JSON,                     -- Example matches
    confidence_boost DECIMAL(3,2) DEFAULT 0.0,
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    config_json JSON,                  -- Pattern-specific configuration
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entity_type (entity_type),
    INDEX idx_pattern_type (pattern_type),
    INDEX idx_active_priority (active, priority DESC)
);

-- ============================================
-- 5. RULE ENGINE RULES (DB-Driven)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_rule_engine_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_code VARCHAR(100) NOT NULL UNIQUE,
    rule_name VARCHAR(255) NOT NULL,
    scenario_code VARCHAR(100),        -- Target scenario (null = general rule)
    condition_type VARCHAR(50),        -- KEYWORD, PATTERN, REGEX, EMBEDDING, HYBRID
    conditions JSON NOT NULL,          -- Rule conditions (DB-driven)
    confidence DECIMAL(3,2) DEFAULT 0.75, -- Rule confidence score
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    description TEXT,
    examples JSON,                     -- Example matches
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario_code (scenario_code),
    INDEX idx_condition_type (condition_type),
    INDEX idx_active_priority (active, priority DESC),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE SET NULL
);

-- ============================================
-- 6. KEYWORD MATCHING PATTERNS
-- ============================================
CREATE TABLE IF NOT EXISTS ai_keyword_patterns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    weight DECIMAL(3,2) DEFAULT 1.0,   -- Keyword weight for scoring
    synonyms JSON,                      -- Alternative keywords
    context_hints JSON,                 -- Context that boosts this keyword
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scenario_keyword (scenario_code, keyword),
    INDEX idx_scenario_active (scenario_code, active),
    INDEX idx_keyword (keyword),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE CASCADE
);

-- ============================================
-- 7. RESPONSE TEMPLATES (Freemarker - DB-Driven)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_response_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) NOT NULL,
    response_type VARCHAR(20) NOT NULL, -- TEXT, TABLE, KV, MIXED, FOLLOW_UP, ERROR
    template_content TEXT NOT NULL,     -- Freemarker template
    template_variables JSON,            -- Available variables documentation
    conditions JSON,                    -- When to use this template (DB-driven)
    priority INT DEFAULT 0,
    template_version INT DEFAULT 1,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario_type (scenario_code, response_type),
    INDEX idx_active_priority (active, priority DESC),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE CASCADE
);

-- ============================================
-- 8. CONTEXT MEMORY (Session-based)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_context_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,  -- ACCOUNT, TRANSACTION, etc. (DB-driven)
    entity_value VARCHAR(500),         -- Actual value (accountId, etc.)
    entity_metadata JSON,              -- Additional context
    mentioned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_referenced TIMESTAMP,
    reference_count INT DEFAULT 1,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id),
    INDEX idx_entity (entity_type, entity_value),
    INDEX idx_last_referenced (last_referenced)
);

-- ============================================
-- 9. FOLLOW-UP TEMPLATES (DB-Driven)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_followup_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    template_question TEXT NOT NULL,   -- Freemarker template
    context_hints JSON,                -- When to use this question (DB-driven)
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenario_param (scenario_code, param_name),
    INDEX idx_active_priority (active, priority DESC),
    FOREIGN KEY (scenario_code) REFERENCES ai_scenarios(scenario_code) ON DELETE CASCADE
);

-- ============================================
-- 10. CONVERSATIONAL RESPONSES (DB-Driven)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_conversational_responses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    intent_type VARCHAR(50) NOT NULL,  -- GREETING, THANKS, GOODBYE, UNKNOWN, HELP
    user_query_patterns JSON,          -- Patterns that trigger this (DB-driven)
    response_template TEXT NOT NULL,   -- Freemarker template
    suggested_actions JSON,            -- Follow-up suggestions (DB-driven)
    context_aware BOOLEAN DEFAULT FALSE,
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intent_type (intent_type),
    INDEX idx_active_priority (active, priority DESC)
);

-- ============================================
-- 11. FAILURE LOGGING & EDGE CASE TRACKING
-- ============================================
CREATE TABLE IF NOT EXISTS ai_failure_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    query TEXT NOT NULL,
    failure_type VARCHAR(50) NOT NULL, -- EDGE_CASE, MODEL_ERROR, TIMEOUT, FALLBACK, etc.
    edge_case_type VARCHAR(50),        -- AMBIGUOUS_INTENT, MISSING_ENTITIES, etc.
    layer_code VARCHAR(50),            -- Which layer failed
    ml_prediction JSON,                -- What ML model predicted
    embedding_prediction JSON,         -- What embeddings predicted
    rule_prediction JSON,              -- What rules predicted
    keyword_prediction JSON,           -- What keywords predicted
    actual_intent VARCHAR(100),        -- If manually corrected
    user_feedback VARCHAR(20),         -- CORRECT, INCORRECT, UNCLEAR
    resolution_method VARCHAR(50),     -- How it was resolved
    context_snapshot JSON,             -- Full context at failure
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    INDEX idx_failure_type (failure_type),
    INDEX idx_edge_case_type (edge_case_type),
    INDEX idx_layer_code (layer_code),
    INDEX idx_unresolved (resolved_at),
    INDEX idx_user (user_id),
    INDEX idx_created_at (created_at)
);

-- ============================================
-- 12. TRAINING DATA QUEUE (Active Learning)
-- ============================================
CREATE TABLE IF NOT EXISTS ai_training_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    query TEXT NOT NULL,
    predicted_scenario VARCHAR(100),
    predicted_confidence DECIMAL(3,2),
    fallback_layer_code VARCHAR(50),   -- Which layer made prediction
    actual_scenario VARCHAR(100),      -- After manual review
    actual_params JSON,                -- After manual review
    labeled_by VARCHAR(100),           -- User/admin who labeled
    labeled_at TIMESTAMP,
    priority INT DEFAULT 0,            -- Higher = more important
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, LABELED, USED, SKIPPED
    failure_log_id BIGINT,             -- Link to failure log
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_priority (status, priority DESC),
    INDEX idx_labeled_at (labeled_at),
    INDEX idx_failure_log (failure_log_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (failure_log_id) REFERENCES ai_failure_logs(id) ON DELETE SET NULL
);

-- ============================================
-- 13. MODEL PERFORMANCE METRICS
-- ============================================
CREATE TABLE IF NOT EXISTS ai_model_metrics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_type VARCHAR(50) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    metric_date DATE NOT NULL,
    total_predictions INT DEFAULT 0,
    correct_predictions INT DEFAULT 0,
    incorrect_predictions INT DEFAULT 0,
    avg_confidence DECIMAL(5,4),
    avg_confidence_correct DECIMAL(5,4),
    avg_confidence_incorrect DECIMAL(5,4),
    edge_case_count INT DEFAULT 0,
    fallback_triggered_count INT DEFAULT 0,
    accuracy DECIMAL(5,4),             -- Calculated: correct/total
    precision_per_class JSON,          -- Per-scenario precision
    recall_per_class JSON,             -- Per-scenario recall
    f1_score_per_class JSON,           -- Per-scenario F1
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_date (model_type, model_version, metric_date),
    INDEX idx_date (metric_date),
    INDEX idx_model_type (model_type)
);

-- ============================================
-- DEFAULT DATA: Fallback Layers Configuration
-- ============================================
INSERT INTO ai_fallback_layers (layer_code, layer_name, layer_type, priority, enabled, confidence_threshold, max_uncertainty, timeout_ms, description, config_json) VALUES
('ML_INTENT_CLASSIFIER', 'ML Intent Classifier', 'ML', 1, FALSE, 0.85, 0.10, 2000, 'Transformer-based intent classification model (disabled - requires model files)', 
 '{"model_type": "INTENT", "provider": "ONNX", "quantization": "INT8"}'),
('EMBEDDING_SIMILARITY', 'Embedding Similarity Matcher', 'EMBEDDING', 1, TRUE, 0.70, 0.15, 500, 'Vector similarity search using pre-computed embeddings (ML-based semantic matching)',
 '{"embedding_model": "all-MiniLM-L6-v2", "top_k": 5, "similarity_threshold": 0.75}'),
('RULE_ENGINE', 'Rule Engine', 'RULES', 3, FALSE, 0.75, 0.20, 1000, 'Deterministic rule-based matching (disabled - requires rules)',
 '{"rule_priority": "HIGH_TO_LOW", "match_type": "FIRST_BEST"}'),
('KEYWORD_MATCHER', 'Keyword Matcher', 'KEYWORDS', 4, FALSE, 0.50, 0.30, 200, 'Simple keyword-based matching with weighted scoring (disabled - requires keywords)',
 '{"case_sensitive": false, "fuzzy_match": true, "synonym_expansion": true}'),
('SCENARIO_TRIGGER_MATCHER', 'Scenario Trigger Matcher', 'SCENARIO', 5, TRUE, 0.50, 0.30, 500, 'Uses trigger phrases from ai_scenarios table directly (fallback only)',
 '{"use_trigger_phrases": true, "use_example_queries": true}'),
('CONVERSATIONAL_HANDLER', 'Conversational Handler', 'CONVERSATIONAL', 5, TRUE, 0.10, 1.0, 100, 'Catch-all conversational responses',
 '{"suggest_common_scenarios": true, "context_aware": true}');

-- ============================================
-- DEFAULT DATA: Conversational Responses
-- ============================================
INSERT INTO ai_conversational_responses (intent_type, user_query_patterns, response_template, suggested_actions, context_aware, priority, active) VALUES
('GREETING', '["hi", "hello", "hey", "good morning", "good afternoon", "good evening", "namaste", "namaskar"]',
 'Hello! I''m {{assistantName}}, your {{orgName}} assistant. How can I help you today?', 
 '["Check my balance", "View transactions", "Get account summary", "Help with transfers"]', 
 TRUE, 1, TRUE),
('THANKS', '["thanks", "thank you", "thankyou", "thanks a lot", "appreciate it"]',
 'You''re welcome! Is there anything else I can help you with?',
 '["View more details", "Check another account", "See recent activity"]',
 TRUE, 1, TRUE),
('GOODBYE', '["bye", "goodbye", "see you", "tata", "exit", "quit"]',
 'Thank you for using {{orgName}}. Have a great day! Feel free to come back anytime.',
 '[]',
 TRUE, 1, TRUE),
('UNKNOWN', '[]',
 'I''m not sure I understand. Could you please rephrase your question? Here are some things I can help with:',
 '["Check account balance", "View transaction history", "Account summary", "Get help"]',
 TRUE, 1, TRUE),
('HELP', '["help", "what can you do", "how does this work", "commands", "options"]',
 'I can help you with various services. Here are some common tasks:',
 '["Account balance", "Transaction history", "Account summary", "Fund transfers"]',
 FALSE, 1, TRUE);
