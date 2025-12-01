-- ============================================================================
-- Sample Response Mappings Configuration
-- Per ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md
-- ============================================================================

-- ============================================================================
-- 1. TXN_STATUS - Transaction Status Check
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('TXN_STATUS', 'DB_QUERY', 'txn_id', 'txnId', '$.txn_id', 'NONE', 1, true),
  ('TXN_STATUS', 'DB_QUERY', 'status_code', 'status', '$.status_code', 'NONE', 2, true),
  ('TXN_STATUS', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 3, true),
  ('TXN_STATUS', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 4, true),
  ('TXN_STATUS', 'DB_QUERY', 'created_at', 'timestamp', '$.created_at', 'NONE', 5, true),
  ('TXN_STATUS', 'DB_QUERY', 'beneficiary_name', 'beneficiaryName', '$.beneficiary_name', 'NONE', 6, true);

-- ============================================================================
-- 2. ACCOUNT_BALANCE - Account Balance Inquiry
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('ACCOUNT_BALANCE', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 1, true),
  ('ACCOUNT_BALANCE', 'DB_QUERY', 'account_type', 'accountType', '$.account_type', 'NONE', 2, true),
  ('ACCOUNT_BALANCE', 'DB_QUERY', 'balance', 'balance', '$.balance', 'NONE', 3, true),
  ('ACCOUNT_BALANCE', 'DB_QUERY', 'currency', 'currency', '$.currency', 'NONE', 4, true),
  ('ACCOUNT_BALANCE', 'DB_QUERY', 'last_updated', 'lastUpdated', '$.last_updated', 'NONE', 5, true);

-- ============================================================================
-- 3. RECENT_TRANSACTIONS - Recent Transactions List
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'txn_id', 'txnId', '$.txn_id', 'NONE', 1, true),
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'txn_date', 'date', '$.txn_date', 'NONE', 2, true),
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'txn_type', 'type', '$.txn_type', 'NONE', 3, true),
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'amount', 'amount', '$.amount', 'NONE', 4, true),
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'description', 'description', '$.description', 'NONE', 5, true),
  ('RECENT_TRANSACTIONS', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 6, true);

-- ============================================================================
-- 4. FILE_STATUS - File Upload Status
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('FILE_STATUS', 'DB_QUERY', 'file_id', 'fileId', '$.file_id', 'NONE', 1, true),
  ('FILE_STATUS', 'DB_QUERY', 'file_name', 'fileName', '$.file_name', 'NONE', 2, true),
  ('FILE_STATUS', 'DB_QUERY', 'file_type', 'fileType', '$.file_type', 'NONE', 3, true),
  ('FILE_STATUS', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 4, true),
  ('FILE_STATUS', 'DB_QUERY', 'upload_date', 'uploadDate', '$.upload_date', 'NONE', 5, true),
  ('FILE_STATUS', 'DB_QUERY', 'processed_rows', 'processedRows', '$.processed_rows', 'NONE', 6, true),
  ('FILE_STATUS', 'DB_QUERY', 'error_rows', 'errorRows', '$.error_rows', 'NONE', 7, true);

-- ============================================================================
-- 5. CUSTOMER_INFO - Customer Information (with PII masking)
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('CUSTOMER_INFO', 'DB_QUERY', 'customer_id', 'customerId', '$.customer_id', 'NONE', 1, true),
  ('CUSTOMER_INFO', 'DB_QUERY', 'customer_name', 'customerName', '$.customer_name', 'NONE', 2, true),
  ('CUSTOMER_INFO', 'DB_QUERY', 'email', 'email', '$.email', 'EMAIL', 3, true),
  ('CUSTOMER_INFO', 'DB_QUERY', 'phone', 'phone', '$.phone', 'PHONE', 4, true),
  ('CUSTOMER_INFO', 'DB_QUERY', 'pan_number', 'panNumber', '$.pan_number', 'PAN', 5, true),
  ('CUSTOMER_INFO', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 6, true);

-- ============================================================================
-- 6. CARD_DETAILS - Card Information (with sensitive masking)
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('CARD_DETAILS', 'DB_QUERY', 'card_number', 'cardNumber', '$.card_number', 'CARD', 1, true),
  ('CARD_DETAILS', 'DB_QUERY', 'card_type', 'cardType', '$.card_type', 'NONE', 2, true),
  ('CARD_DETAILS', 'DB_QUERY', 'cardholder_name', 'cardholderName', '$.cardholder_name', 'NONE', 3, true),
  ('CARD_DETAILS', 'DB_QUERY', 'expiry_date', 'expiryDate', '$.expiry_date', 'NONE', 4, true),
  ('CARD_DETAILS', 'DB_QUERY', 'card_status', 'status', '$.card_status', 'NONE', 5, true),
  ('CARD_DETAILS', 'DB_QUERY', 'credit_limit', 'creditLimit', '$.credit_limit', 'NONE', 6, true),
  ('CARD_DETAILS', 'DB_QUERY', 'available_credit', 'availableCredit', '$.available_credit', 'NONE', 7, true);

