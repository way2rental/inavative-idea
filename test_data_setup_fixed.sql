-- ================================================================
-- AI Orchestrator - Complete Test Data Setup (CORRECTED)
-- ================================================================
-- Purpose: Insert test data for 10 different intent scenarios
-- All operations are READ-ONLY for safety
-- Date: December 1, 2025
-- ================================================================

-- Clean up existing test data (optional - uncomment if needed)
-- DELETE FROM role_scenario_map;
-- DELETE FROM http_url_whitelist;
-- DELETE FROM ai_scenarios;
-- DELETE FROM test_loans;
-- DELETE FROM test_cards;
-- DELETE FROM test_transactions;
-- DELETE FROM test_accounts;

-- ================================================================
-- PART 1: Create Dummy Tables for Testing QueryExecutor
-- ================================================================

-- Dummy Accounts Table
CREATE TABLE IF NOT EXISTS test_accounts (
    account_id VARCHAR(50) PRIMARY KEY,
    account_holder VARCHAR(100) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    balance DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    branch_code VARCHAR(20),
    created_date DATE,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_holder (account_holder),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dummy Transactions Table
CREATE TABLE IF NOT EXISTS test_transactions (
    transaction_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    description TEXT,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    merchant_name VARCHAR(100),
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    category VARCHAR(50),
    INDEX idx_account_id (account_id),
    INDEX idx_transaction_date (transaction_date),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dummy Cards Table
CREATE TABLE IF NOT EXISTS test_cards (
    card_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    card_number VARCHAR(20) NOT NULL,
    card_type VARCHAR(50) NOT NULL,
    card_holder VARCHAR(100) NOT NULL,
    expiry_date DATE,
    cvv VARCHAR(4),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    credit_limit DECIMAL(15, 2),
    available_credit DECIMAL(15, 2),
    INDEX idx_account_id (account_id),
    INDEX idx_card_type (card_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dummy Loans Table
CREATE TABLE IF NOT EXISTS test_loans (
    loan_id VARCHAR(50) PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    loan_type VARCHAR(50) NOT NULL,
    principal_amount DECIMAL(15, 2) NOT NULL,
    outstanding_amount DECIMAL(15, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    emi_amount DECIMAL(15, 2),
    tenure_months INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    start_date DATE,
    maturity_date DATE,
    INDEX idx_account_id (account_id),
    INDEX idx_loan_type (loan_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ================================================================
-- PART 2: Insert Dummy Data into Test Tables
-- ================================================================

-- Insert Test Accounts
TRUNCATE TABLE test_accounts;
INSERT INTO test_accounts (account_id, account_holder, account_type, balance, currency, status, branch_code, created_date) VALUES
('ACC001', 'John Doe', 'SAVINGS', 50000.00, 'USD', 'ACTIVE', 'BR001', '2023-01-15'),
('ACC002', 'Jane Smith', 'CURRENT', 125000.50, 'USD', 'ACTIVE', 'BR002', '2023-03-20'),
('ACC003', 'Robert Johnson', 'SAVINGS', 75000.25, 'USD', 'ACTIVE', 'BR001', '2023-05-10'),
('ACC004', 'Emily Davis', 'CURRENT', 200000.00, 'USD', 'ACTIVE', 'BR003', '2023-06-01'),
('ACC005', 'Michael Brown', 'SAVINGS', 35000.75, 'USD', 'ACTIVE', 'BR002', '2023-08-15');

-- Insert Test Transactions
TRUNCATE TABLE test_transactions;
INSERT INTO test_transactions (transaction_id, account_id, transaction_type, amount, currency, description, status, merchant_name, transaction_date, category) VALUES
('TXN001', 'ACC001', 'DEBIT', 1500.00, 'USD', 'Grocery Shopping', 'COMPLETED', 'Walmart', '2025-11-25 10:30:00', 'GROCERIES'),
('TXN002', 'ACC001', 'CREDIT', 5000.00, 'USD', 'Salary Credit', 'COMPLETED', 'ABC Corp', '2025-11-01 09:00:00', 'SALARY'),
('TXN003', 'ACC002', 'DEBIT', 2500.50, 'USD', 'Online Shopping', 'COMPLETED', 'Amazon', '2025-11-28 14:20:00', 'SHOPPING'),
('TXN004', 'ACC002', 'CREDIT', 10000.00, 'USD', 'Business Income', 'COMPLETED', 'XYZ Ltd', '2025-11-15 11:00:00', 'INCOME'),
('TXN005', 'ACC003', 'DEBIT', 750.25, 'USD', 'Restaurant Bill', 'COMPLETED', 'The Bistro', '2025-11-29 19:45:00', 'DINING'),
('TXN006', 'ACC003', 'DEBIT', 3000.00, 'USD', 'Rent Payment', 'COMPLETED', 'Property Manager', '2025-11-01 08:00:00', 'RENT'),
('TXN007', 'ACC004', 'CREDIT', 25000.00, 'USD', 'Project Payment', 'COMPLETED', 'Client Co', '2025-11-20 16:30:00', 'INCOME'),
('TXN008', 'ACC004', 'DEBIT', 5000.00, 'USD', 'Electronics Purchase', 'COMPLETED', 'Best Buy', '2025-11-22 13:15:00', 'ELECTRONICS'),
('TXN009', 'ACC005', 'DEBIT', 500.00, 'USD', 'Fuel', 'COMPLETED', 'Shell Gas', '2025-11-27 07:30:00', 'FUEL'),
('TXN010', 'ACC005', 'CREDIT', 3500.00, 'USD', 'Freelance Payment', 'COMPLETED', 'Upwork', '2025-11-18 10:00:00', 'INCOME');

-- Insert Test Cards
TRUNCATE TABLE test_cards;
INSERT INTO test_cards (card_id, account_id, card_number, card_type, card_holder, expiry_date, cvv, status, credit_limit, available_credit) VALUES
('CARD001', 'ACC001', '**** **** **** 1234', 'DEBIT', 'John Doe', '2027-12-31', '***', 'ACTIVE', NULL, NULL),
('CARD002', 'ACC002', '**** **** **** 5678', 'CREDIT', 'Jane Smith', '2028-06-30', '***', 'ACTIVE', 100000.00, 95000.00),
('CARD003', 'ACC003', '**** **** **** 9012', 'DEBIT', 'Robert Johnson', '2027-09-30', '***', 'ACTIVE', NULL, NULL),
('CARD004', 'ACC004', '**** **** **** 3456', 'CREDIT', 'Emily Davis', '2029-03-31', '***', 'ACTIVE', 200000.00, 180000.00),
('CARD005', 'ACC005', '**** **** **** 7890', 'DEBIT', 'Michael Brown', '2027-11-30', '***', 'ACTIVE', NULL, NULL);

-- Insert Test Loans
TRUNCATE TABLE test_loans;
INSERT INTO test_loans (loan_id, account_id, loan_type, principal_amount, outstanding_amount, interest_rate, emi_amount, tenure_months, status, start_date, maturity_date) VALUES
('LOAN001', 'ACC001', 'HOME_LOAN', 5000000.00, 4250000.00, 8.50, 42500.00, 240, 'ACTIVE', '2020-01-15', '2040-01-15'),
('LOAN002', 'ACC002', 'CAR_LOAN', 1500000.00, 800000.00, 9.00, 25000.00, 84, 'ACTIVE', '2022-06-01', '2029-06-01'),
('LOAN003', 'ACC003', 'PERSONAL_LOAN', 500000.00, 350000.00, 11.50, 15000.00, 48, 'ACTIVE', '2023-03-15', '2027-03-15'),
('LOAN004', 'ACC004', 'BUSINESS_LOAN', 10000000.00, 8500000.00, 10.00, 150000.00, 120, 'ACTIVE', '2021-09-01', '2031-09-01'),
('LOAN005', 'ACC005', 'EDUCATION_LOAN', 2000000.00, 1700000.00, 8.00, 28000.00, 120, 'ACTIVE', '2022-08-01', '2032-08-01');

-- ================================================================
-- PART 3: Insert AI Scenarios (10 READ-ONLY Test Scenarios)
-- ================================================================

INSERT INTO ai_scenarios (
    scenario_code,
    description,
    execution_type,
    http_method,
    http_url,
    http_headers,
    sql_query,
    request_mapping,
    response_mapping,
    timeout_ms,
    executor_bean,
    security_level,
    required_params,
    optional_params,
    llm_prompt_template,
    prompt_version,
    prompt_history,
    active
) VALUES

-- Scenario 1: Account Balance (Query Executor - READ ONLY)
('ACCOUNT_BALANCE', 'Check account balance', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT account_id, account_holder, balance, currency, account_type, status FROM test_accounts WHERE account_id = :accountId',
'{"accountId": "$.params.accountId"}',
'{"account_id": "$.account_id", "balance": "$.balance"}',
5000, 'queryExecutor', 'LOW',
'["accountId"]', '[]',
'You are a banking assistant. Format the account balance information clearly.',
1, NULL, TRUE),

-- Scenario 2: Transaction History (Query Executor - READ ONLY)
('TRANSACTION_HISTORY', 'View recent transactions', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT transaction_id, transaction_type, amount, currency, description, merchant_name, transaction_date, status FROM test_transactions WHERE account_id = :accountId ORDER BY transaction_date DESC LIMIT 10',
'{"accountId": "$.params.accountId"}',
'{"transactions": "$"}',
5000, 'queryExecutor', 'LOW',
'["accountId"]', '[]',
'Present transaction history in chronological order.',
1, NULL, TRUE),

-- Scenario 3: Account Summary (Query Executor - READ ONLY)
('ACCOUNT_SUMMARY', 'Get complete account summary', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT a.account_id, a.account_holder, a.balance, a.currency, a.account_type, a.status, a.branch_code, COUNT(t.transaction_id) as total_transactions, SUM(CASE WHEN t.transaction_type = ''CREDIT'' THEN t.amount ELSE 0 END) as total_credits, SUM(CASE WHEN t.transaction_type = ''DEBIT'' THEN t.amount ELSE 0 END) as total_debits FROM test_accounts a LEFT JOIN test_transactions t ON a.account_id = t.account_id WHERE a.account_id = :accountId GROUP BY a.account_id',
'{"accountId": "$.params.accountId"}',
'{"account_summary": "$"}',
5000, 'queryExecutor', 'MEDIUM',
'["accountId"]', '[]',
'Provide comprehensive account overview.',
1, NULL, TRUE),

-- Scenario 4: Card Details (Query Executor - READ ONLY)
('CARD_DETAILS', 'View card information', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT card_id, card_number, card_type, card_holder, expiry_date, status, credit_limit, available_credit FROM test_cards WHERE account_id = :accountId',
'{"accountId": "$.params.accountId"}',
'{"cards": "$"}',
5000, 'queryExecutor', 'HIGH',
'["accountId"]', '[]',
'Display card information securely.',
1, NULL, TRUE),

-- Scenario 5: Loan Status (Query Executor - READ ONLY)
('LOAN_STATUS', 'Check loan details and status', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT loan_id, loan_type, principal_amount, outstanding_amount, interest_rate, emi_amount, tenure_months, status, start_date, maturity_date FROM test_loans WHERE account_id = :accountId',
'{"accountId": "$.params.accountId"}',
'{"loans": "$"}',
5000, 'queryExecutor', 'MEDIUM',
'["accountId"]', '[]',
'Present loan details with EMI and payment schedule.',
1, NULL, TRUE),

-- Scenario 6: Spending Analysis (Query Executor - READ ONLY)
('SPENDING_ANALYSIS', 'Analyze spending by category', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT category, SUM(amount) as total_spent, COUNT(*) as transaction_count FROM test_transactions WHERE account_id = :accountId AND transaction_type = ''DEBIT'' AND transaction_date >= DATE_SUB(NOW(), INTERVAL 30 DAY) GROUP BY category ORDER BY total_spent DESC',
'{"accountId": "$.params.accountId"}',
'{"spending_analysis": "$"}',
5000, 'queryExecutor', 'LOW',
'["accountId"]', '[]',
'Analyze spending patterns by category.',
1, NULL, TRUE),

-- Scenario 7: Fund Transfer Preview (HTTP GET - READ ONLY)
-- NOTE: Uses GET to TEST API - NO actual transfer happens
('FUND_TRANSFER', 'Preview fund transfer details', 'HTTP_CALL',
'GET', 'https://jsonplaceholder.typicode.com/posts/1',
'{"Content-Type": "application/json", "X-Test-Mode": "true"}',
NULL,
'{"fromAccount": "$.params.fromAccountId", "toAccount": "$.params.toAccountId", "amount": "$.params.amount"}',
'{"transactionId": "$.id", "status": "preview", "message": "Transfer preview - no actual transfer made"}',
5000, 'httpExecutor', 'HIGH',
'["fromAccountId", "toAccountId", "amount"]', '["remarks"]',
'Show transfer preview with validation. NO actual transfer occurs in test mode.',
1, NULL, TRUE),

-- Scenario 8: Bill Payment Preview (HTTP GET - READ ONLY)
-- NOTE: Uses GET to TEST API - NO actual payment happens
('BILL_PAYMENT', 'Preview bill payment details', 'HTTP_CALL',
'GET', 'https://jsonplaceholder.typicode.com/posts/2',
'{"Content-Type": "application/json", "X-Test-Mode": "true"}',
NULL,
'{"accountId": "$.params.accountId", "billType": "$.params.billType", "amount": "$.params.amount"}',
'{"paymentId": "$.id", "status": "preview", "message": "Payment preview - no actual payment made"}',
5000, 'httpExecutor', 'HIGH',
'["accountId", "billType", "amount", "billerId"]', '["dueDate"]',
'Show payment preview with bill details. NO actual payment occurs in test mode.',
1, NULL, TRUE),

-- Scenario 9: Credit Score Check (HTTP GET - READ ONLY)
('CREDIT_SCORE', 'Check credit score', 'HTTP_CALL',
'GET', 'https://jsonplaceholder.typicode.com/users/1',
'{"Content-Type": "application/json", "X-Credit-Bureau": "TEST"}',
NULL,
'{"userId": "$.params.userId"}',
'{"creditScore": "750", "rating": "GOOD", "lastUpdated": "2025-11-30"}',
5000, 'httpExecutor', 'MEDIUM',
'["userId"]', '[]',
'Present credit score with interpretation.',
1, NULL, TRUE),

-- Scenario 10: Investment Portfolio (Query Executor - READ ONLY)
('INVESTMENT_PORTFOLIO', 'View investment portfolio', 'DB_QUERY',
NULL, NULL, NULL,
'SELECT ''INV001'' as investment_id, ''Mutual Fund'' as investment_type, 150000.00 as invested_amount, 175000.00 as current_value, 16.67 as returns_percentage, ''ACTIVE'' as status UNION SELECT ''INV002'', ''Stocks'', 200000.00, 245000.00, 22.50, ''ACTIVE'' UNION SELECT ''INV003'', ''Fixed Deposit'', 500000.00, 525000.00, 5.00, ''ACTIVE''',
'{}',
'{"investments": "$"}',
5000, 'queryExecutor', 'LOW',
'[]', '[]',
'Display investment portfolio with returns.',
1, NULL, TRUE);

-- ================================================================
-- PART 4: Insert HTTP URL Whitelist (All GET methods for read-only)
-- ================================================================

INSERT INTO http_url_whitelist (
    url_pattern,
    description,
    allowed_methods,
    added_by,
    active,
    created_at,
    updated_at
) VALUES
('https://jsonplaceholder.typicode.com/.*', 'JSONPlaceholder - Test API (READ ONLY)', 'GET', 'system', TRUE, NOW(), NOW()),
('https://api.example.com/.*', 'Example API (READ ONLY)', 'GET', 'system', TRUE, NOW(), NOW()),
('https://reqres.in/api/users/.*', 'Reqres API - User endpoint (READ ONLY)', 'GET', 'system', TRUE, NOW(), NOW()),
('http://localhost:.*/api/test/.*', 'Local test APIs (READ ONLY)', 'GET', 'system', TRUE, NOW(), NOW()),
('https://httpbin.org/get', 'HTTPBin GET endpoint (READ ONLY)', 'GET', 'system', TRUE, NOW(), NOW());

-- ================================================================
-- PART 5: Insert Role-Scenario Mappings (RBAC)
-- ================================================================

-- Admin has access to ALL scenarios
INSERT INTO role_scenario_map (role_name, scenario_code) VALUES
('ADMIN', 'ACCOUNT_BALANCE'),
('ADMIN', 'TRANSACTION_HISTORY'),
('ADMIN', 'ACCOUNT_SUMMARY'),
('ADMIN', 'CARD_DETAILS'),
('ADMIN', 'LOAN_STATUS'),
('ADMIN', 'SPENDING_ANALYSIS'),
('ADMIN', 'FUND_TRANSFER'),
('ADMIN', 'BILL_PAYMENT'),
('ADMIN', 'CREDIT_SCORE'),
('ADMIN', 'INVESTMENT_PORTFOLIO');

-- User has access to read-only scenarios
INSERT INTO role_scenario_map (role_name, scenario_code) VALUES
('USER', 'ACCOUNT_BALANCE'),
('USER', 'TRANSACTION_HISTORY'),
('USER', 'ACCOUNT_SUMMARY'),
('USER', 'CARD_DETAILS'),
('USER', 'LOAN_STATUS'),
('USER', 'SPENDING_ANALYSIS'),
('USER', 'CREDIT_SCORE'),
('USER', 'INVESTMENT_PORTFOLIO');

-- Manager has access to most scenarios including previews
INSERT INTO role_scenario_map (role_name, scenario_code) VALUES
('MANAGER', 'ACCOUNT_BALANCE'),
('MANAGER', 'TRANSACTION_HISTORY'),
('MANAGER', 'ACCOUNT_SUMMARY'),
('MANAGER', 'CARD_DETAILS'),
('MANAGER', 'LOAN_STATUS'),
('MANAGER', 'SPENDING_ANALYSIS'),
('MANAGER', 'FUND_TRANSFER'),
('MANAGER', 'BILL_PAYMENT'),
('MANAGER', 'CREDIT_SCORE'),
('MANAGER', 'INVESTMENT_PORTFOLIO');

-- ================================================================
-- VERIFICATION & SUMMARY
-- ================================================================

SELECT '✅ SETUP COMPLETE - ALL SCENARIOS ARE READ-ONLY!' as STATUS;

SELECT 'Entity' as CATEGORY, 'Count' as TOTAL, 'Notes' as DESCRIPTION
UNION ALL
SELECT 'AI Scenarios', CAST(COUNT(*) AS CHAR), 'All READ-ONLY operations'
FROM ai_scenarios WHERE scenario_code IN (
    'ACCOUNT_BALANCE', 'TRANSACTION_HISTORY', 'ACCOUNT_SUMMARY',
    'CARD_DETAILS', 'LOAN_STATUS', 'SPENDING_ANALYSIS',
    'FUND_TRANSFER', 'BILL_PAYMENT', 'CREDIT_SCORE', 'INVESTMENT_PORTFOLIO'
)
UNION ALL
SELECT 'Test Accounts', CAST(COUNT(*) AS CHAR), 'Sample account data' FROM test_accounts
UNION ALL
SELECT 'Test Transactions', CAST(COUNT(*) AS CHAR), 'Sample transactions' FROM test_transactions
UNION ALL
SELECT 'Test Cards', CAST(COUNT(*) AS CHAR), 'Sample card data' FROM test_cards
UNION ALL
SELECT 'Test Loans', CAST(COUNT(*) AS CHAR), 'Sample loan data' FROM test_loans
UNION ALL
SELECT 'URL Whitelist', CAST(COUNT(*) AS CHAR), 'GET methods only' FROM http_url_whitelist
UNION ALL
SELECT 'RBAC Mappings', CAST(COUNT(*) AS CHAR), 'Role permissions' FROM role_scenario_map;

-- ================================================================
-- IMPORTANT NOTES
-- ================================================================
/*
✅ ALL SCENARIOS ARE READ-ONLY:
1. Query Executor: Uses SELECT statements only
2. HTTP Calls: All use GET method to test APIs
3. Fund Transfer: Preview only - NO actual transfer
4. Bill Payment: Preview only - NO actual payment
5. URLs whitelisted for GET only

🔒 SAFETY FEATURES:
- No UPDATE/DELETE/INSERT queries
- No POST/PUT/DELETE HTTP methods
- All external APIs are test/mock endpoints
- CVV numbers masked in test data
- No sensitive operations possible

📝 TEST QUERIES:
1. "What is my account balance for ACC001?"
2. "Show my recent transactions for ACC002"
3. "Show account summary for ACC003"
4. "Show my card details for ACC004"
5. "What is my loan status for ACC001?"
6. "Show spending analysis for ACC001"
7. "Preview transfer of 1000 from ACC001 to ACC002"
8. "Preview electricity bill payment of 2500 from ACC002"
9. "What is my credit score for USR001?"
10. "Show my investment portfolio"
*/

SELECT '🎉 All test data inserted successfully! All operations are READ-ONLY and safe.' as MESSAGE;

