-- =====================================================================
-- AI Orchestrator - Complete Database Schema
-- Version 1.0.0 - Fresh Start for SaaS Deployment
-- =====================================================================
-- This migration creates all tables needed for the AI Orchestrator.
-- NO HARDCODED VALUES - all configuration comes from database.
-- =====================================================================

-- =====================================================================
-- CORE TABLES
-- =====================================================================

-- System Configuration (replaces all hardcoded values)
CREATE TABLE IF NOT EXISTS ai_system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    json_value JSON,
    category VARCHAR(50),
    description VARCHAR(500),
    default_value VARCHAR(500),
    value_type VARCHAR(20) DEFAULT 'STRING',
    editable BOOLEAN DEFAULT TRUE,
    visible BOOLEAN DEFAULT TRUE,
    tenant_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- AI Scenarios (main entity - all AI capabilities)
CREATE TABLE IF NOT EXISTS ai_scenarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_code VARCHAR(100) NOT NULL UNIQUE,
    scenario_name VARCHAR(255),
    description VARCHAR(500),
    execution_type VARCHAR(50) DEFAULT 'DB_QUERY',
    http_method VARCHAR(10),
    http_url VARCHAR(500),
    http_headers TEXT,
    sql_query TEXT,
    request_mapping JSON,
    response_mapping JSON,
    timeout_ms INT DEFAULT 5000,
    required_params JSON,
    llm_prompt_template TEXT,
    active BOOLEAN DEFAULT TRUE,
    -- AI Intent Detection fields (NO HARDCODING)
    trigger_phrases JSON,
    example_queries JSON,
    category VARCHAR(100),
    display_order INT DEFAULT 0,
    icon VARCHAR(50),
    -- Multi-filter engine fields
    filter_definitions JSON,
    security_filters JSON,
    max_results INT DEFAULT 100,
    default_sort VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Intent Configurations (training phrases for AI)
CREATE TABLE IF NOT EXISTS ai_intent_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intent_code VARCHAR(100) NOT NULL UNIQUE,
    scenario_code VARCHAR(100) NOT NULL,
    training_phrases JSON,
    confidence_threshold DECIMAL(3,2) DEFAULT 0.85,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Prompt Templates (versioned prompts for AI)
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_key VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50),
    system_prompt TEXT,
    user_template TEXT,
    response_format VARCHAR(50),
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INT DEFAULT 1024,
    version INT DEFAULT 1,
    enabled BOOLEAN DEFAULT TRUE,
    version_history JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Policy Rules (business rules for AI behavior)
CREATE TABLE IF NOT EXISTS ai_policy_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_key VARCHAR(100) NOT NULL UNIQUE,
    policy_name VARCHAR(255),
    description TEXT,
    rule_expression TEXT,
    on_fail VARCHAR(50) DEFAULT 'BLOCK',
    failure_message TEXT,
    applicable_scenarios JSON,
    applicable_roles JSON,
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Follow-up Question Groups
CREATE TABLE IF NOT EXISTS ai_follow_up_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_code VARCHAR(100) NOT NULL UNIQUE,
    group_name VARCHAR(255),
    scenario_codes JSON,
    questions JSON,
    question_order JSON,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Response Mappings
CREATE TABLE IF NOT EXISTS ai_response_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_code VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    source_path VARCHAR(255),
    display_name VARCHAR(255),
    format_type VARCHAR(50),
    format_pattern VARCHAR(255),
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_scenario_field (scenario_code, field_name)
);

-- HTTP URL Whitelist (security)
CREATE TABLE IF NOT EXISTS ai_http_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_pattern VARCHAR(500) NOT NULL,
    method VARCHAR(10) DEFAULT 'GET',
    description VARCHAR(500),
    added_by VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Role-Scenario Mapping (RBAC)
CREATE TABLE IF NOT EXISTS ai_role_scenario_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL,
    scenario_code VARCHAR(100) NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_scenario (role_name, scenario_code)
);

-- =====================================================================
-- SESSION & AUDIT TABLES
-- =====================================================================

-- Chat Sessions
CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    context TEXT,
    last_used_params JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- Chat Messages
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    scenario_code VARCHAR(100),
    params JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
);

