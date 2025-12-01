-- V5: Enterprise AI Dynamic Configuration
-- Adds tables for fully dynamic AI orchestration: prompts, intents, follow-ups, policies, and response mappings
-- Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md and ADMIN_PANEL_UI_UX_SPEC.md

-- ===================== RESPONSE MAPPINGS =====================
-- JSON Path-based response mapping per SPEC Section 4.1
CREATE TABLE IF NOT EXISTS ai_response_mappings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) DEFAULT 'DB_QUERY',
    source_field VARCHAR(255),
    target_field VARCHAR(255) NOT NULL,
    json_path VARCHAR(255) NOT NULL,
    masking_type VARCHAR(50) DEFAULT 'NONE',
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    INDEX idx_mapping_scenario (scenario_code),
    INDEX idx_mapping_active (active)
);

-- ===================== PROMPT TEMPLATES =====================
-- Dynamic prompt management with versioning
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_key VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) DEFAULT 'USER',
    system_prompt TEXT,
    user_template TEXT,
    response_format VARCHAR(20) DEFAULT 'TEXT',
    temperature DOUBLE DEFAULT 0.7,
    max_tokens INT DEFAULT 1024,
    version INT DEFAULT 1,
    version_history JSON,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    INDEX idx_prompt_category (category),
    INDEX idx_prompt_enabled (enabled)
);

-- ===================== INTENT CONFIGURATIONS =====================
-- Dynamic intent detection without hardcoding
CREATE TABLE IF NOT EXISTS ai_intents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    intent_key VARCHAR(100) NOT NULL UNIQUE,
    intent_name VARCHAR(200),
    description VARCHAR(500),
    training_phrases JSON,
    confidence_threshold DOUBLE DEFAULT 0.72,
    followup_group VARCHAR(100),
    scenario_code VARCHAR(100),
    category VARCHAR(100),
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intent_scenario (scenario_code),
    INDEX idx_intent_category (category),
    INDEX idx_intent_active (active)
);

-- ===================== FOLLOW-UP GROUPS =====================
-- Dynamic follow-up question management
CREATE TABLE IF NOT EXISTS ai_followup_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_key VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    scenario_codes JSON,
    questions JSON,
    question_order JSON,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    INDEX idx_followup_active (active)
);

-- ===================== POLICY RULES =====================
-- Runtime policy evaluation for access control
CREATE TABLE IF NOT EXISTS ai_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    policy_key VARCHAR(100) NOT NULL UNIQUE,
    policy_name VARCHAR(200),
    description VARCHAR(500),
    rule_expression TEXT,
    on_fail VARCHAR(20) DEFAULT 'BLOCK',
    failure_message VARCHAR(500),
    applicable_scenarios JSON,
    applicable_roles JSON,
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    INDEX idx_policy_active (active),
    INDEX idx_policy_priority (priority)
);

-- ===================== ADD ALLOWED_METHODS TO URL WHITELIST =====================
ALTER TABLE http_url_whitelist
    ADD COLUMN IF NOT EXISTS allowed_methods VARCHAR(100) DEFAULT 'GET' AFTER description,
    ADD COLUMN IF NOT EXISTS added_by VARCHAR(100) AFTER allowed_methods;

-- ===================== INSERT DEFAULT PROMPTS =====================
INSERT INTO ai_prompt_templates (prompt_key, category, system_prompt, user_template, response_format) VALUES
('INTENT_DETECT', 'SYSTEM', 
 'You are an intent detection assistant for a banking application. Analyze user queries and extract intent and parameters.',
 'Given the user message: "{{userInput}}"

Determine the intent from these options:
- TXN_STATUS: Check transaction status
- FILE_STATUS: Check file processing status
- ACCOUNT_SUMMARY: Get account summary
- BALANCE_CHECK: Check account balance
- UNKNOWN: Cannot determine

Extract any parameters and respond in JSON format:
{
  "scenario": "<intent>",
  "confidence": <0.0-1.0>,
  "params": {"key": "value"},
  "missingParams": ["param1", "param2"],
  "reasoning": "<explanation>"
}',
 'JSON'),

('RESPONSE_FORMAT', 'SYSTEM',
 'You are a helpful banking assistant. Format data responses in a clear, conversational manner.',
 'The user asked: "{{userQuery}}"

Based on the data: {{dataJson}}

Provide a friendly, clear response that answers their question. Use bullet points for multiple items.',
 'TEXT'),

('FOLLOWUP_GENERATE', 'SYSTEM',
 'Generate natural follow-up questions to collect missing information from users.',
 'The user wants to perform: {{scenarioCode}}
Missing parameters: {{missingParams}}

Generate a single, friendly follow-up question to collect this information.',
 'TEXT')
ON DUPLICATE KEY UPDATE category = VALUES(category);

