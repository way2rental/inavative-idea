-- V9__add_session_last_params.sql
-- Add last used parameters column to track context across messages
-- This enables the LLM to resolve references like "same account" or "that account"

-- Add last used params column (JSON of last successfully used params)
-- Example: {"accountId": "ACC001", "userId": "12345"}
ALTER TABLE chat_sessions 
ADD COLUMN IF NOT EXISTS last_used_params VARCHAR(2000) NULL;
