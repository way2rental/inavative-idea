-- Create Sample Training Data for ML Models
-- This script creates labeled training data for Intent Classification and NER

-- Insert Intent Classification Training Data
-- Each record contains: user query + scenario code (label)

INSERT INTO ai_training_data (user_query, scenario_code, predicted_scenario, predicted_confidence, user_feedback, labeled_by, labeled_at, used_for_training, created_at, updated_at)
VALUES
-- Account Balance queries
('What is my account balance?', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('Show me my balance', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.92, true, 'admin', NOW(), false, NOW(), NOW()),
('How much money do I have?', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),
('Check my account balance for account ACC001', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('What is the balance of my account?', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('I want to see my account balance', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.85, true, 'admin', NOW(), false, NOW(), NOW()),
('Display account balance', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),
('Get my balance', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.87, true, 'admin', NOW(), false, NOW(), NOW()),

-- Transaction History queries
('Show me my transaction history', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('List all my transactions', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.91, true, 'admin', NOW(), false, NOW(), NOW()),
('What are my recent transactions?', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.89, true, 'admin', NOW(), false, NOW(), NOW()),
('Get transaction history for account ACC001', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Show transactions from last month', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('I need to see my transaction list', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.86, true, 'admin', NOW(), false, NOW(), NOW()),
('Display my transaction history', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),
('Get all transactions', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.87, true, 'admin', NOW(), false, NOW(), NOW()),

-- Transaction Status queries
('What is the status of transaction TXN123?', 'TXN_STATUS', 'TXN_STATUS', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('Check status of transaction TXN123', 'TXN_STATUS', 'TXN_STATUS', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('Is transaction TXN123 completed?', 'TXN_STATUS', 'TXN_STATUS', 0.91, true, 'admin', NOW(), false, NOW(), NOW()),
('Get transaction status for TXN123', 'TXN_STATUS', 'TXN_STATUS', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Show me the status of my transaction', 'TXN_STATUS', 'TXN_STATUS', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('What is the current status of transaction TXN123?', 'TXN_STATUS', 'TXN_STATUS', 0.92, true, 'admin', NOW(), false, NOW(), NOW()),
('Check if transaction TXN123 is processed', 'TXN_STATUS', 'TXN_STATUS', 0.89, true, 'admin', NOW(), false, NOW(), NOW()),
('Transaction status for TXN123', 'TXN_STATUS', 'TXN_STATUS', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),

-- Account Summary queries
('Show me my account summary', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.92, true, 'admin', NOW(), false, NOW(), NOW()),
('Get account summary for ACC001', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('I want to see my account summary', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('Display account summary', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),
('What is my account summary?', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.87, true, 'admin', NOW(), false, NOW(), NOW()),
('Get summary of my account', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.89, true, 'admin', NOW(), false, NOW(), NOW()),
('Show account overview', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.85, true, 'admin', NOW(), false, NOW(), NOW()),
('Account summary please', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.86, true, 'admin', NOW(), false, NOW(), NOW()),

-- Payment queries
('Make a payment of 1000 to account ACC002', 'PAYMENT', 'PAYMENT', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('I want to pay 500 to ACC002', 'PAYMENT', 'PAYMENT', 0.91, true, 'admin', NOW(), false, NOW(), NOW()),
('Transfer 2000 to account ACC002', 'PAYMENT', 'PAYMENT', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Send payment of 1500', 'PAYMENT', 'PAYMENT', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('Pay 3000 to ACC002', 'PAYMENT', 'PAYMENT', 0.92, true, 'admin', NOW(), false, NOW(), NOW()),
('I need to make a payment', 'PAYMENT', 'PAYMENT', 0.86, true, 'admin', NOW(), false, NOW(), NOW()),
('Process payment of 1000', 'PAYMENT', 'PAYMENT', 0.89, true, 'admin', NOW(), false, NOW(), NOW()),
('Initiate payment to ACC002', 'PAYMENT', 'PAYMENT', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),

-- More variations for better training
('Balance check', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.82, true, 'admin', NOW(), false, NOW(), NOW()),
('My balance', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.80, true, 'admin', NOW(), false, NOW(), NOW()),
('Account balance ACC001', 'ACCOUNT_BALANCE', 'ACCOUNT_BALANCE', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('Transactions', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.78, true, 'admin', NOW(), false, NOW(), NOW()),
('My transactions', 'TRANSACTION_HISTORY', 'TRANSACTION_HISTORY', 0.81, true, 'admin', NOW(), false, NOW(), NOW()),
('TXN123 status', 'TXN_STATUS', 'TXN_STATUS', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('Status TXN123', 'TXN_STATUS', 'TXN_STATUS', 0.85, true, 'admin', NOW(), false, NOW(), NOW()),
('Summary', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.75, true, 'admin', NOW(), false, NOW(), NOW()),
('Account overview', 'ACCOUNT_SUMMARY', 'ACCOUNT_SUMMARY', 0.83, true, 'admin', NOW(), false, NOW(), NOW()),
('Pay 1000', 'PAYMENT', 'PAYMENT', 0.84, true, 'admin', NOW(), false, NOW(), NOW()),
('Transfer money', 'PAYMENT', 'PAYMENT', 0.79, true, 'admin', NOW(), false, NOW(), NOW());

-- Insert NER Training Data (with entity labels)
-- Each record contains: user query + entities JSON (label)

INSERT INTO ai_training_data (user_query, entities, predicted_scenario, predicted_confidence, user_feedback, labeled_by, labeled_at, used_for_training, created_at, updated_at)
VALUES
-- Account ID entities
('Show balance for account ACC001', '{"ACCOUNT_ID": "ACC001"}', 'ACCOUNT_BALANCE', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('Get transactions for ACC002', '{"ACCOUNT_ID": "ACC002"}', 'TRANSACTION_HISTORY', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('Account summary for ACC001', '{"ACCOUNT_ID": "ACC001"}', 'ACCOUNT_SUMMARY', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Balance of ACC003', '{"ACCOUNT_ID": "ACC003"}', 'ACCOUNT_BALANCE', 0.92, true, 'admin', NOW(), false, NOW(), NOW()),

-- Transaction ID entities
('Status of transaction TXN123', '{"TXN_ID": "TXN123"}', 'TXN_STATUS', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('Check TXN456 status', '{"TXN_ID": "TXN456"}', 'TXN_STATUS', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),
('Transaction TXN789 details', '{"TXN_ID": "TXN789"}', 'TXN_STATUS', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),

-- Amount entities
('Pay 1000 to ACC002', '{"AMOUNT": "1000", "ACCOUNT_ID": "ACC002"}', 'PAYMENT', 0.95, true, 'admin', NOW(), false, NOW(), NOW()),
('Transfer 5000 to ACC003', '{"AMOUNT": "5000", "ACCOUNT_ID": "ACC003"}', 'PAYMENT', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Send 2500 to account ACC001', '{"AMOUNT": "2500", "ACCOUNT_ID": "ACC001"}', 'PAYMENT', 0.93, true, 'admin', NOW(), false, NOW(), NOW()),

-- Date entities
('Transactions from 2024-01-01', '{"DATE": "2024-01-01"}', 'TRANSACTION_HISTORY', 0.90, true, 'admin', NOW(), false, NOW(), NOW()),
('Show transactions since January 1st', '{"DATE": "2024-01-01"}', 'TRANSACTION_HISTORY', 0.88, true, 'admin', NOW(), false, NOW(), NOW()),
('Balance on 2024-12-31', '{"DATE": "2024-12-31"}', 'ACCOUNT_BALANCE', 0.87, true, 'admin', NOW(), false, NOW(), NOW()),

-- Combined entities
('Pay 1000 to ACC002 on 2024-01-15', '{"AMOUNT": "1000", "ACCOUNT_ID": "ACC002", "DATE": "2024-01-15"}', 'PAYMENT', 0.96, true, 'admin', NOW(), false, NOW(), NOW()),
('Transaction TXN123 for account ACC001', '{"TXN_ID": "TXN123", "ACCOUNT_ID": "ACC001"}', 'TXN_STATUS', 0.94, true, 'admin', NOW(), false, NOW(), NOW()),
('Transactions for ACC001 from 2024-01-01', '{"ACCOUNT_ID": "ACC001", "DATE": "2024-01-01"}', 'TRANSACTION_HISTORY', 0.93, true, 'admin', NOW(), false, NOW(), NOW());

-- Update statistics
SELECT 
    COUNT(*) as total_training_data,
    COUNT(CASE WHEN scenario_code IS NOT NULL THEN 1 END) as intent_data,
    COUNT(CASE WHEN entities IS NOT NULL THEN 1 END) as ner_data,
    COUNT(CASE WHEN labeled_at IS NOT NULL THEN 1 END) as labeled_data
FROM ai_training_data;
