# STRUCTURED CHAT RESPONSE UPGRADE
## Backend + Frontend Contract for Enterprise AI Chat UI

Author: Mahendra Malviya  
Purpose: Replace unstructured AI text responses with fully structured, type-safe UI rendering (TEXT, TABLE, KV, BULLET, FOLLOW_UP, ERROR, MIXED)

---

## 1. PROBLEM STATEMENT (CURRENT ISSUES)

The current system has the following limitations:

- AI returns unstructured plain text
- Frontend tries to infer tables, bullets, and key-values using regex
- Streaming mixes status + final response
- Follow-up questions are not clearly separated
- Tables break for large data
- ERROR, FOLLOW_UP, DATA all look visually similar
- SSE response is hard to safely parse

This causes:
❌ Broken formatting  
❌ UI inconsistency  
❌ Poor user experience  
❌ High risk when scaling to 100+ scenarios

---

## 2. TARGET SOLUTION (FINAL STATE)

The system must use:

✅ A **single structured JSON response contract**  
✅ Strongly typed rendering in the frontend  
✅ Stream only **status messages**, NOT partial data  
✅ Render final response by `type`  
✅ Never guess structure from free text

---

## 3. OFFICIAL RESPONSE CONTRACT (MANDATORY)

Every final AI response MUST follow this structure:

