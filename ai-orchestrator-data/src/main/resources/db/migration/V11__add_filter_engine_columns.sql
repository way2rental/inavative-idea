-- V11: Add multi-filter engine columns to ai_scenarios
-- This enables dynamic WHERE clause generation based on AI-extracted filters

-- Add filter_definitions column (JSON array of filter configurations)
-- Each filter has: name, displayName, description, type, dbColumn, operator, mandatory, defaultValue, validationPattern, enumValues
ALTER TABLE ai_scenarios ADD COLUMN IF NOT EXISTS filter_definitions JSON DEFAULT NULL;

-- Add security_filters column (JSON object for RLS configuration)
-- Structure: { "userLevel": {"dbColumn": "user_id", "contextKey": "userId"}, ... }
ALTER TABLE ai_scenarios ADD COLUMN IF NOT EXISTS security_filters JSON DEFAULT NULL;

-- Add max_results for pagination (prevents memory exhaustion)
ALTER TABLE ai_scenarios ADD COLUMN IF NOT EXISTS max_results INT DEFAULT 100;

-- Add default_sort for consistent ordering
ALTER TABLE ai_scenarios ADD COLUMN IF NOT EXISTS default_sort VARCHAR(255) DEFAULT NULL;

-- Example filter_definitions for TRANSACTION_HISTORY:
-- [
--   {"name": "accountId", "displayName": "Account ID", "type": "STRING", 
--    "dbColumn": "account_id", "operator": "=", "mandatory": true,
--    "validationPattern": "^ACC[0-9]+$", "validationError": "Account ID must start with ACC"},
--   {"name": "dateFrom", "displayName": "Start Date", "type": "DATE",
--    "dbColumn": "transaction_date", "operator": ">=", "mandatory": false},
--   {"name": "dateTo", "displayName": "End Date", "type": "DATE",
--    "dbColumn": "transaction_date", "operator": "<=", "mandatory": false},
--   {"name": "minAmount", "displayName": "Minimum Amount", "type": "DECIMAL",
--    "dbColumn": "amount", "operator": ">=", "mandatory": false},
--   {"name": "transactionType", "displayName": "Transaction Type", "type": "ENUM",
--    "dbColumn": "txn_type", "operator": "=", "mandatory": false,
--    "enumValues": ["CREDIT", "DEBIT", "TRANSFER"]}
-- ]

-- Update existing TRANSACTION_HISTORY scenario with filter definitions
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Bank account identifier (e.g., ACC001)"},
  {"name": "dateFrom", "displayName": "Start Date", "type": "DATE", "dbColumn": "transaction_date", "operator": ">=", "mandatory": false, "description": "Filter transactions from this date"},
  {"name": "dateTo", "displayName": "End Date", "type": "DATE", "dbColumn": "transaction_date", "operator": "<=", "mandatory": false, "description": "Filter transactions up to this date"},
  {"name": "minAmount", "displayName": "Minimum Amount", "type": "DECIMAL", "dbColumn": "amount", "operator": ">=", "mandatory": false, "description": "Minimum transaction amount"},
  {"name": "maxAmount", "displayName": "Maximum Amount", "type": "DECIMAL", "dbColumn": "amount", "operator": "<=", "mandatory": false, "description": "Maximum transaction amount"},
  {"name": "transactionType", "displayName": "Transaction Type", "type": "ENUM", "dbColumn": "txn_type", "operator": "=", "mandatory": false, "enumValues": ["CREDIT", "DEBIT", "TRANSFER"], "description": "Type of transaction"}
]',
max_results = 50,
default_sort = 'transaction_date DESC'
WHERE scenario_code = 'TRANSACTION_HISTORY';

-- Update ACCOUNT_BALANCE scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Bank account identifier (e.g., ACC001)"}
]',
max_results = 1
WHERE scenario_code = 'ACCOUNT_BALANCE';

-- Update ACCOUNT_SUMMARY scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Bank account identifier (e.g., ACC001)"}
]',
max_results = 1
WHERE scenario_code = 'ACCOUNT_SUMMARY';

-- Update PAYMENT_HISTORY scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Bank account identifier"},
  {"name": "dateFrom", "displayName": "From Date", "type": "DATE", "dbColumn": "payment_date", "operator": ">=", "mandatory": false},
  {"name": "dateTo", "displayName": "To Date", "type": "DATE", "dbColumn": "payment_date", "operator": "<=", "mandatory": false},
  {"name": "paymentStatus", "displayName": "Status", "type": "ENUM", "dbColumn": "status", "operator": "=", "mandatory": false, "enumValues": ["PENDING", "COMPLETED", "FAILED"]}
]',
max_results = 50,
default_sort = 'payment_date DESC'
WHERE scenario_code = 'PAYMENT_HISTORY';

-- Update FUND_TRANSFER scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "fromAccountId", "displayName": "From Account", "type": "STRING", "dbColumn": "from_account", "operator": "=", "mandatory": true, "description": "Source account ID"},
  {"name": "toAccountId", "displayName": "To Account", "type": "STRING", "dbColumn": "to_account", "operator": "=", "mandatory": true, "description": "Destination account ID"},
  {"name": "amount", "displayName": "Amount", "type": "DECIMAL", "dbColumn": "amount", "operator": "=", "mandatory": true, "description": "Transfer amount"}
]'
WHERE scenario_code = 'FUND_TRANSFER';

-- Update CARD_DETAILS scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Bank account linked to card"}
]',
max_results = 5
WHERE scenario_code = 'CARD_DETAILS';

-- Update BILL_PAYMENT scenario
UPDATE ai_scenarios 
SET filter_definitions = '[
  {"name": "accountId", "displayName": "Account ID", "type": "STRING", "dbColumn": "account_id", "operator": "=", "mandatory": true, "description": "Account to pay from"},
  {"name": "billerId", "displayName": "Biller ID", "type": "STRING", "dbColumn": "biller_id", "operator": "=", "mandatory": true, "description": "Biller identifier"},
  {"name": "amount", "displayName": "Amount", "type": "DECIMAL", "dbColumn": "amount", "operator": "=", "mandatory": true, "description": "Bill amount"}
]'
WHERE scenario_code = 'BILL_PAYMENT';
