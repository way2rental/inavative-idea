-- =====================================================================
-- Migration: Remove IntentConfig (ai_intents table)
-- Version: 3
-- Date: 2025-01-10
-- 
-- Intent detection is now consolidated into AiScenario entity.
-- This migration drops the redundant ai_intents table.
-- All intent configuration is now stored in ai_scenarios table.
-- =====================================================================

-- Drop the ai_intents table if it exists
DROP TABLE IF EXISTS ai_intents;

-- =====================================================================
-- Note: All intent configuration fields have been merged into ai_scenarios:
-- - intent_key → scenario_code (already exists)
-- - intent_name → scenario_name (already exists)
-- - training_phrases → trigger_phrases / example_queries (already exists)
-- - confidence_threshold → confidence_threshold (added to ai_scenarios)
-- - followup_group → followup_group (added to ai_scenarios)
-- - category → category (already exists)
-- - priority → display_order (already exists)
-- =====================================================================
