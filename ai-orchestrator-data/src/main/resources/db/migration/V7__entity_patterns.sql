-- =====================================================================
-- Entity Patterns Configuration
-- Version 7.0.0 - Entity Extraction Patterns
-- =====================================================================
-- NO HARDCODING - All entity patterns from database
-- Fully configurable and manageable from Admin Panel
-- =====================================================================

-- ============================================
-- ENTITY PATTERNS
-- ============================================
-- Add entity patterns for common banking entities
-- These patterns enable automatic entity extraction from user queries

-- ACCOUNT_ID Pattern: Matches account identifiers like ACC001, ACC002, etc.
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'ACCOUNT_ID',
    'REGEX',
    '\\bACC[0-9]+\\b',
    'Account ID',
    'Bank account identifier starting with ACC followed by digits (e.g., ACC001, ACC123)',
    '["ACC001", "ACC002", "ACC123", "ACC456"]',
    0.85,
    10,
    TRUE,
    '{"caseSensitive": false, "minLength": 5, "maxLength": 20}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- TRANSACTION_ID Pattern: Matches transaction identifiers like TXN001, TXN123, etc.
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'TRANSACTION_ID',
    'REGEX',
    '\\bTXN[0-9]+\\b',
    'Transaction ID',
    'Transaction identifier starting with TXN followed by digits (e.g., TXN001, TXN123)',
    '["TXN001", "TXN002", "TXN123"]',
    0.85,
    9,
    TRUE,
    '{"caseSensitive": false, "minLength": 5, "maxLength": 20}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- CARD_ID Pattern: Matches card identifiers like CARD001, CARD123, etc.
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'CARD_ID',
    'REGEX',
    '\\bCARD[0-9]+\\b',
    'Card ID',
    'Card identifier starting with CARD followed by digits (e.g., CARD001, CARD123)',
    '["CARD001", "CARD002", "CARD123"]',
    0.85,
    9,
    TRUE,
    '{"caseSensitive": false, "minLength": 6, "maxLength": 20}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- LOAN_ID Pattern: Matches loan identifiers like LOAN001, LOAN123, etc.
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'LOAN_ID',
    'REGEX',
    '\\bLOAN[0-9]+\\b',
    'Loan ID',
    'Loan identifier starting with LOAN followed by digits (e.g., LOAN001, LOAN123)',
    '["LOAN001", "LOAN002", "LOAN123"]',
    0.85,
    9,
    TRUE,
    '{"caseSensitive": false, "minLength": 6, "maxLength": 20}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- AMOUNT Pattern: Matches currency amounts like ₹1000, 5000, $100, etc.
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'AMOUNT',
    'REGEX',
    '\\b[₹$]?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?\\b',
    'Amount',
    'Currency amount with optional currency symbol (e.g., ₹1000, 5000, $100.50)',
    '["₹1000", "5000", "$100.50", "1,00,000"]',
    0.80,
    8,
    TRUE,
    '{"caseSensitive": false, "currencySymbols": ["₹", "$", "USD", "INR"]}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- DATE Pattern: Matches dates in various formats
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'DATE',
    'REGEX',
    '\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4})\\b',
    'Date',
    'Date in various formats (e.g., 01/01/2024, 01-01-2024, January 1, 2024)',
    '["01/01/2024", "01-01-2024", "January 1, 2024", "Dec 31, 2023"]',
    0.75,
    7,
    TRUE,
    '{"caseSensitive": false, "dateFormats": ["MM/DD/YYYY", "DD-MM-YYYY", "Month DD, YYYY"]}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- PHONE_NUMBER Pattern: Matches phone numbers
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'PHONE_NUMBER',
    'REGEX',
    '\\b[+]?\\d{1,4}[-\\s]?\\d{1,4}[-\\s]?\\d{1,9}\\b',
    'Phone Number',
    'Phone number in various formats (e.g., +91-1234567890, 123-456-7890)',
    '["+91-1234567890", "123-456-7890", "1234567890"]',
    0.75,
    6,
    TRUE,
    '{"caseSensitive": false, "minLength": 10, "maxLength": 15}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;

-- EMAIL Pattern: Matches email addresses
INSERT INTO ai_entity_patterns (
    entity_type,
    pattern_type,
    pattern_definition,
    display_name,
    description,
    examples,
    confidence_boost,
    priority,
    active,
    config_json
) VALUES (
    'EMAIL',
    'REGEX',
    '\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b',
    'Email Address',
    'Email address (e.g., user@example.com)',
    '["user@example.com", "test@bank.com"]',
    0.90,
    8,
    TRUE,
    '{"caseSensitive": false}'
) ON DUPLICATE KEY UPDATE
    pattern_definition = VALUES(pattern_definition),
    display_name = VALUES(display_name),
    description = VALUES(description),
    examples = VALUES(examples),
    updated_at = CURRENT_TIMESTAMP;
