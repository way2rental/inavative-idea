-- V2: Dynamic Executor Architecture
-- Adds execution_type and config columns to enable pluggable, config-driven execution

-- Add new columns to ai_scenarios for dynamic execution
ALTER TABLE ai_scenarios
    ADD COLUMN execution_type VARCHAR(50) DEFAULT 'DB_QUERY' AFTER description,
    ADD COLUMN http_method VARCHAR(10) DEFAULT NULL AFTER execution_type,
    ADD COLUMN http_url VARCHAR(500) DEFAULT NULL AFTER http_method,
    ADD COLUMN http_headers JSON DEFAULT NULL AFTER http_url,
    ADD COLUMN sql_query TEXT DEFAULT NULL AFTER http_headers,
    ADD COLUMN request_mapping JSON DEFAULT NULL AFTER sql_query,
    ADD COLUMN response_mapping JSON DEFAULT NULL AFTER request_mapping,
    ADD COLUMN timeout_ms INT DEFAULT 5000 AFTER response_mapping,
    ADD COLUMN prompt_version INT DEFAULT 1 AFTER timeout_ms,
    ADD COLUMN prompt_history JSON DEFAULT NULL AFTER prompt_version;

-- URL Whitelist for HTTP calls (security enforcement)
CREATE TABLE IF NOT EXISTS http_url_whitelist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    url_pattern VARCHAR(500) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Scenario Test Results (sandbox tester)
CREATE TABLE IF NOT EXISTS scenario_test_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    test_id VARCHAR(100) UNIQUE NOT NULL,
    scenario_code VARCHAR(100) NOT NULL,
    test_params JSON NOT NULL,
    executor_type VARCHAR(50),
    request_built JSON,
    response_result JSON,
    success BOOLEAN,
    error_message TEXT,
    execution_time_ms BIGINT,
    dry_run BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scenario_code (scenario_code),
    INDEX idx_test_id (test_id)
);

-- Update existing scenarios to use dynamic execution
UPDATE ai_scenarios SET 
    execution_type = 'HTTP_CALL',
    http_method = 'GET',
    http_url = '${business-data.base-url}/api/transactions/{txnId}',
    request_mapping = '{"txnId": "$.params.txnId"}',
    response_mapping = '{"transactionId": "$.txn_id", "status": "$.status", "amount": "$.amount", "date": "$.txn_date"}',
    timeout_ms = 5000
WHERE scenario_code = 'TXN_STATUS';

UPDATE ai_scenarios SET 
    execution_type = 'HTTP_CALL',
    http_method = 'GET',
    http_url = '${business-data.base-url}/api/files/{fileName}/status',
    request_mapping = '{"fileName": "$.params.fileName"}',
    response_mapping = '{"fileName": "$.file_name", "status": "$.status", "processedAt": "$.processed_at", "recordCount": "$.record_count"}',
    timeout_ms = 5000
WHERE scenario_code = 'FILE_STATUS';

UPDATE ai_scenarios SET 
    execution_type = 'DB_QUERY',
    sql_query = 'SELECT account_id, account_holder, balance, currency, last_updated FROM accounts WHERE account_id = :accountId',
    request_mapping = '{"accountId": "$.params.accountId"}',
    response_mapping = '{"accountId": "$.account_id", "accountHolder": "$.account_holder", "balance": "$.balance", "currency": "$.currency", "lastUpdated": "$.last_updated"}',
    timeout_ms = 5000
WHERE scenario_code = 'ACCOUNT_SUMMARY';

-- Insert default URL whitelist patterns
INSERT INTO http_url_whitelist (url_pattern, description, active)
VALUES 
    ('http://localhost:*/**', 'Local development services', true),
    ('http://internal-api.*/**', 'Internal API services', true),
    ('http://business-data-service/**', 'Business data service', true),
    ('https://internal.*/**', 'Internal HTTPS services', true)
ON DUPLICATE KEY UPDATE description = VALUES(description);