```json
{
  "type": "TEXT | BULLET | TABLE | KV | MIXED | FOLLOW_UP | ERROR",
  "title": "Optional heading",
  "confidence": 0.0,
  "payload": {},
  "footer": "Optional helper message"
}
4. APPROVED RESPONSE TYPES
4.1 TEXT
json
Copy code
{
  "type": "TEXT",
  "title": "Transaction Status",
  "payload": {
    "message": "✅ Your transaction TXN98231 was successful."
  }
}
4.2 BULLET
json
Copy code
{
  "type": "BULLET",
  "title": "Available Services",
  "payload": {
    "items": [
      "Transaction Status",
      "File Processing Status",
      "Account Summary"
    ]
  }
}
4.3 KV (Key-Value Summary)
json
Copy code
{
  "type": "KV",
  "title": "Account Summary",
  "payload": {
    "Account No": "XXXX4567",
    "Balance": "₹5,40,000",
    "Status": "Active"
  }
}
4.4 TABLE
json
Copy code
{
  "type": "TABLE",
  "title": "Recent Transactions",
  "payload": {
    "columns": ["Txn ID", "Date", "Amount", "Status"],
    "rows": [
      ["TXN1", "29-Nov", "₹5000", "SUCCESS"],
      ["TXN2", "30-Nov", "₹1500", "PENDING"]
    ]
  }
}
4.5 MIXED
json
Copy code
{
  "type": "MIXED",
  "title": "File Summary",
  "payload": {
    "text": "Here is today's processing summary:",
    "table": {
      "columns": ["File", "Status", "Time"],
      "rows": [
        ["PAYMENT.xml", "SUCCESS", "10:21 AM"]
      ]
    }
  }
}
4.6 FOLLOW_UP
json
Copy code
{
  "type": "FOLLOW_UP",
  "payload": {
    "missingParams": ["txnId"],
    "question": "Please provide the transaction ID."
  }
}
4.7 ERROR
json
Copy code
{
  "type": "ERROR",
  "payload": {
    "message": "Unable to understand your request.",
    "suggestions": [
      "Transaction Status",
      "Account Summary",
      "File Status"
    ]
  }
}
5. BACKEND CHANGES (MANDATORY)
5.1 Create Universal DTO
Create a new DTO:

java
Copy code
public class StructuredChatResponse {
    private String type;
    private String title;
    private Double confidence;
    private Map<String, Object> payload;
    private String footer;
}
Replace all places returning:

String message

ChatResponse.message

With:

StructuredChatResponse response

5.2 AI Formatter Prompt MUST Be Strict JSON
Formatter System Prompt:

sql
Copy code
You are a response formatting engine.
You MUST return ONLY JSON.
You MUST set one type from:
TEXT, BULLET, TABLE, KV, MIXED, FOLLOW_UP, ERROR.

Never return:
- Markdown tables
- Free text paragraphs without JSON
- Mixed uncontrolled formats
- Partial JSON

Return a single valid JSON object only.
5.3 Follow-Up Handling Change
When required params are missing:

✅ DO NOT return normal TEXT
✅ ALWAYS return:

json
Copy code
{
  "type": "FOLLOW_UP",
  "payload": {
    "missingParams": [...],
    "question": "..."
  }
}
5.4 ERROR / UNKNOWN Handling
For:

UNKNOWN intent

Low confidence

Authorization failure

✅ Always return ERROR type with suggestions.

5.5 SSE Streaming Rule (STRICT)
Streaming must follow TWO PHASE MODEL ONLY:

Phase 1 – Status Only
Examples:

kotlin
Copy code
🔍 Understanding request
🔐 Verifying permissions
📊 Fetching data
Phase 2 – FINAL RESPONSE (ONE SINGLE JSON CHUNK)
✅ Exactly one StructuredChatResponse object
❌ Never stream tables row-by-row
❌ Never stream partial JSON

6. FRONTEND CHANGES (MANDATORY)
6.1 Update ChatMessage Model
ts
Copy code
export interface StructuredResponse {
  type: 'TEXT' | 'BULLET' | 'TABLE' | 'KV' | 'MIXED' | 'FOLLOW_UP' | 'ERROR';
  title?: string;
  payload: any;
  confidence?: number;
  footer?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content?: string; // only for streaming status
  structured?: StructuredResponse; // final response
  timestamp: Date;
  isLoading?: boolean;
  isStreaming?: boolean;
}
6.2 Replace formatResponse() Completely
✅ REMOVE:

Regex based interpretation

Markdown parsing

Guess-based table detection

✅ USE:

Pure JSON rendering by type.

6.3 Typed Renderer in HTML
html
Copy code
<div [ngSwitch]="message.structured?.type">

  <app-chat-text *ngSwitchCase="'TEXT'"></app-chat-text>
  <app-chat-bullet *ngSwitchCase="'BULLET'"></app-chat-bullet>
  <app-chat-kv *ngSwitchCase="'KV'"></app-chat-kv>
  <app-chat-table *ngSwitchCase="'TABLE'"></app-chat-table>
  <app-chat-follow-up *ngSwitchCase="'FOLLOW_UP'"></app-chat-follow-up>
  <app-chat-error *ngSwitchCase="'ERROR'"></app-chat-error>

</div>
6.4 SSE Final JSON Handling
When SSE completes:

✅ Parse final chunk as JSON
✅ Assign it to:

ts
Copy code
assistantMessage.structured = parsedJson;
assistantMessage.content = undefined;
assistantMessage.isLoading = false;
✅ Status messages must never go into structured.

7. UI RENDERING BEHAVIOR BY TYPE
Type	UI Behavior
TEXT	Simple paragraph
BULLET	Ul list
KV	Card-style key-value layout
TABLE	Full width table
FOLLOW_UP	Highlighted question
ERROR	Red warning box
MIXED	Text + table

8. SECURITY & COMPLIANCE BENEFITS
With this design:

✅ No UI guesswork
✅ No XSS from AI
✅ No broken layouts
✅ Clean audit rendering
✅ Predictable behavior
✅ Safe export to PDF/Excel later
✅ Mobile UI compatible

9. MIGRATION STRATEGY
Implement StructuredChatResponse DTO

Update Formatter Prompt to strict JSON

Replace ChatResponse.message with structured response

Update SSE final chunk to send structured JSON

Update Angular ChatMessage interface

Implement typed rendering with ngSwitch

Remove formatResponse() completely

10. ACCEPTANCE CRITERIA
The implementation is considered complete ONLY if:

✅ No regex-based rendering exists
✅ No markdown tables are returned by AI
✅ All responses have a valid type
✅ Follow-ups always use FOLLOW_UP
✅ Errors always use ERROR
✅ Tables render from structured columns + rows
✅ Streaming only shows status messages
✅ Final response is always a single valid JSON block

11. FUTURE EXTENSIONS SUPPORTED BY THIS DESIGN
PDF export

Excel export

Mobile Chat UI

Voice assistants

Bulk reporting dashboards

Audit replay viewer

Fraud analytics views

END OF DOCUMENT

yaml
Copy code

---

✅ This file is **exactly what Copilot needs**  
✅ It is **implementation-ready**  
✅ It is **safe for enterprise banking UI**  
✅ It fixes **all your current rendering + follow-up + SSE issues**