-- ===================== INSERT DEFAULT INTENTS =====================
INSERT INTO ai_intents (intent_key, intent_name, description, training_phrases, scenario_code, category) VALUES
('TXN_STATUS', 'Transaction Status', 'Check transaction status', 
 '["check transaction", "transaction status", "payment status", "where is my money", "txn status"]',
 'TXN_STATUS', 'TRANSACTIONS'),

('FILE_STATUS', 'File Status', 'Check file processing status',
 '["file status", "batch status", "processing status", "upload status", "file processing"]',
 'FILE_STATUS', 'FILES'),

('ACCOUNT_SUMMARY', 'Account Summary', 'Get account summary',
 '["account summary", "my account", "account details", "account info", "show account"]',
 'ACCOUNT_SUMMARY', 'ACCOUNTS'),

('BALANCE_CHECK', 'Balance Check', 'Check account balance',
 '["check balance", "my balance", "account balance", "how much money", "available balance"]',
 'BALANCE_CHECK', 'ACCOUNTS')
ON DUPLICATE KEY UPDATE intent_name = VALUES(intent_name);

-- ===================== INSERT DEFAULT FOLLOW-UP GROUPS =====================
INSERT INTO ai_followup_groups (group_key, description, scenario_codes, questions) VALUES
('TXN_FOLLOWUPS', 'Transaction-related follow-up questions', 
 '["TXN_STATUS"]',
 '[{"key": "txnId", "question": "Please provide your Transaction ID", "type": "string", "required": true},
   {"key": "date", "question": "Transaction date (optional)", "type": "date", "required": false}]'),

('FILE_FOLLOWUPS', 'File-related follow-up questions',
 '["FILE_STATUS"]',
 '[{"key": "fileName", "question": "Please provide the file name", "type": "string", "required": true},
   {"key": "uploadDate", "question": "Upload date (optional)", "type": "date", "required": false}]'),

('ACCOUNT_FOLLOWUPS', 'Account-related follow-up questions',
 '["ACCOUNT_SUMMARY", "BALANCE_CHECK"]',
 '[{"key": "accountId", "question": "Please provide your Account ID", "type": "string", "required": true}]')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ===================== INSERT DEFAULT POLICIES =====================
INSERT INTO ai_policies (policy_key, policy_name, rule_expression, on_fail, failure_message) VALUES
('CROSS_USER_PROTECTION', 'Cross User Data Protection',
 'request.userId == resource.ownerId',
 'BLOCK', 'Unauthorized access attempt detected. You can only access your own data.'),

('ADMIN_ONLY_SCENARIOS', 'Admin Only Scenarios',
 'request.role == "ADMIN"',
 'BLOCK', 'This action requires administrator privileges.'),

('RATE_LIMIT_PROTECTION', 'Rate Limit Protection',
 'request.requestCount < 100',
 'BLOCK', 'Rate limit exceeded. Please try again later.')
ON DUPLICATE KEY UPDATE policy_name = VALUES(policy_name);

-- ===================== INSERT DEFAULT RESPONSE MAPPINGS =====================
-- Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md Section 5 Example
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
-- TXN_STATUS mappings
('TXN_STATUS', 'DB_QUERY', 'txn_id', 'txnId', '$.txn_id', 'NONE', 1),
('TXN_STATUS', 'DB_QUERY', 'status_code', 'status', '$.status_code', 'NONE', 2),
('TXN_STATUS', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 3),
('TXN_STATUS', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 4),
('TXN_STATUS', 'DB_QUERY', 'created_at', 'timestamp', '$.created_at', 'NONE', 5),

-- FILE_STATUS mappings
('FILE_STATUS', 'HTTP_CALL', 'file_name', 'fileName', '$.file_name', 'NONE', 1),
('FILE_STATUS', 'HTTP_CALL', 'status', 'status', '$.status', 'NONE', 2),
('FILE_STATUS', 'HTTP_CALL', 'processed_at', 'processedAt', '$.processed_at', 'NONE', 3),
('FILE_STATUS', 'HTTP_CALL', 'record_count', 'recordCount', '$.record_count', 'NONE', 4),

-- ACCOUNT_SUMMARY mappings
('ACCOUNT_SUMMARY', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 1),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'account_holder', 'accountHolder', '$.account_holder', 'NONE', 2),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'balance', 'balance', '$.balance', 'NONE', 3),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 4),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'last_updated', 'lastUpdated', '$.last_updated', 'NONE', 5),

-- BALANCE_CHECK mappings
('BALANCE_CHECK', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 1),
('BALANCE_CHECK', 'DB_QUERY', 'balance', 'availableBalance', '$.balance', 'NONE', 2),
('BALANCE_CHECK', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 3)
ON DUPLICATE KEY UPDATE target_field = VALUES(target_field);
