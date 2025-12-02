-- =====================================================================
-- AI Orchestrator - Test Data for SaaS Deployment
-- Version 1.0.0 - INSERT ONLY (JPA handles table creation)
-- =====================================================================
-- NO HARDCODED VALUES - all configuration comes from database.
-- Tables are auto-created by JPA/Hibernate from entity annotations.
-- This file only contains INSERT statements for test data.
-- =====================================================================

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
-- FilterEngine handles WHERE clause generation - base SQL should not have WHERE
-- =====================================================================

INSERT INTO ai_scenarios (
    scenario_code, scenario_name, description, execution_type, sql_query,
    required_params, trigger_phrases, example_queries, category, icon,
    filter_definitions, max_results, default_sort, active
) VALUES 

-- 1. Account Balance (FilterEngine generates WHERE clause)
('ACCOUNT_BALANCE', 'Check Account Balance', 
 'View current balance and available funds for a bank account',
 'DB_QUERY',
 'SELECT account_id, account_name, account_type, currency, available_balance, current_balance, blocked_amount, last_updated FROM accounts',
 '["accountId"]',
 '["balance", "check balance", "account balance", "how much money", "available funds", "show balance", "my balance"]',
 '["What is my account balance?", "Show balance for ACC001", "How much money do I have?", "Check my available balance"]',
 'Account', 'wallet',
 '[{"name":"accountId","displayName":"Account ID","description":"Bank account number starting with ACC","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true,"validationPattern":"^ACC[0-9]+$","validationError":"Account ID must start with ACC followed by numbers"}]',
 1, 'last_updated DESC', TRUE),

-- 2. Transaction History (Multiple filters supported via FilterEngine)
('TRANSACTION_HISTORY', 'Transaction History',
 'View transaction history with filters for date, amount, and type',
 'DB_QUERY',
 'SELECT txn_id, account_id, txn_date, txn_type, amount, currency, description, category, balance_after, status FROM transactions',
 '["accountId"]',
 '["transactions", "transaction history", "recent transactions", "show transactions", "txn history", "my transactions", "payment history"]',
 '["Show my recent transactions", "Transaction history for ACC001", "Show transactions from last week", "List all debit transactions"]',
 'Transaction', 'receipt',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"dateFrom","displayName":"From Date","type":"DATE","dbColumn":"txn_date","operator":">=","mandatory":false},{"name":"dateTo","displayName":"To Date","type":"DATE","dbColumn":"txn_date","operator":"<=","mandatory":false},{"name":"txnType","displayName":"Transaction Type","type":"ENUM","dbColumn":"txn_type","operator":"=","mandatory":false,"enumValues":["CREDIT","DEBIT","TRANSFER"]},{"name":"minAmount","displayName":"Minimum Amount","type":"DECIMAL","dbColumn":"amount","operator":">=","mandatory":false},{"name":"maxAmount","displayName":"Maximum Amount","type":"DECIMAL","dbColumn":"amount","operator":"<=","mandatory":false}]',
 50, 'txn_date DESC', TRUE),

-- 3. Account Summary (with subquery, filter applied to main table alias)
('ACCOUNT_SUMMARY', 'Account Summary',
 'Get comprehensive account summary including balance, recent activity, and account details',
 'DB_QUERY',
 'SELECT a.account_id, a.account_name, a.account_type, a.currency, a.available_balance, a.current_balance, a.opening_date, a.branch_code, a.ifsc_code, (SELECT COUNT(*) FROM transactions t WHERE t.account_id = a.account_id AND t.txn_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)) as recent_txn_count FROM accounts a',
 '["accountId"]',
 '["account summary", "account details", "account info", "account overview", "my account", "show account"]',
 '["Show my account summary", "Account details for ACC001", "Give me account overview"]',
 'Account', 'file-text',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"a.account_id","operator":"=","mandatory":true}]',
 1, NULL, TRUE),

