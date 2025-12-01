-- V4: Add default LLM prompt templates for all scenarios
-- Ensures prompts are DB-driven and manageable from admin panel

-- Update TXN_STATUS with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For transaction status queries, provide clear status information including:
- Transaction ID/UTR
- Current status (Pending/Completed/Failed)
- Amount and date
- Any relevant details
Format the response in a friendly, professional manner with appropriate emojis.'
WHERE scenario_code = 'TXN_STATUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update FILE_STATUS with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For file processing status queries, provide:
- File name and upload date
- Processing status (Pending/Processing/Completed/Failed)
- Record count if available
- Any error details if failed
Keep the response concise and helpful.'
WHERE scenario_code = 'FILE_STATUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update ACCOUNT_SUMMARY with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For account summary queries, provide:
- Account number (partially masked for security)
- Current balance with currency
- Account type
- Last transaction date
Present the information in a clear, secure manner.'
WHERE scenario_code = 'ACCOUNT_SUMMARY' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update BALANCE_CHECK with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For balance check queries, provide:
- Available balance
- Currency
- As-of date/time
Keep it simple and direct.'
WHERE scenario_code = 'BALANCE_CHECK' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update PAYMENT_HISTORY with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For payment history queries, provide:
- List of recent transactions
- Date, amount, and description for each
- Running balance if available
Format as a clear list.'
WHERE scenario_code = 'PAYMENT_HISTORY' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update BATCH_STATUS with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For batch processing status queries, provide:
- Batch ID
- Processing status
- Records processed vs total
- Start/end time if available
- Error count if any'
WHERE scenario_code = 'BATCH_STATUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update REFUND_STATUS with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For refund status queries, provide:
- Refund reference ID
- Original transaction details
- Refund status and expected timeline
- Amount being refunded'
WHERE scenario_code = 'REFUND_STATUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update UPLOAD_STATUS with default prompt template
UPDATE ai_scenarios SET 
    llm_prompt_template = 'For file upload status queries, provide:
- Upload reference ID
- File name
- Upload status
- Processing status if applicable
- Any validation errors'
WHERE scenario_code = 'UPLOAD_STATUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Set generic template for public/informational scenarios
UPDATE ai_scenarios SET 
    llm_prompt_template = 'Provide helpful information about this banking service. Be informative but concise. Include relevant details that would help the customer.'
WHERE scenario_code IN ('WHATSAPP_BANKING', 'CURRENT_ACCOUNT', 'SAVINGS_ACCOUNT', 'FIXED_DEPOSIT', 
                        'CREDIT_CARD', 'DEBIT_CARD', 'API_SUPPORT', 'RETAIL_LOAN', 'CORPORATE_BANKING', 'NEO_BUSINESS')
AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update AMBIGUOUS with clarification prompt
UPDATE ai_scenarios SET 
    llm_prompt_template = 'The user query is ambiguous. Ask for clarification by providing numbered options based on the possible scenarios detected.'
WHERE scenario_code = 'AMBIGUOUS' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');

-- Update UNKNOWN with fallback prompt
UPDATE ai_scenarios SET 
    llm_prompt_template = 'Unable to understand the user request. Provide a helpful response suggesting common actions the user might want to take, such as checking transaction status, account balance, or file processing status.'
WHERE scenario_code = 'UNKNOWN' AND (llm_prompt_template IS NULL OR llm_prompt_template = '');
