-- AI Orchestrator Database Schema

-- Scenario Registry
CREATE TABLE IF NOT EXISTS ai_scenarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255),
    executor_bean VARCHAR(255),
    security_level VARCHAR(50) DEFAULT 'AUTH',
    required_params JSON,
    optional_params JSON,
    llm_prompt_template TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Role-Scenario Mapping
CREATE TABLE IF NOT EXISTS role_scenario_map (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(100) NOT NULL,
    scenario_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_scenario (role_name, scenario_code)
);

-- Chat Sessions
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- Chat Messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(100),
    user_id VARCHAR(100),
    scenario_code VARCHAR(100),
    request_time TIMESTAMP,
    response_time TIMESTAMP,
    success BOOLEAN,
    error_message TEXT,
    raw_intent_json JSON,
    raw_result_json JSON,
    INDEX idx_user_id (user_id),
    INDEX idx_scenario_code (scenario_code),
    INDEX idx_request_time (request_time)
);

-- Insert default scenarios
INSERT INTO ai_scenarios (scenario_code, description, executor_bean, security_level, required_params, optional_params, active)
VALUES 
    ('TXN_STATUS', 'Check the status of a transaction by its transaction ID', 'txnStatusExecutor', 'AUTH', '["txnId"]', '["date", "channel"]', true),
    ('FILE_STATUS', 'Check file processing status by fileName', 'fileStatusExecutor', 'AUTH', '["fileName"]', '["date"]', true),
    ('ACCOUNT_SUMMARY', 'Show account summary', 'accountSummaryExecutor', 'AUTH', '["accountId"]', '[]', true)
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Insert default role mappings
INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES 
    ('USER', 'TXN_STATUS'),
    ('USER', 'ACCOUNT_SUMMARY'),
    ('ADMIN', 'TXN_STATUS'),
    ('ADMIN', 'ACCOUNT_SUMMARY'),
    ('ADMIN', 'FILE_STATUS'),
    ('OPERATOR', 'FILE_STATUS')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);