-- 4. Fund Transfer Status (OR conditions handled by FilterEngine)
('FUND_TRANSFER_STATUS', 'Fund Transfer Status',
 'Check the status of a fund transfer using transfer reference number',
 'DB_QUERY',
 'SELECT transfer_id, from_account, to_account, amount, currency, status, initiated_at, completed_at, remarks, failure_reason FROM fund_transfers',
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
 'SELECT payment_id, biller_name, biller_category, amount, payment_date, status, account_id, reference_number FROM bill_payments',
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
 'SELECT card_id, card_type, card_number_masked, card_holder_name, expiry_date, status, credit_limit, available_credit, linked_account FROM cards',
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
 'SELECT loan_id, loan_type, principal_amount, outstanding_balance, interest_rate, emi_amount, next_emi_date, loan_status, disbursement_date, tenure_months, account_id FROM loans',
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
 'SELECT fd_id, account_id, principal_amount, interest_rate, maturity_date, maturity_amount, tenure_days, status, created_date FROM fixed_deposits',
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
 'SELECT beneficiary_id, beneficiary_name, account_number, ifsc_code, bank_name, beneficiary_type, nickname, added_date, verified, account_id FROM beneficiaries',
 '["accountId"]',
 '["beneficiaries", "payee list", "saved accounts", "transfer contacts", "my beneficiaries"]',
 '["Show my beneficiaries", "List saved payees", "Who can I transfer money to?"]',
 'Payment', 'users',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"beneficiaryType","displayName":"Beneficiary Type","type":"ENUM","dbColumn":"beneficiary_type","operator":"=","mandatory":false,"enumValues":["INTERNAL","EXTERNAL","INTERNATIONAL"]}]',
 50, 'beneficiary_name ASC', TRUE),

-- 10. Spending Analysis (GROUP BY query with filters before GROUP BY)
('SPENDING_ANALYSIS', 'Spending Analysis',
 'Analyze spending patterns by category and time period',
 'DB_QUERY',
 'SELECT category, SUM(amount) as total_spent, COUNT(*) as transaction_count, AVG(amount) as avg_transaction FROM transactions WHERE txn_type = ''DEBIT''',
 '["accountId", "dateFrom"]',
 '["spending analysis", "where did I spend", "spending breakdown", "expense analysis", "spending pattern", "analyze spending"]',
 '["Analyze my spending", "Where did I spend money last month?", "Spending breakdown by category"]',
 'Analytics', 'chart-pie',
 '[{"name":"accountId","displayName":"Account ID","type":"STRING","dbColumn":"account_id","operator":"=","mandatory":true},{"name":"dateFrom","displayName":"From Date","type":"DATE","dbColumn":"txn_date","operator":">=","mandatory":true},{"name":"dateTo","displayName":"To Date","type":"DATE","dbColumn":"txn_date","operator":"<=","mandatory":false}]',
 20, NULL, TRUE);

-- Update SPENDING_ANALYSIS to add GROUP BY (FilterEngine adds filters before GROUP BY)
UPDATE ai_scenarios SET sql_query = 'SELECT category, SUM(amount) as total_spent, COUNT(*) as transaction_count, AVG(amount) as avg_transaction FROM transactions WHERE txn_type = ''DEBIT'' GROUP BY category' WHERE scenario_code = 'SPENDING_ANALYSIS';

-- =====================================================================
-- SAMPLE DATA - INTENT CONFIGS (uses intent_key per IntentConfig entity)
-- =====================================================================

INSERT INTO ai_intents (intent_key, intent_name, scenario_code, training_phrases, confidence_threshold, category, active) VALUES
('INTENT_BALANCE', 'Account Balance Intent', 'ACCOUNT_BALANCE', '["check balance", "account balance", "how much money", "available balance", "show balance", "my balance", "what is my balance", "balance enquiry"]', 0.85, 'Account', TRUE),
('INTENT_TRANSACTIONS', 'Transaction History Intent', 'TRANSACTION_HISTORY', '["show transactions", "transaction history", "recent transactions", "my transactions", "list transactions", "transaction list"]', 0.85, 'Transaction', TRUE),
('INTENT_SUMMARY', 'Account Summary Intent', 'ACCOUNT_SUMMARY', '["account summary", "account details", "account info", "my account", "account overview"]', 0.85, 'Account', TRUE),
('INTENT_TRANSFER_STATUS', 'Transfer Status Intent', 'FUND_TRANSFER_STATUS', '["transfer status", "payment status", "check transfer", "did transfer go through"]', 0.85, 'Payment', TRUE),
('INTENT_BILLS', 'Bill Payment Intent', 'BILL_PAYMENT_HISTORY', '["bill payments", "utility bills", "paid bills", "bill history"]', 0.85, 'Payment', TRUE),
('INTENT_CARDS', 'Card Details Intent', 'CARD_DETAILS', '["card details", "my cards", "credit card", "debit card", "card info"]', 0.85, 'Card', TRUE),
('INTENT_LOANS', 'Loan Status Intent', 'LOAN_STATUS', '["loan status", "my loan", "emi details", "loan balance", "outstanding loan"]', 0.85, 'Loan', TRUE),
('INTENT_FD', 'Fixed Deposit Intent', 'FIXED_DEPOSIT_DETAILS', '["fixed deposit", "fd details", "my fd", "term deposit"]', 0.85, 'Investment', TRUE),
('INTENT_BENEFICIARIES', 'Beneficiary List Intent', 'BENEFICIARY_LIST', '["beneficiaries", "payee list", "saved accounts", "transfer contacts"]', 0.85, 'Payment', TRUE),
('INTENT_SPENDING', 'Spending Analysis Intent', 'SPENDING_ANALYSIS', '["spending analysis", "expense analysis", "spending pattern", "where did I spend"]', 0.85, 'Analytics', TRUE);

