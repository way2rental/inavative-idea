-- Insert Response Template for TRANSACTION_HISTORY scenario
-- This template formats transaction history responses using Freemarker

-- First, delete any existing template for TRANSACTION_HISTORY to avoid conflicts
DELETE FROM ai_response_templates WHERE scenario_code = 'TRANSACTION_HISTORY' AND response_type = 'TEXT';

-- Insert the new template
INSERT INTO ai_response_templates (
    scenario_code,
    response_type,
    template_content,
    template_variables,
    conditions,
    priority,
    template_version,
    active,
    created_at
) VALUES (
    'TRANSACTION_HISTORY',
    'TEXT',
    '<#-- Transaction History Response Template -->
Here is your transaction history for ${scenarioName}:

<#if data?? && data?is_sequence && (data?size > 0)>
<#assign transactions = data />
<#assign transactionCount = transactions?size />

<#if transactionCount == 1>
Found 1 transaction:
<#else>
Found ${transactionCount} transactions:
</#if>

<#list transactions as txn>
---
Transaction #${txn_index + 1}
<#if txn.transactionDate??>
Date: ${txn.transactionDate}
</#if>
<#if txn.transactionType??>
Type: ${txn.transactionType}
</#if>
<#if txn.amount??>
Amount: ${currencySymbol}${txn.amount?string("#,##0.00")}<#if txn.currency??> ${txn.currency}</#if>
</#if>
<#if txn.description??>
Description: ${txn.description}
</#if>
<#if txn.category??>
Category: ${txn.category}
</#if>
<#if txn.balanceAfter??>
Balance After: ${currencySymbol}${txn.balanceAfter?string("#,##0.00")}
</#if>
<#if txn.status??>
Status: ${txn.status}
</#if>
</#list>

<#else>
No transactions found for this account.

<#if userQuery??>
You asked: "${userQuery}"
</#if>
</#if>

<#if assistantName??>
---
${assistantName}
${orgName}
</#if>',
    '{"variables": ["result", "data", "scenarioCode", "scenarioName", "userQuery", "assistantName", "orgName", "currencySymbol"], "description": "Variables available in template: result (ScenarioResult object), data (list of transactions), scenarioCode, scenarioName, userQuery, assistantName, orgName, currencySymbol"}',
    '{"hasData": true}',
    100,
    1,
    TRUE,
    NOW()
);

-- Notes:
-- 1. This template displays transaction history in a simple, readable format
-- 2. It handles empty results gracefully
-- 3. All numeric values are formatted with thousands separators and 2 decimal places
-- 4. Currency symbol is automatically included from SystemConfig
-- 
-- Freemarker syntax used:
-- - ${variable} - Variable interpolation
-- - <#if condition>...</#if> - Conditional blocks
-- - <#list items as item>...</#list> - Loop through collections
-- - <#assign var = value /> - Variable assignment
-- - txn_index - Loop counter (0-based, automatically available in <#list>)
-- - ?string("format") - Number formatting
-- - ?? - Checks if variable exists (null check)
-- - ?is_sequence - Checks if variable is a list/array
-- - ?size - Gets size of collection
-- 
-- The data structure for TRANSACTION_HISTORY:
-- data: List of transaction objects with fields:
--   - transactionId (String)
--   - accountId (String, masked)
--   - transactionDate (String)
--   - transactionType (String: CREDIT, DEBIT, TRANSFER)
--   - amount (Number)
--   - currency (String)
--   - description (String)
--   - category (String)
--   - balanceAfter (Number)
--   - status (String)