-- Message Feedback
CREATE TABLE IF NOT EXISTS ai_message_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    rating INT,
    feedback_type VARCHAR(50),
    feedback_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    scenario_code VARCHAR(100),
    raw_intent_json JSON,
    raw_result_json JSON,
    execution_time_ms BIGINT,
    success BOOLEAN DEFAULT TRUE,
    error_details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_scenario (scenario_code),
    INDEX idx_created (created_at)
);

-- Scenario Test Results
CREATE TABLE IF NOT EXISTS ai_scenario_test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_code VARCHAR(100) NOT NULL,
    test_params JSON NOT NULL,
    request_built JSON,
    response_result JSON,
    execution_time_ms BIGINT,
    success BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    tested_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- SAMPLE DATA - SYSTEM CONFIG
-- =====================================================================

INSERT INTO ai_system_config (config_key, config_value, category, description, default_value, value_type) VALUES
-- Branding (customize per bank)
('ORG_NAME', 'Enterprise Bank', 'BRANDING', 'Full organization name', 'Enterprise Bank', 'STRING'),
('ORG_SHORT_NAME', 'EBank', 'BRANDING', 'Short organization name', 'EBank', 'STRING'),
('AI_ASSISTANT_NAME', 'AHA', 'BRANDING', 'AI assistant name', 'AHA', 'STRING'),
('AI_ASSISTANT_FULL_NAME', 'AI Helpdesk Assistant', 'BRANDING', 'AI assistant full name', 'AI Helpdesk Assistant', 'STRING'),
('WELCOME_MESSAGE', 'Hello! I''m your AI banking assistant. How can I help you today?', 'BRANDING', 'Welcome message', 'Hello! I''m your AI banking assistant.', 'STRING'),
('GOODBYE_MESSAGE', 'Thank you for banking with us. Have a great day!', 'BRANDING', 'Goodbye message', 'Thank you!', 'STRING'),
-- Performance
('DEFAULT_TIMEOUT_MS', '5000', 'PERFORMANCE', 'Default request timeout in milliseconds', '5000', 'LONG'),
('MAX_TIMEOUT_MS', '30000', 'PERFORMANCE', 'Maximum request timeout in milliseconds', '30000', 'LONG'),
('CACHE_TTL_SECONDS', '300', 'PERFORMANCE', 'Cache TTL in seconds', '300', 'LONG'),
('MAX_RESULT_SIZE', '1000', 'PERFORMANCE', 'Maximum results per query', '1000', 'LONG'),
('MAX_RESPONSE_SIZE_BYTES', '1048576', 'PERFORMANCE', 'Maximum response size (1MB)', '1048576', 'LONG'),
('MAX_STREAM_DURATION_SECONDS', '120', 'PERFORMANCE', 'Maximum SSE stream duration', '120', 'LONG'),
-- Security
('SESSION_TIMEOUT_SECONDS', '300', 'SECURITY', 'User session timeout', '300', 'LONG'),
('MAX_QUERIES_PER_MINUTE', '60', 'SECURITY', 'Rate limit per user', '60', 'LONG'),
('DEFAULT_CONFIDENCE_THRESHOLD', '0.85', 'SECURITY', 'Minimum confidence for intent detection', '0.85', 'DOUBLE'),
-- Messages
('ERROR_MESSAGE_GENERIC', 'I apologize, but I encountered an issue. Please try again.', 'MESSAGES', 'Generic error message', 'I apologize, but I encountered an issue.', 'STRING'),
('ERROR_MESSAGE_TIMEOUT', 'Your request is taking longer than expected. Please try again.', 'MESSAGES', 'Timeout error message', 'Request timeout.', 'STRING'),
('ERROR_MESSAGE_UNAUTHORIZED', 'You don''t have permission to access this feature.', 'MESSAGES', 'Unauthorized error message', 'Access denied.', 'STRING'),
-- LLM Settings
('LLM_TEMPERATURE', '0.7', 'LLM', 'LLM temperature (0-2)', '0.7', 'DOUBLE'),
('LLM_MAX_TOKENS', '1024', 'LLM', 'Maximum tokens for LLM response', '1024', 'LONG'),
('PROMPT_CACHE_TTL_SECONDS', '300', 'LLM', 'Prompt cache TTL', '300', 'LONG');