-- =====================================================================
-- SAMPLE DATA - RBAC (Role-Scenario Mapping)
-- =====================================================================

INSERT INTO role_scenario_map (role_name, scenario_code) VALUES
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
-- SAMPLE DATA - POLICY RULES
-- =====================================================================

INSERT INTO ai_policy_rules (policy_key, policy_name, description, rule_expression, on_fail, failure_message, applicable_scenarios, applicable_roles, priority, active) VALUES
('QUERY_LENGTH_LIMIT', 'Query Length Limit', 'Limit user query length for safety', '#{query.length() <= 1000}', 'BLOCK', 'Your query is too long. Please keep it under 1000 characters.', NULL, NULL, 100, TRUE),
('RATE_LIMIT', 'Rate Limit Policy', 'Prevent excessive queries per minute', '#{requestCount <= 60}', 'WARN', 'You are making too many requests. Please slow down.', NULL, NULL, 90, TRUE),
('HIGH_VALUE_TRANSFER_ADMIN', 'High Value Transfer Restriction', 'High value transfers require admin role', '#{amount <= 100000 || user.hasRole("ADMIN")}', 'BLOCK', 'Transfers over 1 lakh require admin approval.', '["FUND_TRANSFER_STATUS"]', NULL, 80, TRUE);

-- =====================================================================
-- SAMPLE DATA - HTTP URL WHITELIST
-- =====================================================================

INSERT INTO http_url_whitelist (url_pattern, allowed_methods, description, added_by, active) VALUES
('https://api.example.com/v1/*', 'GET', 'Example Bank External API', 'system', TRUE),
('https://internal.bank.com/api/*', 'GET,POST', 'Internal Bank Services', 'system', TRUE);

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

-- Sample Fund Transfers
INSERT INTO fund_transfers (transfer_id, from_account, to_account, amount, currency, status, initiated_at, completed_at, remarks) VALUES
('TXF001', 'ACC001', 'ACC002', 10000.00, 'INR', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 'Monthly transfer'),
('TXF002', 'ACC001', '1234567890', 25000.00, 'INR', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), 'Payment to Jane'),
('TXF003', 'ACC001', '0987654321', 50000.00, 'INR', 'PENDING', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 'Payment to ABC Company');

-- =====================================================================
-- SAMPLE DATA - FOLLOW-UP GROUPS (uses group_key per FollowUpGroup entity)
-- =====================================================================

INSERT INTO ai_followup_groups (group_key, description, scenario_codes, questions, question_order, active) VALUES
('ACCOUNT_FOLLOWUPS', 'Follow-up questions for account-related scenarios', 
 '["ACCOUNT_BALANCE", "ACCOUNT_SUMMARY", "TRANSACTION_HISTORY"]',
 '[{"key":"viewTransactions","question":"Would you like to see recent transactions?","type":"BOOLEAN"},{"key":"viewCards","question":"Would you like to see cards linked to this account?","type":"BOOLEAN"}]',
 '["viewTransactions", "viewCards"]', TRUE),
('PAYMENT_FOLLOWUPS', 'Follow-up questions for payment scenarios',
 '["FUND_TRANSFER_STATUS", "BILL_PAYMENT_HISTORY"]',
 '[{"key":"viewMore","question":"Would you like to see more payment details?","type":"BOOLEAN"},{"key":"repeatPayment","question":"Would you like to make another payment?","type":"BOOLEAN"}]',
 '["viewMore", "repeatPayment"]', TRUE);

-- =====================================================================
-- SAMPLE DATA - RESPONSE MAPPINGS (Required for AI response formatting)
-- Maps DB columns to AI-friendly structured output with masking
-- =====================================================================

-- ACCOUNT_BALANCE response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('ACCOUNT_BALANCE', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 1),
('ACCOUNT_BALANCE', 'DB_QUERY', 'account_name', 'accountName', '$.account_name', 'NONE', 2),
('ACCOUNT_BALANCE', 'DB_QUERY', 'account_type', 'accountType', '$.account_type', 'NONE', 3),
('ACCOUNT_BALANCE', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 4),
('ACCOUNT_BALANCE', 'DB_QUERY', 'available_balance', 'availableBalance', '$.available_balance', 'NONE', 5),
('ACCOUNT_BALANCE', 'DB_QUERY', 'current_balance', 'currentBalance', '$.current_balance', 'NONE', 6),
('ACCOUNT_BALANCE', 'DB_QUERY', 'blocked_amount', 'blockedAmount', '$.blocked_amount', 'NONE', 7),
('ACCOUNT_BALANCE', 'DB_QUERY', 'last_updated', 'lastUpdated', '$.last_updated', 'NONE', 8);

