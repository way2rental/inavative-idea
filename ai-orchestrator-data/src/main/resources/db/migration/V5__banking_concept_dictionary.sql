-- =====================================================================
-- Banking Concept Dictionary
-- Version 5.0.0 - Banking Domain Concepts for Enhanced Understanding
-- =====================================================================
-- NO HARDCODING - All banking concepts from database
-- Fully configurable and manageable from Admin Panel
-- =====================================================================

-- ============================================
-- BANKING CONCEPT DICTIONARY
-- ============================================
CREATE TABLE IF NOT EXISTS ai_concept_dictionary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    concept_code VARCHAR(100) NOT NULL UNIQUE,  -- ACCOUNT_BALANCE, CREDIT_CARD, etc.
    concept_name VARCHAR(200) NOT NULL,          -- "Account Balance", "Credit Card"
    parent_concept_code VARCHAR(100),           -- ACCOUNT (for hierarchies)
    concept_type VARCHAR(50),                  -- ACCOUNT, TRANSACTION, PRODUCT, SERVICE, etc.
    synonyms TEXT,                              -- JSON array: ["balance", "amount", "funds", "CC", "credit card"]
    description TEXT,                           -- Concept description
    related_concepts JSON,                      -- JSON array of related concept codes
    priority INT DEFAULT 0,                     -- Matching priority
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_concept_code (concept_code),
    INDEX idx_parent_concept (parent_concept_code),
    INDEX idx_concept_type (concept_type),
    INDEX idx_active_priority (active, priority DESC),
    FOREIGN KEY (parent_concept_code) REFERENCES ai_concept_dictionary(concept_code) ON DELETE SET NULL
);

-- ============================================
-- INSERT DEFAULT BANKING CONCEPTS
-- ============================================
INSERT INTO ai_concept_dictionary (concept_code, concept_name, parent_concept_code, concept_type, synonyms, description, priority, active) VALUES
-- Account Concepts
('ACCOUNT', 'Account', NULL, 'ACCOUNT', '["account", "acct", "acc"]', 'Banking account', 100, TRUE),
('ACCOUNT_BALANCE', 'Account Balance', 'ACCOUNT', 'ACCOUNT', '["balance", "amount", "funds", "available balance", "current balance"]', 'Account balance information', 90, TRUE),
('ACCOUNT_SUMMARY', 'Account Summary', 'ACCOUNT', 'ACCOUNT', '["summary", "account summary", "account overview", "account details"]', 'Account summary information', 85, TRUE),
('SAVINGS_ACCOUNT', 'Savings Account', 'ACCOUNT', 'ACCOUNT', '["savings", "savings account", "SA", "savings acct"]', 'Savings account type', 80, TRUE),
('CHECKING_ACCOUNT', 'Checking Account', 'ACCOUNT', 'ACCOUNT', '["checking", "checking account", "current account", "CA"]', 'Checking account type', 80, TRUE),
('CURRENT_ACCOUNT', 'Current Account', 'ACCOUNT', 'ACCOUNT', '["current", "current account", "checking"]', 'Current account type', 80, TRUE),

-- Card Concepts
('CREDIT_CARD', 'Credit Card', NULL, 'CARD', '["credit card", "CC", "credit", "card", "creditcard"]', 'Credit card', 95, TRUE),
('DEBIT_CARD', 'Debit Card', NULL, 'CARD', '["debit card", "DC", "debit", "ATM card"]', 'Debit card', 90, TRUE),
('CARD_STATEMENT', 'Card Statement', 'CREDIT_CARD', 'CARD', '["statement", "card statement", "credit card statement", "CC statement", "bill"]', 'Credit card statement', 85, TRUE),
('CARD_BALANCE', 'Card Balance', 'CREDIT_CARD', 'CARD', '["card balance", "credit balance", "outstanding", "due amount"]', 'Credit card balance', 85, TRUE),

-- Transaction Concepts
('TRANSACTION', 'Transaction', NULL, 'TRANSACTION', '["transaction", "txn", "tx", "payment", "transfer"]', 'Banking transaction', 95, TRUE),
('TRANSACTION_HISTORY', 'Transaction History', 'TRANSACTION', 'TRANSACTION', '["history", "transactions", "transaction history", "statement", "activity"]', 'Transaction history', 90, TRUE),
('TRANSACTION_TYPE', 'Transaction Type', 'TRANSACTION', 'TRANSACTION', '["type", "transaction type", "debit", "credit", "transfer"]', 'Transaction type', 80, TRUE),
('DEBIT_TRANSACTION', 'Debit Transaction', 'TRANSACTION', 'TRANSACTION', '["debit", "withdrawal", "deduction", "payment"]', 'Debit transaction', 75, TRUE),
('CREDIT_TRANSACTION', 'Credit Transaction', 'TRANSACTION', 'TRANSACTION', '["credit", "deposit", "addition", "income"]', 'Credit transaction', 75, TRUE),