-- =====================================================================
-- SAMPLE DATA - 10 TEST AI SCENARIOS (QUERY_EXECUTOR)
-- =====================================================================

INSERT INTO ai_scenarios (
    scenario_code, scenario_name, description, execution_type, sql_query,
    required_params, trigger_phrases, example_queries, category, icon,
    filter_definitions, max_results, default_sort, active
) VALUES 

-- 1. Account Balance
('ACCOUNT_BALANCE', 'Check Account Balance', 
 'View current balance and available funds for a bank account',
 'DB_QUERY',
 'SELECT account_id, account_name, account_type, currency, available_balance, current_balance, blocked_amount, last_updated FROM accounts WHERE account_id = :accountId',
 '["accountId"]',
 '["balance", "check balance", "account balance", "how much money", "available funds", "show balance", "my balance"]',
 '["What is my account balance?", "Show balance for ACC001", "How much money do I have?", "Check my available balance"]',
 'Account', 'wallet',
 '[{"name":"accountId","displayName":"Account ID","description":"Bank account number starting with ACC","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true,"validationPattern":"^ACC[0-9]+$","validationError":"Account ID must start with ACC followed by numbers"}]',
 1, 'last_updated DESC', TRUE),

-- 2. Transaction History
('TRANSACTION_HISTORY', 'Transaction History',
 'View transaction history with filters for date, amount, and type',
 'DB_QUERY',
 'SELECT txn_id, account_id, txn_date, txn_type, amount, currency, description, balance_after, status FROM transactions WHERE 1=1',
 '["accountId"]',
 '["transactions", "transaction history", "recent transactions", "show transactions", "txn history", "my transactions", "payment history"]',
 '["Show my recent transactions", "Transaction history for ACC001", "Show transactions from last week", "List all debit transactions"]',
 'Transaction', 'receipt',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"dateFrom","displayName":"From Date","type":"DATE","dbColumn":"txn_date","operator":">=","mandatory":false},{"name":"dateTo","displayName":"To Date","type":"DATE","dbColumn":"txn_date","operator":"<=","mandatory":false},{"name":"txnType","displayName":"Transaction Type","type":"ENUM","dbColumn":"txn_type","operator":"=","mandatory":false,"enumValues":["CREDIT","DEBIT","TRANSFER"]},{"name":"minAmount","displayName":"Minimum Amount","type":"DECIMAL","dbColumn":"amount","operator":">=","mandatory":false},{"name":"maxAmount","displayName":"Maximum Amount","type":"DECIMAL","dbColumn":"amount","operator":"<=","mandatory":false}]',
 50, 'txn_date DESC', TRUE),

-- 3. Account Summary
('ACCOUNT_SUMMARY', 'Account Summary',
 'Get comprehensive account summary including balance, recent activity, and account details',
 'DB_QUERY',
 'SELECT a.account_id, a.account_name, a.account_type, a.currency, a.available_balance, a.current_balance, a.opening_date, a.branch_code, a.ifsc_code, (SELECT COUNT(*) FROM transactions t WHERE t.account_id = a.account_id AND t.txn_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)) as recent_txn_count FROM accounts a WHERE a.account_id = :accountId',
 '["accountId"]',
 '["account summary", "account details", "account info", "account overview", "my account", "show account"]',
 '["Show my account summary", "Account details for ACC001", "Give me account overview"]',
 'Account', 'file-text',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true}]',
 1, NULL, TRUE),

-- 4. Fund Transfer Status
('FUND_TRANSFER_STATUS', 'Fund Transfer Status',
 'Check the status of a fund transfer using transfer reference number',
 'DB_QUERY',
 'SELECT transfer_id, from_account, to_account, amount, currency, status, initiated_at, completed_at, remarks, failure_reason FROM fund_transfers WHERE transfer_id = :transferId OR from_account = :accountId',
 '[]',
 '["transfer status", "fund transfer", "check transfer", "payment status", "money transfer status"]',
 '["Check transfer status TXF001", "Status of my last transfer", "Did my transfer go through?"]',
 'Payment', 'send',
 '[{"name":"transferId","displayName":"Transfer ID","type":"STRING","dbColumn":"transfer_id","operator":"=","mandatory":false},{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"from_account","operator":"=","mandatory":false}]',
 10, 'initiated_at DESC', TRUE),