-- TRANSACTION_HISTORY response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('TRANSACTION_HISTORY', 'DB_QUERY', 'txn_id', 'transactionId', '$.txn_id', 'NONE', 1),
('TRANSACTION_HISTORY', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 2),
('TRANSACTION_HISTORY', 'DB_QUERY', 'txn_date', 'transactionDate', '$.txn_date', 'NONE', 3),
('TRANSACTION_HISTORY', 'DB_QUERY', 'txn_type', 'transactionType', '$.txn_type', 'NONE', 4),
('TRANSACTION_HISTORY', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 5),
('TRANSACTION_HISTORY', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 6),
('TRANSACTION_HISTORY', 'DB_QUERY', 'description', 'description', '$.description', 'NONE', 7),
('TRANSACTION_HISTORY', 'DB_QUERY', 'category', 'category', '$.category', 'NONE', 8),
('TRANSACTION_HISTORY', 'DB_QUERY', 'balance_after', 'balanceAfter', '$.balance_after', 'NONE', 9),
('TRANSACTION_HISTORY', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 10);

-- ACCOUNT_SUMMARY response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('ACCOUNT_SUMMARY', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 1),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'account_name', 'accountName', '$.account_name', 'NONE', 2),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'account_type', 'accountType', '$.account_type', 'NONE', 3),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 4),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'available_balance', 'availableBalance', '$.available_balance', 'NONE', 5),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'current_balance', 'currentBalance', '$.current_balance', 'NONE', 6),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'opening_date', 'openingDate', '$.opening_date', 'NONE', 7),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'branch_code', 'branchCode', '$.branch_code', 'NONE', 8),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'ifsc_code', 'ifscCode', '$.ifsc_code', 'NONE', 9),
('ACCOUNT_SUMMARY', 'DB_QUERY', 'recent_txn_count', 'recentTransactionCount', '$.recent_txn_count', 'NONE', 10);

-- FUND_TRANSFER_STATUS response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'transfer_id', 'transferId', '$.transfer_id', 'NONE', 1),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'from_account', 'fromAccount', '$.from_account', 'ACCOUNT', 2),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'to_account', 'toAccount', '$.to_account', 'ACCOUNT', 3),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 4),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 5),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 6),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'initiated_at', 'initiatedAt', '$.initiated_at', 'NONE', 7),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'completed_at', 'completedAt', '$.completed_at', 'NONE', 8),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'remarks', 'remarks', '$.remarks', 'NONE', 9),
('FUND_TRANSFER_STATUS', 'DB_QUERY', 'failure_reason', 'failureReason', '$.failure_reason', 'NONE', 10);

-- BILL_PAYMENT_HISTORY response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'payment_id', 'paymentId', '$.payment_id', 'NONE', 1),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'biller_name', 'billerName', '$.biller_name', 'NONE', 2),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'biller_category', 'billerCategory', '$.biller_category', 'NONE', 3),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 4),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'payment_date', 'paymentDate', '$.payment_date', 'NONE', 5),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 6),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 7),
('BILL_PAYMENT_HISTORY', 'DB_QUERY', 'reference_number', 'referenceNumber', '$.reference_number', 'NONE', 8);

-- CARD_DETAILS response mappings (with sensitive data masking)
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('CARD_DETAILS', 'DB_QUERY', 'card_id', 'cardId', '$.card_id', 'NONE', 1),
('CARD_DETAILS', 'DB_QUERY', 'card_type', 'cardType', '$.card_type', 'NONE', 2),
('CARD_DETAILS', 'DB_QUERY', 'card_number_masked', 'cardNumber', '$.card_number_masked', 'CARD', 3),
('CARD_DETAILS', 'DB_QUERY', 'card_holder_name', 'cardHolderName', '$.card_holder_name', 'NONE', 4),
('CARD_DETAILS', 'DB_QUERY', 'expiry_date', 'expiryDate', '$.expiry_date', 'NONE', 5),
('CARD_DETAILS', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 6),
('CARD_DETAILS', 'DB_QUERY', 'credit_limit', 'creditLimit', '$.credit_limit', 'NONE', 7),
('CARD_DETAILS', 'DB_QUERY', 'available_credit', 'availableCredit', '$.available_credit', 'NONE', 8),
('CARD_DETAILS', 'DB_QUERY', 'linked_account', 'linkedAccount', '$.linked_account', 'ACCOUNT', 9);

