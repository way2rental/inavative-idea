-- V6: Add db_key column for multi-datasource routing
-- Author: Mahendra Malviya
-- Date: December 2025
--
-- This migration adds support for dynamic multi-database routing.
-- Each scenario can now specify which database to use via db_key.

-- Add db_key column to ai_scenarios
ALTER TABLE ai_scenarios ADD COLUMN IF NOT EXISTS db_key VARCHAR(50) DEFAULT 'internal';

-- Update existing scenarios to use 'internal' as default
UPDATE ai_scenarios SET db_key = 'internal' WHERE db_key IS NULL;

-- Add index for db_key lookups
CREATE INDEX IF NOT EXISTS idx_scenarios_db_key ON ai_scenarios(db_key);
