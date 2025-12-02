-- V8__add_session_pending_state.sql
-- Add pending follow-up state columns to chat_sessions table
-- These columns enable context retention when asking for missing parameters

-- Add pending scenario column
ALTER TABLE chat_sessions 
ADD COLUMN IF NOT EXISTS pending_scenario VARCHAR(100) NULL;

-- Add pending params column (comma-separated list of missing params)
ALTER TABLE chat_sessions 
ADD COLUMN IF NOT EXISTS pending_params VARCHAR(500) NULL;

-- Add collected params column (JSON of already collected params)
ALTER TABLE chat_sessions 
ADD COLUMN IF NOT EXISTS collected_params VARCHAR(2000) NULL;