-- 5. Bill Payment History
('BILL_PAYMENT_HISTORY', 'Bill Payment History',
 'View history of bill payments including utilities, insurance, and subscriptions',
 'DB_QUERY',
 'SELECT payment_id, biller_name, biller_category, amount, payment_date, status, account_id, reference_number FROM bill_payments WHERE account_id = :accountId',
 '["accountId"]',
 '["bill payments", "bill history", "payment history", "utility payments", "paid bills"]',
 '["Show my bill payments", "Bill payment history for last month", "List all utility payments"]',
 'Payment', 'file-invoice',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"billerCategory","displayName":"Biller Category","type":"ENUM","dbColumn":"biller_category","operator":"=","mandatory":false,"enumValues":["ELECTRICITY","WATER","GAS","TELECOM","INSURANCE","SUBSCRIPTION"]},{"name":"dateFrom","displayName":"From Date","type":"DATE","dbColumn":"payment_date","operator":">=","mandatory":false},{"name":"dateTo","displayName":"To Date","type":"DATE","dbColumn":"payment_date","operator":"<=","mandatory":false}]',
 50, 'payment_date DESC', TRUE),

-- 6. Card Details
('CARD_DETAILS', 'Card Details',
 'View debit and credit card information linked to account',
 'DB_QUERY',
 'SELECT card_id, card_type, card_number_masked, card_holder_name, expiry_date, status, credit_limit, available_credit, linked_account FROM cards WHERE linked_account = :accountId OR card_id = :cardId',
 '[]',
 '["card details", "my cards", "credit card", "debit card", "card info", "show cards"]',
 '["Show my card details", "Credit card information", "What cards are linked to my account?"]',
 'Card', 'credit-card',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"linked_account","operator":"=","mandatory":false},{"name":"cardId","displayName":"Card ID","type":"STRING","dbColumn":"card_id","operator":"=","mandatory":false},{"name":"cardType","displayName":"Card Type","type":"ENUM","dbColumn":"card_type","operator":"=","mandatory":false,"enumValues":["DEBIT","CREDIT","PREPAID"]}]',
 10, 'card_type ASC', TRUE),

-- 7. Loan Status
('LOAN_STATUS', 'Loan Status',
 'Check loan details, EMI schedule, and outstanding balance',
 'DB_QUERY',
 'SELECT loan_id, loan_type, principal_amount, outstanding_balance, interest_rate, emi_amount, next_emi_date, loan_status, disbursement_date, tenure_months, account_id FROM loans WHERE account_id = :accountId OR loan_id = :loanId',
 '[]',
 '["loan status", "my loan", "loan details", "emi details", "outstanding loan", "loan balance"]',
 '["Show my loan status", "What is my outstanding loan balance?", "When is my next EMI due?"]',
 'Loan', 'building-bank',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":false},{"name":"loanId","displayName":"Loan ID","type":"STRING","dbColumn":"loan_id","operator":"=","mandatory":false},{"name":"loanType","displayName":"Loan Type","type":"ENUM","dbColumn":"loan_type","operator":"=","mandatory":false,"enumValues":["HOME","PERSONAL","CAR","EDUCATION","BUSINESS"]}]',
 10, 'next_emi_date ASC', TRUE),

-- 8. Fixed Deposit Details
('FIXED_DEPOSIT_DETAILS', 'Fixed Deposit Details',
 'View fixed deposit accounts, maturity dates, and interest earned',
 'DB_QUERY',
 'SELECT fd_id, account_id, principal_amount, interest_rate, maturity_date, maturity_amount, tenure_days, status, created_date FROM fixed_deposits WHERE account_id = :accountId',
 '["accountId"]',
 '["fixed deposit", "fd details", "my fd", "fd status", "term deposit", "fd maturity"]',
 '["Show my fixed deposits", "When does my FD mature?", "FD interest rate"]',
 'Investment', 'piggy-bank',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"status","displayName":"FD Status","type":"ENUM","dbColumn":"status","operator":"=","mandatory":false,"enumValues":["ACTIVE","MATURED","CLOSED","PREMATURE_CLOSED"]}]',
 20, 'maturity_date ASC', TRUE),