-- ============================================================================
-- 7. LOAN_STATUS - Loan Status Inquiry
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('LOAN_STATUS', 'DB_QUERY', 'loan_id', 'loanId', '$.loan_id', 'NONE', 1, true),
  ('LOAN_STATUS', 'DB_QUERY', 'loan_type', 'loanType', '$.loan_type', 'NONE', 2, true),
  ('LOAN_STATUS', 'DB_QUERY', 'loan_amount', 'loanAmount', '$.loan_amount', 'NONE', 3, true),
  ('LOAN_STATUS', 'DB_QUERY', 'outstanding_amount', 'outstandingAmount', '$.outstanding_amount', 'NONE', 4, true),
  ('LOAN_STATUS', 'DB_QUERY', 'next_emi_date', 'nextEmiDate', '$.next_emi_date', 'NONE', 5, true),
  ('LOAN_STATUS', 'DB_QUERY', 'emi_amount', 'emiAmount', '$.emi_amount', 'NONE', 6, true),
  ('LOAN_STATUS', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 7, true);

-- ============================================================================
-- 8. BENEFICIARY_LIST - Registered Beneficiaries
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('BENEFICIARY_LIST', 'DB_QUERY', 'beneficiary_id', 'beneficiaryId', '$.beneficiary_id', 'NONE', 1, true),
  ('BENEFICIARY_LIST', 'DB_QUERY', 'beneficiary_name', 'beneficiaryName', '$.beneficiary_name', 'NONE', 2, true),
  ('BENEFICIARY_LIST', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 3, true),
  ('BENEFICIARY_LIST', 'DB_QUERY', 'bank_name', 'bankName', '$.bank_name', 'NONE', 4, true),
  ('BENEFICIARY_LIST', 'DB_QUERY', 'ifsc_code', 'ifscCode', '$.ifsc_code', 'NONE', 5, true),
  ('BENEFICIARY_LIST', 'DB_QUERY', 'registration_date', 'registrationDate', '$.registration_date', 'NONE', 6, true);

-- ============================================================================
-- 9. STATEMENT_SUMMARY - Account Statement Summary
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 1, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'from_date', 'fromDate', '$.from_date', 'NONE', 2, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'to_date', 'toDate', '$.to_date', 'NONE', 3, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'opening_balance', 'openingBalance', '$.opening_balance', 'NONE', 4, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'closing_balance', 'closingBalance', '$.closing_balance', 'NONE', 5, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'total_credits', 'totalCredits', '$.total_credits', 'NONE', 6, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'total_debits', 'totalDebits', '$.total_debits', 'NONE', 7, true),
  ('STATEMENT_SUMMARY', 'DB_QUERY', 'transaction_count', 'transactionCount', '$.transaction_count', 'NONE', 8, true);

-- ============================================================================
-- 10. CHEQUE_STATUS - Cheque Book Status
-- ============================================================================
INSERT INTO ai_response_mappings (scenario_code, source_type, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('CHEQUE_STATUS', 'DB_QUERY', 'cheque_request_id', 'requestId', '$.cheque_request_id', 'NONE', 1, true),
  ('CHEQUE_STATUS', 'DB_QUERY', 'request_date', 'requestDate', '$.request_date', 'NONE', 2, true),
  ('CHEQUE_STATUS', 'DB_QUERY', 'status', 'status', '$.status', 'NONE', 3, true),
  ('CHEQUE_STATUS', 'DB_QUERY', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 4, true),
  ('CHEQUE_STATUS', 'DB_QUERY', 'delivery_address', 'deliveryAddress', '$.delivery_address', 'NONE', 5, true),
  ('CHEQUE_STATUS', 'DB_QUERY', 'expected_delivery_date', 'expectedDeliveryDate', '$.expected_delivery_date', 'NONE', 6, true);

-- ============================================================================
-- Verification Query: Check all configured scenarios
-- ============================================================================
-- SELECT scenario_code, COUNT(*) as mapping_count
-- FROM ai_response_mappings
-- WHERE active = true
-- GROUP BY scenario_code
-- ORDER BY scenario_code;

-- ============================================================================
-- Query to view mappings for a specific scenario
-- ============================================================================
-- SELECT scenario_code, source_field, target_field, json_path, masking_type, display_order
-- FROM ai_response_mappings
-- WHERE scenario_code = 'TXN_STATUS'
--   AND active = true
-- ORDER BY display_order;

