-- V3: Add Banking Service Scenarios and Complete Role Mappings
-- Adds all customer-facing banking scenarios to support comprehensive AI assistant

-- Insert additional banking scenarios
INSERT INTO ai_scenarios (scenario_code, description, executor_bean, security_level, required_params, optional_params, active)
VALUES
    ('BALANCE_CHECK', 'Check account balance', 'balanceCheckExecutor', 'AUTH', '["accountId"]', '[]', true),
    ('PAYMENT_HISTORY', 'View payment history', 'paymentHistoryExecutor', 'AUTH', '["accountId"]', '["fromDate", "toDate", "limit"]', true),
    ('STATEMENT', 'Get account statement', 'statementExecutor', 'AUTH', '["accountId", "month"]', '["format"]', true),
    ('WHATSAPP_BANKING', 'WhatsApp banking service information', 'whatsappBankingExecutor', 'PUBLIC', '[]', '["service"]', true),
    ('CURRENT_ACCOUNT', 'Current account opening information', 'currentAccountExecutor', 'PUBLIC', '[]', '[]', true),
    ('SAVINGS_ACCOUNT', 'Savings account opening information', 'savingsAccountExecutor', 'PUBLIC', '[]', '[]', true),
    ('FIXED_DEPOSIT', 'Fixed deposit information', 'fixedDepositExecutor', 'PUBLIC', '[]', '["amount", "tenure"]', true),
    ('CREDIT_CARD', 'Credit card services information', 'creditCardExecutor', 'PUBLIC', '[]', '["cardType"]', true),
    ('DEBIT_CARD', 'Debit card services information', 'debitCardExecutor', 'PUBLIC', '[]', '[]', true),
    ('API_SUPPORT', 'API integration support information', 'apiSupportExecutor', 'PUBLIC', '[]', '[]', true),
    ('RETAIL_LOAN', 'Retail loan services information', 'retailLoanExecutor', 'PUBLIC', '[]', '["loanType", "amount"]', true),
    ('CORPORATE_BANKING', 'Corporate banking services information', 'corporateBankingExecutor', 'AUTH', '[]', '[]', true),
    ('NEO_BUSINESS', 'Neo for Business information', 'neoBusinessExecutor', 'PUBLIC', '[]', '[]', true),
    ('BATCH_STATUS', 'Check batch processing status', 'batchStatusExecutor', 'AUTH', '["batchId"]', '["date"]', true),
    ('UPLOAD_STATUS', 'Check file upload status', 'uploadStatusExecutor', 'AUTH', '["uploadId"]', '[]', true),
    ('REFUND_STATUS', 'Check refund status', 'refundStatusExecutor', 'AUTH', '["refundId"]', '[]', true),
    ('AMBIGUOUS', 'Ambiguous intent - requires clarification', 'ambiguousExecutor', 'PUBLIC', '[]', '[]', true),
    ('UNKNOWN', 'Unknown intent - fallback handler', 'unknownExecutor', 'PUBLIC', '[]', '[]', true)
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Clear existing role mappings (we'll insert complete set)
DELETE FROM role_scenario_map;

-- USER role: Access to all customer-facing scenarios
INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES
    -- Transaction & Account scenarios
    ('USER', 'TXN_STATUS'),
    ('USER', 'ACCOUNT_SUMMARY'),
    ('USER', 'BALANCE_CHECK'),
    ('USER', 'PAYMENT_HISTORY'),
    ('USER', 'STATEMENT'),
    ('USER', 'REFUND_STATUS'),
    -- Banking Services (Public info that users can access)
    ('USER', 'WHATSAPP_BANKING'),
    ('USER', 'CURRENT_ACCOUNT'),
    ('USER', 'SAVINGS_ACCOUNT'),
    ('USER', 'FIXED_DEPOSIT'),
    ('USER', 'CREDIT_CARD'),
    ('USER', 'DEBIT_CARD'),
    ('USER', 'API_SUPPORT'),
    ('USER', 'RETAIL_LOAN'),
    ('USER', 'NEO_BUSINESS'),
    -- Utility scenarios
    ('USER', 'AMBIGUOUS'),
    ('USER', 'UNKNOWN')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- ADMIN role: Access to all scenarios including operational ones
INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES
    -- All USER scenarios
    ('ADMIN', 'TXN_STATUS'),
    ('ADMIN', 'ACCOUNT_SUMMARY'),
    ('ADMIN', 'BALANCE_CHECK'),
    ('ADMIN', 'PAYMENT_HISTORY'),
    ('ADMIN', 'STATEMENT'),
    ('ADMIN', 'REFUND_STATUS'),
    ('ADMIN', 'WHATSAPP_BANKING'),
    ('ADMIN', 'CURRENT_ACCOUNT'),
    ('ADMIN', 'SAVINGS_ACCOUNT'),
    ('ADMIN', 'FIXED_DEPOSIT'),
    ('ADMIN', 'CREDIT_CARD'),
    ('ADMIN', 'DEBIT_CARD'),
    ('ADMIN', 'API_SUPPORT'),
    ('ADMIN', 'RETAIL_LOAN'),
    ('ADMIN', 'NEO_BUSINESS'),
    -- Operational scenarios
    ('ADMIN', 'FILE_STATUS'),
    ('ADMIN', 'BATCH_STATUS'),
    ('ADMIN', 'UPLOAD_STATUS'),
    -- Corporate scenarios
    ('ADMIN', 'CORPORATE_BANKING'),
    -- Utility scenarios
    ('ADMIN', 'AMBIGUOUS'),
    ('ADMIN', 'UNKNOWN')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- OPERATOR role: Access to operational scenarios only
INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES
    ('OPERATOR', 'FILE_STATUS'),
    ('OPERATOR', 'BATCH_STATUS'),
    ('OPERATOR', 'UPLOAD_STATUS'),
    ('OPERATOR', 'AMBIGUOUS'),
    ('OPERATOR', 'UNKNOWN')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- GUEST role: Access to public information only
INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES
    ('GUEST', 'WHATSAPP_BANKING'),
    ('GUEST', 'CURRENT_ACCOUNT'),
    ('GUEST', 'SAVINGS_ACCOUNT'),
    ('GUEST', 'FIXED_DEPOSIT'),
    ('GUEST', 'CREDIT_CARD'),
    ('GUEST', 'DEBIT_CARD'),
    ('GUEST', 'API_SUPPORT'),
    ('GUEST', 'RETAIL_LOAN'),
    ('GUEST', 'NEO_BUSINESS'),
    ('GUEST', 'AMBIGUOUS'),
    ('GUEST', 'UNKNOWN')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);