-- Statement Concepts
('STATEMENT', 'Statement', NULL, 'STATEMENT', '["statement", "bank statement", "account statement", "monthly statement"]', 'Bank statement', 90, TRUE),
('BILL_STATEMENT', 'Bill Statement', 'STATEMENT', 'STATEMENT', '["bill", "bill statement", "invoice", "credit card bill"]', 'Bill statement', 85, TRUE),

-- Loan Concepts
('LOAN', 'Loan', NULL, 'LOAN', '["loan", "borrowing", "credit"]', 'Loan product', 85, TRUE),
('LOAN_STATUS', 'Loan Status', 'LOAN', 'LOAN', '["loan status", "loan details", "loan information", "outstanding loan"]', 'Loan status information', 80, TRUE),
('LOAN_BALANCE', 'Loan Balance', 'LOAN', 'LOAN', '["loan balance", "outstanding amount", "remaining balance"]', 'Outstanding loan balance', 80, TRUE),

-- Transfer Concepts
('FUND_TRANSFER', 'Fund Transfer', NULL, 'TRANSFER', '["transfer", "fund transfer", "money transfer", "send money"]', 'Fund transfer', 90, TRUE),
('TRANSFER_HISTORY', 'Transfer History', 'FUND_TRANSFER', 'TRANSFER', '["transfer history", "transfer list", "transfers"]', 'Transfer history', 85, TRUE),

-- Payment Concepts
('BILL_PAYMENT', 'Bill Payment', NULL, 'PAYMENT', '["bill payment", "pay bill", "payment", "pay"]', 'Bill payment', 90, TRUE),
('PAYMENT_HISTORY', 'Payment History', 'BILL_PAYMENT', 'PAYMENT', '["payment history", "payments", "paid bills"]', 'Payment history', 85, TRUE),

-- Investment Concepts
('INVESTMENT', 'Investment', NULL, 'INVESTMENT', '["investment", "portfolio", "investments", "stocks", "mutual funds"]', 'Investment products', 80, TRUE),
('INVESTMENT_PORTFOLIO', 'Investment Portfolio', 'INVESTMENT', 'INVESTMENT', '["portfolio", "investment portfolio", "holdings", "investments"]', 'Investment portfolio', 85, TRUE),

-- Date/Time Concepts
('DATE_RANGE', 'Date Range', NULL, 'DATE', '["date range", "period", "from", "to", "between", "last month", "this month"]', 'Date range for queries', 70, TRUE),
('LAST_MONTH', 'Last Month', 'DATE_RANGE', 'DATE', '["last month", "previous month", "past month"]', 'Last month period', 65, TRUE),
('THIS_MONTH', 'This Month', 'DATE_RANGE', 'DATE', '["this month", "current month"]', 'This month period', 65, TRUE),
('LAST_WEEK', 'Last Week', 'DATE_RANGE', 'DATE', '["last week", "previous week", "past week"]', 'Last week period', 65, TRUE),

-- Amount Concepts
('AMOUNT', 'Amount', NULL, 'AMOUNT', '["amount", "value", "sum", "total"]', 'Monetary amount', 70, TRUE),
('MIN_AMOUNT', 'Minimum Amount', 'AMOUNT', 'AMOUNT', '["minimum", "min", "above", "more than", "over"]', 'Minimum amount filter', 65, TRUE),
('MAX_AMOUNT', 'Maximum Amount', 'AMOUNT', 'AMOUNT', '["maximum", "max", "below", "less than", "under"]', 'Maximum amount filter', 65, TRUE),

-- General Banking Concepts
('BALANCE', 'Balance', NULL, 'GENERAL', '["balance", "amount", "funds", "available"]', 'Account balance', 95, TRUE),
('INTEREST', 'Interest', NULL, 'GENERAL', '["interest", "rate", "interest rate"]', 'Interest rate', 75, TRUE),
('CREDIT_SCORE', 'Credit Score', NULL, 'GENERAL', '["credit score", "credit rating", "CIBIL", "credit"]', 'Credit score', 80, TRUE),
('SPENDING_ANALYSIS', 'Spending Analysis', NULL, 'ANALYSIS', '["spending", "analysis", "expenses", "expenditure", "spend analysis"]', 'Spending analysis', 85, TRUE);