-- 9. Beneficiary List
('BENEFICIARY_LIST', 'Beneficiary List',
 'View saved beneficiaries for fund transfers',
 'DB_QUERY',
 'SELECT beneficiary_id, beneficiary_name, account_number, ifsc_code, bank_name, beneficiary_type, nickname, added_date, verified, account_id FROM beneficiaries WHERE account_id = :accountId',
 '["accountId"]',
 '["beneficiaries", "payee list", "saved accounts", "transfer contacts", "my beneficiaries"]',
 '["Show my beneficiaries", "List saved payees", "Who can I transfer money to?"]',
 'Payment', 'users',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"beneficiaryType","displayName":"Beneficiary Type","type":"ENUM","dbColumn":"beneficiary_type","operator":"=","mandatory":false,"enumValues":["INTERNAL","EXTERNAL","INTERNATIONAL"]}]',
 50, 'beneficiary_name ASC', TRUE),

-- 10. Spending Analysis
('SPENDING_ANALYSIS', 'Spending Analysis',
 'Analyze spending patterns by category and time period',
 'DB_QUERY',
 'SELECT category, SUM(amount) as total_spent, COUNT(*) as transaction_count, AVG(amount) as avg_transaction FROM transactions WHERE account_id = :accountId AND txn_type = ''DEBIT'' AND txn_date >= :dateFrom GROUP BY category ORDER BY total_spent DESC',
 '["accountId", "dateFrom"]',
 '["spending analysis", "where did I spend", "spending breakdown", "expense analysis", "spending pattern", "analyze spending"]',
 '["Analyze my spending", "Where did I spend money last month?", "Spending breakdown by category"]',
 'Analytics', 'chart-pie',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"dateFrom","displayName":"From Date","type":"DATE","dbColumn":"txn_date","operator":">=","mandatory":true},{"name":"dateTo","displayName":"To Date","type":"DATE","dbColumn":"txn_date","operator":"<=","mandatory":false}]',
 20, 'total_spent DESC', TRUE);

-- =====================================================================
-- SAMPLE DATA - INTENT CONFIGS
-- =====================================================================

INSERT INTO ai_intent_configs (intent_code, scenario_code, training_phrases, confidence_threshold, active) VALUES
('INTENT_BALANCE', 'ACCOUNT_BALANCE', '["check balance", "account balance", "how much money", "available balance", "show balance", "my balance", "what is my balance", "balance enquiry"]', 0.85, TRUE),
('INTENT_TRANSACTIONS', 'TRANSACTION_HISTORY', '["show transactions", "transaction history", "recent transactions", "my transactions", "list transactions", "transaction list"]', 0.85, TRUE),
('INTENT_SUMMARY', 'ACCOUNT_SUMMARY', '["account summary", "account details", "account info", "my account", "account overview"]', 0.85, TRUE),
('INTENT_TRANSFER_STATUS', 'FUND_TRANSFER_STATUS', '["transfer status", "payment status", "check transfer", "did transfer go through"]', 0.85, TRUE),
('INTENT_BILLS', 'BILL_PAYMENT_HISTORY', '["bill payments", "utility bills", "paid bills", "bill history"]', 0.85, TRUE),
('INTENT_CARDS', 'CARD_DETAILS', '["card details", "my cards", "credit card", "debit card", "card info"]', 0.85, TRUE),
('INTENT_LOANS', 'LOAN_STATUS', '["loan status", "my loan", "emi details", "loan balance", "outstanding loan"]', 0.85, TRUE),
('INTENT_FD', 'FIXED_DEPOSIT_DETAILS', '["fixed deposit", "fd details", "my fd", "term deposit"]', 0.85, TRUE),
('INTENT_BENEFICIARIES', 'BENEFICIARY_LIST', '["beneficiaries", "payee list", "saved accounts", "transfer contacts"]', 0.85, TRUE),
('INTENT_SPENDING', 'SPENDING_ANALYSIS', '["spending analysis", "expense analysis", "spending pattern", "where did I spend"]', 0.85, TRUE);