-- LOAN_STATUS response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('LOAN_STATUS', 'DB_QUERY', 'loan_id', 'loanId', '$.loan_id', 'NONE', 1),
('LOAN_STATUS', 'DB_QUERY', 'loan_type', 'loanType', '$.loan_type', 'NONE', 2),
('LOAN_STATUS', 'DB_QUERY', 'principal_amount', 'principalAmount', '$.principal_amount', 'NONE', 3),
('LOAN_STATUS', 'DB_QUERY', 'outstanding_balance', 'outstandingBalance', '$.outstanding_balance', 'NONE', 4),
('LOAN_STATUS', 'DB_QUERY', 'interest_rate', 'interestRate', '$.interest_rate', 'NONE', 5),
('LOAN_STATUS', 'DB_QUERY', 'emi_amount', 'emiAmount', '$.emi_amount', 'NONE', 6),
('LOAN_STATUS', 'DB_QUERY', 'next_emi_date', 'nextEmiDate', '$.next_emi_date', 'NONE', 7),
('LOAN_STATUS', 'DB_QUERY', 'loan_status', 'status', '$.loan_status', 'NONE', 8),
('LOAN_STATUS', 'DB_QUERY', 'disbursement_date', 'disbursementDate', '$.disbursement_date', 'NONE', 9),
('LOAN_STATUS', 'DB_QUERY', 'tenure_months', 'tenureMonths', '$.tenure_months', 'NONE', 10),
('LOAN_STATUS', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 11);

-- FIXED_DEPOSIT_DETAILS response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'fd_id', 'fdId', '$.fd_id', 'NONE', 1),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'account_id', 'accountId', '$.account_id', 'ACCOUNT', 2),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'principal_amount', 'principalAmount', '$.principal_amount', 'NONE', 3),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'interest_rate', 'interestRate', '$.interest_rate', 'NONE', 4),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'maturity_date', 'maturityDate', '$.maturity_date', 'NONE', 5),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'maturity_amount', 'maturityAmount', '$.maturity_amount', 'NONE', 6),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'tenure_days', 'tenureDays', '$.tenure_days', 'NONE', 7),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 8),
('FIXED_DEPOSIT_DETAILS', 'DB_QUERY', 'created_date', 'createdDate', '$.created_date', 'NONE', 9);

-- BENEFICIARY_LIST response mappings (with sensitive data masking)
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('BENEFICIARY_LIST', 'DB_QUERY', 'beneficiary_id', 'beneficiaryId', '$.beneficiary_id', 'NONE', 1),
('BENEFICIARY_LIST', 'DB_QUERY', 'beneficiary_name', 'beneficiaryName', '$.beneficiary_name', 'NONE', 2),
('BENEFICIARY_LIST', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 3),
('BENEFICIARY_LIST', 'DB_QUERY', 'ifsc_code', 'ifscCode', '$.ifsc_code', 'NONE', 4),
('BENEFICIARY_LIST', 'DB_QUERY', 'bank_name', 'bankName', '$.bank_name', 'NONE', 5),
('BENEFICIARY_LIST', 'DB_QUERY', 'beneficiary_type', 'beneficiaryType', '$.beneficiary_type', 'NONE', 6),
('BENEFICIARY_LIST', 'DB_QUERY', 'nickname', 'nickname', '$.nickname', 'NONE', 7),
('BENEFICIARY_LIST', 'DB_QUERY', 'added_date', 'addedDate', '$.added_date', 'NONE', 8),
('BENEFICIARY_LIST', 'DB_QUERY', 'verified', 'verified', '$.verified', 'NONE', 9),
('BENEFICIARY_LIST', 'DB_QUERY', 'account_id', 'ownerAccountId', '$.account_id', 'ACCOUNT', 10);

-- SPENDING_ANALYSIS response mappings
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order) VALUES
('SPENDING_ANALYSIS', 'DB_QUERY', 'category', 'category', '$.category', 'NONE', 1),
('SPENDING_ANALYSIS', 'DB_QUERY', 'total_spent', 'totalSpent', '$.total_spent', 'NONE', 2),
('SPENDING_ANALYSIS', 'DB_QUERY', 'transaction_count', 'transactionCount', '$.transaction_count', 'NONE', 3),
('SPENDING_ANALYSIS', 'DB_QUERY', 'avg_transaction', 'averageTransaction', '$.avg_transaction', 'NONE', 4);
