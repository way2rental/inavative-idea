# Enterprise AI Orchestrator – JSON Path Mapping & SSE Streaming Specification

Author: Mahendra Malviya  
Purpose: Convert raw DB/API result into AI-friendly structured JSON using JSON Path mapping and stream AI responses via SSE exactly like ChatGPT.

This document is an EXTENSION of:
ENTERPRISE_AI_ORCHESTRATOR_FULL_SYSTEM_SPEC.md

---

## 1. PROBLEM STATEMENT

Currently:
- Raw database results are returned directly.
- AI formatter receives inconsistent input structures.
- Follow-up responses break UI rendering.
- SSE streaming is not perfectly aligned with frontend display.
- Unknown scenarios and partial messages are not handled gracefully.

Goal:
- Introduce a **JSON Path–based Response Mapping Layer**
- Ensure the AI always receives:
    - Predictable
    - Clean
    - Structured
    - Masked
    - Scenario-specific JSON
- Ensure SSE works like ChatGPT (token-by-token smooth streaming)

---

## 2. CORE PRINCIPLE

AI MUST NEVER receive:
- Raw SQL rows
- Raw API payloads
- Unmasked sensitive values
- Large unfiltered datasets

AI MUST ALWAYS receive:
- Clean structured JSON
- Only scenario-relevant fields
- Masked sensitive values
- A predictable schema

---

## 3. RESPONSE MAPPING ARCHITECTURE

[DB/API Raw Result]
|
v
[JSON Path Mapping Engine]
|
v
[Standardized Scenario Output JSON]
|
v
[Formatter AI]
|
v
[SSE Stream]
|
v
[Angular UI]

pgsql
Copy code

---

## 4. JSON PATH MAPPING – SCENARIO DRIVEN

### 4.1 New Table: `ai_response_mappings`

```sql
CREATE TABLE ai_response_mappings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,

  scenario_code VARCHAR(100) NOT NULL,

  source_type VARCHAR(50),    -- DB_QUERY or HTTP_CALL

  source_field VARCHAR(255), -- Raw DB column or JSON API path

  target_field VARCHAR(255), -- Final structured JSON field name

  json_path VARCHAR(255),    -- JSON Path expression

  masking_type VARCHAR(50),  -- NONE, ACCOUNT, PAN, AADHAAR, CARD

  active BOOLEAN DEFAULT TRUE
);
5. EXAMPLE: TXN_STATUS MAPPING
Raw DB Result
json
Copy code
{
  "txn_id": "TXN123",
  "status_code": "S",
  "amount": 5000,
  "account_number": "123456789012",
  "created_at": "2025-11-29T10:30:00"
}
Mapping Table
scenario_code	source_field	target_field	json_path	masking_type
TXN_STATUS	txn_id	txnId	$.txn_id	NONE
TXN_STATUS	status_code	status	$.status_code	NONE
TXN_STATUS	amount	amount	$.amount	NONE
TXN_STATUS	account_number	accountNumber	$.account_number	ACCOUNT
TXN_STATUS	created_at	timestamp	$.created_at	NONE

6. FINAL STRUCTURED JSON SENT TO AI
json
Copy code
{
  "txnId": "TXN123",
  "status": "SUCCESS",
  "amount": 5000,
  "accountNumber": "XXXX-XXXX-9012",
  "timestamp": "2025-11-29T10:30:00"
}
✅ This is what is sent to Formatter AI
✅ Not the raw DB row
✅ Fully masked
✅ Fully predictable

7. GENERAL MAPPING ENGINE RULES
JSON Path is applied on:

DB result (converted to JSON)

HTTP API response

Masking is applied AFTER extraction

Fields not in mapping are discarded

If a mapped field is missing → null is passed

If result set > 1 row:

Convert into JSON array before mapping

8. MASKING ENGINE
Type	Rule
ACCOUNT	XXXX-XXXX-1234
PAN	AA******Z
AADHAAR	XXXX-XXXX-1234
CARD	XXXX-XXXX-XXXX-1234

9. FORMATTER AI CONTRACT (STRICT)
Formatter ALWAYS receives:

json
Copy code
{
  "scenario": "TXN_STATUS",
  "userQuery": "check my transaction status",
  "structuredData": {
    "txnId": "...",
    "status": "...",
    "amount": "...",
    "timestamp": "..."
  }
}
Formatter MUST return:

Natural language response only

No JSON

No markdown

No technical terms

RBI-safe wording

10. SSE STREAMING CONTRACT
10.1 SSE Message Types
Event Type	Purpose
message	Standard token stream
done	End of stream
error	Stream failure
followup	Follow-up question
unknown	Unknown scenario

10.2 SSE Payload FORMAT (MANDATORY)
json
Copy code
{
  "event": "message",
  "data": "Your transaction"
}
json
Copy code
{
  "event": "done"
}
11. FRONTEND SSE RENDERING RULES
Append data token-by-token to chat bubble

Do NOT overwrite previous text

On done → stop typing animation

On error → show fallback message

On followup → show follow-up question directly

On unknown → show suggestion buttons

12. UNKNOWN QUERY HANDLER (AI-LIKE UX)
When scenario = UNKNOWN:

json
Copy code
{
  "event": "unknown",
  "data": {
    "message": "I didn't understand this yet. Here are some things I can help with:",
    "options": [
      "Check Transaction Status",
      "Check File Status",
      "Account Summary",
      "Balance Inquiry"
    ]
  }
}
Frontend must render:

Message

Clickable chips/options

13. FOLLOW-UP SSE FORMAT
json
Copy code
{
  "event": "followup",
  "data": {
    "scenario": "TXN_STATUS",
    "missingParams": ["txnId"],
    "question": "Please provide your transaction ID."
  }
}
Frontend:

Stops stream

Shows question as assistant message

Awaits user input

14. ERROR SSE FORMAT
json
Copy code
{
  "event": "error",
  "data": "Something went wrong while processing your request."
}
15. MULTI-ROW DB RESULT HANDLING
If DB returns multiple rows:

Convert to JSON array

Apply mapping to every object

Provide top N rows only (configurable)

Formatter AI summarizes the list

16. MAX DATA SAFETY LIMITS
Limit	Value
Max rows to AI	50
Max response size	20 KB
Max SSE stream duration	120s

17. WHY THIS MAKES THE BOT LIKE CHATGPT
Token-based SSE streaming

Incremental rendering

Friendly formatter output

Natural follow-ups

No raw JSON shown to user

No broken UI state

18. STRICT DO-NOT-RULES
Formatter MUST NOT see raw DB output

UI MUST NOT render raw objects

AI MUST NOT receive full tables

SSE MUST NOT send arrays as one chunk

No technical errors exposed to end user

19. IMPLEMENTATION LAYERS REQUIRED
Copilot must implement:

JsonPathResponseMapper

MaskingService

FormatterAiRequestBuilder

SsePublisherService

UnknownScenarioHandler

FollowUpSseEmitter

StreamingErrorHandler

20. FINAL OBJECTIVE
After this enhancement:

✅ All DB responses become AI-friendly
✅ All frontend messages feel like ChatGPT
✅ All follow-ups display correctly
✅ All unknown queries show smart suggestions
✅ No UI breaking state
✅ No raw JSON exposed to the user