-- =====================================================================
-- SAMPLE DATA - RBAC (Role-Scenario Mapping)
-- =====================================================================

INSERT INTO ai_role_scenario_map (role_name, scenario_code) VALUES
-- USER role can access basic queries
('USER', 'ACCOUNT_BALANCE'),
('USER', 'TRANSACTION_HISTORY'),
('USER', 'ACCOUNT_SUMMARY'),
('USER', 'CARD_DETAILS'),
-- PREMIUM role gets additional features
('PREMIUM', 'ACCOUNT_BALANCE'),
('PREMIUM', 'TRANSACTION_HISTORY'),
('PREMIUM', 'ACCOUNT_SUMMARY'),
('PREMIUM', 'CARD_DETAILS'),
('PREMIUM', 'FUND_TRANSFER_STATUS'),
('PREMIUM', 'BILL_PAYMENT_HISTORY'),
('PREMIUM', 'BENEFICIARY_LIST'),
('PREMIUM', 'SPENDING_ANALYSIS'),
-- CORPORATE role gets everything
('CORPORATE', 'ACCOUNT_BALANCE'),
('CORPORATE', 'TRANSACTION_HISTORY'),
('CORPORATE', 'ACCOUNT_SUMMARY'),
('CORPORATE', 'CARD_DETAILS'),
('CORPORATE', 'FUND_TRANSFER_STATUS'),
('CORPORATE', 'BILL_PAYMENT_HISTORY'),
('CORPORATE', 'BENEFICIARY_LIST'),
('CORPORATE', 'SPENDING_ANALYSIS'),
('CORPORATE', 'LOAN_STATUS'),
('CORPORATE', 'FIXED_DEPOSIT_DETAILS'),
-- ADMIN has access to all
('ADMIN', 'ACCOUNT_BALANCE'),
('ADMIN', 'TRANSACTION_HISTORY'),
('ADMIN', 'ACCOUNT_SUMMARY'),
('ADMIN', 'CARD_DETAILS'),
('ADMIN', 'FUND_TRANSFER_STATUS'),
('ADMIN', 'BILL_PAYMENT_HISTORY'),
('ADMIN', 'BENEFICIARY_LIST'),
('ADMIN', 'SPENDING_ANALYSIS'),
('ADMIN', 'LOAN_STATUS'),
('ADMIN', 'FIXED_DEPOSIT_DETAILS');

-- =====================================================================
-- SAMPLE DATA - PROMPT TEMPLATES
-- =====================================================================

INSERT INTO ai_prompt_templates (prompt_key, category, system_prompt, user_template, response_format, temperature, max_tokens, version, enabled) VALUES
('INTENT_DETECTION', 'CORE', 'You are an AI banking assistant. Detect user intent from their message and extract relevant parameters.', 'User said: {{userMessage}}\n\nAvailable scenarios: {{scenarios}}\n\nExtract intent and parameters as JSON.', 'JSON', 0.3, 512, 1, TRUE),
('RESPONSE_FORMATTING', 'CORE', 'You are a friendly banking assistant. Format the data into a clear, helpful response.', 'Format this data for the user:\n{{data}}\n\nUser asked: {{query}}', 'STRUCTURED', 0.7, 1024, 1, TRUE),
('CLARIFICATION', 'CORE', 'You are a helpful assistant. Ask a clarifying question to understand the user better.', 'User intent is unclear. Possible matches: {{options}}\n\nAsk a friendly clarifying question.', 'TEXT', 0.7, 256, 1, TRUE),
('FOLLOW_UP', 'CORE', 'You are asking for missing information in a friendly way.', 'Missing parameters: {{missingParams}}\n\nAsk for this information naturally.', 'TEXT', 0.7, 256, 1, TRUE);

-- =====================================================================
-- SAMPLE BUSINESS DATA TABLES (for testing scenarios)
-- =====================================================================

CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    account_name VARCHAR(255),
    account_type VARCHAR(50),
    currency VARCHAR(10) DEFAULT 'INR',
    available_balance DECIMAL(15,2) DEFAULT 0,
    current_balance DECIMAL(15,2) DEFAULT 0,
    blocked_amount DECIMAL(15,2) DEFAULT 0,
    opening_date DATE,
    branch_code VARCHAR(20),
    ifsc_code VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    txn_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    txn_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    txn_type VARCHAR(20),
    amount DECIMAL(15,2),
    currency VARCHAR(10) DEFAULT 'INR',
    description VARCHAR(500),
    category VARCHAR(100),
    balance_after DECIMAL(15,2),
    status VARCHAR(20) DEFAULT 'SUCCESS',
    reference_number VARCHAR(100),
    INDEX idx_account (account_id),
    INDEX idx_date (txn_date)
);

CREATE TABLE IF NOT EXISTS fund_transfers (
    transfer_id VARCHAR(50) PRIMARY KEY,
    from_account VARCHAR(50) NOT NULL,
    to_account VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2),
    currency VARCHAR(10) DEFAULT 'INR',
    status VARCHAR(20) DEFAULT 'PENDING',
    initiated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    remarks VARCHAR(500),
    failure_reason VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    biller_name VARCHAR(255),
    biller_category VARCHAR(50),
    amount DECIMAL(15,2),
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'SUCCESS',
    reference_number VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS cards (
    card_id VARCHAR(50) PRIMARY KEY,
    linked_account VARCHAR(50) NOT NULL,
    card_type VARCHAR(20),
    card_number_masked VARCHAR(20),
    card_holder_name VARCHAR(255),
    expiry_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    credit_limit DECIMAL(15,2),
    available_credit DECIMAL(15,2)
);

CREATE TABLE IF NOT EXISTS loans (
    loan_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    loan_type VARCHAR(50),
    principal_amount DECIMAL(15,2),
    outstanding_balance DECIMAL(15,2),
    interest_rate DECIMAL(5,2),
    emi_amount DECIMAL(15,2),
    next_emi_date DATE,
    loan_status VARCHAR(20) DEFAULT 'ACTIVE',
    disbursement_date DATE,
    tenure_months INT
);

CREATE TABLE IF NOT EXISTS fixed_deposits (
    fd_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    principal_amount DECIMAL(15,2),
    interest_rate DECIMAL(5,2),
    maturity_date DATE,
    maturity_amount DECIMAL(15,2),
    tenure_days INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_date DATE
);

CREATE TABLE IF NOT EXISTS beneficiaries (
    beneficiary_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    beneficiary_name VARCHAR(255),
    account_number VARCHAR(50),
    ifsc_code VARCHAR(20),
    bank_name VARCHAR(255),
    beneficiary_type VARCHAR(20),
    nickname VARCHAR(100),
    added_date DATE,
    verified BOOLEAN DEFAULT FALSE
);

-- =====================================================================
-- SAMPLE BUSINESS DATA (Test Data for Scenarios)
-- =====================================================================

-- Sample Accounts
INSERT INTO accounts (account_id, user_id, account_name, account_type, currency, available_balance, current_balance, blocked_amount, opening_date, branch_code, ifsc_code) VALUES
('ACC001', 'user1', 'John Doe Savings', 'SAVINGS', 'INR', 125450.00, 130000.00, 4550.00, '2020-01-15', 'MUM001', 'AXIS0001234'),
('ACC002', 'user1', 'John Doe Current', 'CURRENT', 'INR', 50000.00, 50000.00, 0.00, '2021-06-20', 'MUM001', 'AXIS0001234'),
('ACC003', 'user2', 'Jane Smith Savings', 'SAVINGS', 'INR', 75000.00, 75000.00, 0.00, '2019-08-10', 'DEL002', 'AXIS0005678');

-- Sample Transactions
INSERT INTO transactions (txn_id, account_id, txn_date, txn_type, amount, description, category, balance_after) VALUES
('TXN001', 'ACC001', DATE_SUB(NOW(), INTERVAL 1 DAY), 'CREDIT', 50000.00, 'Salary Credit - Dec 2024', 'SALARY', 130000.00),
('TXN002', 'ACC001', DATE_SUB(NOW(), INTERVAL 2 DAY), 'DEBIT', 2500.00, 'Amazon Shopping', 'SHOPPING', 80000.00),
('TXN003', 'ACC001', DATE_SUB(NOW(), INTERVAL 3 DAY), 'DEBIT', 1500.00, 'Electricity Bill', 'UTILITIES', 82500.00),
('TXN004', 'ACC001', DATE_SUB(NOW(), INTERVAL 5 DAY), 'DEBIT', 3000.00, 'Restaurant - Dinner', 'FOOD', 84000.00),
('TXN005', 'ACC001', DATE_SUB(NOW(), INTERVAL 7 DAY), 'TRANSFER', 10000.00, 'Transfer to ACC002', 'TRANSFER', 87000.00),
('TXN006', 'ACC002', DATE_SUB(NOW(), INTERVAL 1 DAY), 'CREDIT', 10000.00, 'Transfer from ACC001', 'TRANSFER', 50000.00),
('TXN007', 'ACC002', DATE_SUB(NOW(), INTERVAL 3 DAY), 'DEBIT', 5000.00, 'Office Supplies', 'BUSINESS', 40000.00);

-- Sample Cards
INSERT INTO cards (card_id, linked_account, card_type, card_number_masked, card_holder_name, expiry_date, status, credit_limit, available_credit) VALUES
('CARD001', 'ACC001', 'DEBIT', '****1234', 'JOHN DOE', '2027-12-31', 'ACTIVE', NULL, NULL),
('CARD002', 'ACC001', 'CREDIT', '****5678', 'JOHN DOE', '2026-08-31', 'ACTIVE', 200000.00, 150000.00),
('CARD003', 'ACC002', 'DEBIT', '****9012', 'JOHN DOE', '2028-03-31', 'ACTIVE', NULL, NULL);

-- Sample Loans
INSERT INTO loans (loan_id, account_id, loan_type, principal_amount, outstanding_balance, interest_rate, emi_amount, next_emi_date, loan_status, disbursement_date, tenure_months) VALUES
('LOAN001', 'ACC001', 'HOME', 5000000.00, 4500000.00, 8.50, 45000.00, '2025-01-05', 'ACTIVE', '2023-01-15', 240),
('LOAN002', 'ACC001', 'PERSONAL', 200000.00, 150000.00, 12.00, 10000.00, '2025-01-10', 'ACTIVE', '2024-01-10', 24);

-- Sample Fixed Deposits
INSERT INTO fixed_deposits (fd_id, account_id, principal_amount, interest_rate, maturity_date, maturity_amount, tenure_days, status, created_date) VALUES
('FD001', 'ACC001', 100000.00, 7.25, '2025-06-15', 107250.00, 365, 'ACTIVE', '2024-06-15'),
('FD002', 'ACC001', 50000.00, 6.75, '2025-03-01', 53375.00, 180, 'ACTIVE', '2024-09-01');

-- Sample Beneficiaries
INSERT INTO beneficiaries (beneficiary_id, account_id, beneficiary_name, account_number, ifsc_code, bank_name, beneficiary_type, nickname, added_date, verified) VALUES
('BEN001', 'ACC001', 'Jane Smith', '1234567890', 'HDFC0001234', 'HDFC Bank', 'EXTERNAL', 'Jane', '2024-01-15', TRUE),
('BEN002', 'ACC001', 'ABC Company', '0987654321', 'ICIC0005678', 'ICICI Bank', 'EXTERNAL', 'Office', '2024-03-20', TRUE),
('BEN003', 'ACC001', 'John Doe Current', 'ACC002', 'AXIS0001234', 'Axis Bank', 'INTERNAL', 'My Current', '2024-02-10', TRUE);

-- Sample Bill Payments
INSERT INTO bill_payments (payment_id, account_id, biller_name, biller_category, amount, payment_date, status, reference_number) VALUES
('BILL001', 'ACC001', 'Mumbai Electricity', 'ELECTRICITY', 2500.00, DATE_SUB(NOW(), INTERVAL 5 DAY), 'SUCCESS', 'ELEC202412001'),
('BILL002', 'ACC001', 'Airtel Mobile', 'TELECOM', 599.00, DATE_SUB(NOW(), INTERVAL 10 DAY), 'SUCCESS', 'TEL202412001'),
('BILL003', 'ACC001', 'Netflix Subscription', 'SUBSCRIPTION', 649.00, DATE_SUB(NOW(), INTERVAL 15 DAY), 'SUCCESS', 'SUB202412001');
