# Enterprise AI Orchestrator – DEEP AS-BUILT System Dump

Author: Mahendra Malviya  
Purpose: Capture the **exact implementation details** of the current system.  
Rules for Copilot (IMPORTANT):

- Use ONLY the actual code, config, DB schema, and UI that exist.
- If something is not implemented, write **`NOT_IMPLEMENTED`** clearly.
- Do NOT describe plans or TODOs as if they exist.
- For each section: include **class names, method names, and flow details**.

---

## 0. REPO & CONTEXT

- Repository URL / path:
- Current branch:
- Last commit hash:
- Last commit message:

---

## 1. PROJECT STRUCTURE (MODULES + PACKAGES)

### 1.1 Maven/Gradle Modules

List ALL backend modules with paths:

- `module-name` – path – description

Example:
- `core-orchestrator` – `/core-orchestrator` – Chat orchestration and scenario routing
- `llm-integration` – `/llm-integration` – Ollama/OpenAI client

(Replace with real list.)

### 1.2 Key Java Package Structure

List major packages and what they contain:

- `com.xyz.ai.chat` – Chat service layer
- `com.xyz.ai.intent` – Intent detection
- `com.xyz.ai.security` – Security & JWT

---

## 2. AUTHENTICATION & SECURITY (DETAILED)

### 2.1 JWT

- JWT filter class:
    - **Class Name**:
    - **Package**:
    - **Key methods**:
        - `doFilterInternal(...)` – Describe logic in bullet points.

- JWT service class:
    - **Class Name**:
    - **Methods**:
        - `extractUsername(token)` – what it does
        - `extractRoles(token)` – what it returns (type & structure)
        - `validateToken(token, userDetails)` – what checks it performs

- Actual JWT payload fields used:
    - `userId` claim: YES/NO – how extracted? (show code snippet)
    - `orgId` claim: YES/NO – how extracted?
    - `roles` claim: YES/NO – how extracted?

### 2.2 RBAC (Role-Based Access Control)

- RBAC service class:
    - **Class Name**:
    - **Method**: `isAnyRoleAuthorized(List<String> roles, String scenarioCode)`
        - Describe logic
        - Show sample code snippet

- Where RBAC is called in the chat flow:
    - **Class**:
    - **Method**:
    - **Line of flow** (high-level description).

- If there is any place where scenario is executed **without** RBAC check, list it.

---

## 3. INTENT DETECTION – IMPLEMENTATION DETAILS

### 3.1 DB Schema Actually Used

- Does table `ai_intents_master` exist? (YES/NO)
    - If YES: paste actual CREATE TABLE or JPA entity with fields.

- List **all fields** currently in `ai_intents_master` entity:
    - `scenarioCode`:
    - `description`:
    - `requiredParams`:
    - `optionalParams`:
    - `positiveSignals`:
    - `negativeSignals`:
    - `examplePhrases`:
    - others…

### 3.2 Intent Detection Client

- Intent client class:
    - **Class Name**:
    - **Package**:
    - **Method**: `detectIntent(String userMessage, String sessionContext)`
        - Describe step-by-step:
            - How prompt is built
            - Which model is called (name)
            - Which URL is used
            - Whether it’s synchronous or reactive
        - Paste the exact JSON structure it EXPECTS from AI:
          ```json
          {
            "scenario": "...",
            "confidence": ...,
            "params": {...},
            "missingParams": [...]
          }
          ```

### 3.3 Prompt Builder

- Prompt builder class:
    - **Class Name**:
    - **Method**: `buildIntentDetectionPrompt(...)`
        - Paste the actual prompt template (SYSTEM + body) OR summarize accurately if too long.
        - Explain where `ai_intents_master` data is injected.

### 3.4 Intent JSON Parsing

- Intent parsing class:
    - **Class Name**:
    - **Method**: `parseIntentResult(String llmResponse)`
        - Does it:
            - Extract JSON with regex? YES/NO
            - Use ObjectMapper? YES/NO
            - Handle invalid JSON? HOW?

- If parsing fails:
    - What scenario is returned?
    - Example fallback JSON (actual code behavior).

---

## 4. SERVER-SIDE VALIDATION OF INTENT

### 4.1 Required Parameter Validation

- Validation class:
    - **Class Name**:
    - **Method**: e.g. `validateRequiredParams(IntentResult intent)`
        - Describe logic:
            - Does it use DB `requiredParams`?
            - Does it double-check `intent.params` instead of trusting `missingParams` from AI?

- Example:
    - For scenario `TXN_STATUS`:
        - required params:
        - Test case: AI misses `missingParams`
        - What does this method do?

### 4.2 Confidence Validation

- Class:
    - **Class Name**:
    - **Fields** (thresholds from config?):
        - `executeThreshold`:
        - `confirmThreshold`:

- Method:
    - `validateConfidence(IntentResult intent)` – describe logic,
        - What happens if:
            - `confidence >= executeThreshold`
            - `confirmThreshold <= confidence < executeThreshold`
            - `confidence < confirmThreshold`

---

## 5. FOLLOW-UP & MULTI-STEP CONVERSATIONS

### 5.1 Follow-Up Detection & Building

- Follow-up service class:
    - **Class Name**:
    - **Method**: `buildFollowUpQuestion(String scenarioCode, List<String> missingParams)`
        - Where are questions coming from?
            - DB: table `ai_intent_followups`? YES/NO
            - Hardcoded? Show code.

- If no custom follow-up exists:
    - Default question string? Show it.

### 5.2 Multi-Step Context Handling

- Chat service class:
    - **Class Name**:
    - **Method**: `processChat(ChatRequest req)`
        - Explain **exact flow**:
            - Step 1: Load session? Where?
            - Step 2: Save user message? Where?
            - Step 3: Build context? Which method?
            - Step 4: Call LLM for intent?
            - Step 5: Validate & route?

- Method that builds session context:
    - **Name**:
    - **Logic**:
        - How many past messages?
        - In which format? (`user: ...`, `assistant: ...`?)

---

## 6. UNKNOWN SCENARIO HANDLING

- Where is `scenario = UNKNOWN` handled?
    - **Class**:
    - **Method**:

- What user-visible message is returned?
    - Paste the actual text.

- Do you return suggested options? YES/NO  
  If YES:
    - Where do option labels come from?

---

## 7. EXECUTION LAYER (SCENARIOS & EXECUTORS)

### 7.1 Scenario Router

- Router class:
    - **Class Name**:
    - **Method**: `route(ScenarioRequest request)`
        - How does it choose execution path?
            - Map<String, ScenarioExecutor>?
            - DB-driven routing?
            - Switch-case? (if any)

- Registered executors:
    - List **all classes** implementing `ScenarioExecutor` (if interface exists).

### 7.2 Dynamic Executors

If `QueryExecutor` / `HttpCallExecutor` exist:

- DB Query Executor:
    - **Class Name**:
    - **Method**: `execute(ScenarioRequest request)`
    - How does it:
        - Choose DataSource?
        - Build SQL?
        - Bind parameters?
        - Enforce row-level security?

- HTTP Call Executor:
    - **Class Name**:
    - **Method**: `execute(ScenarioRequest request)`
    - How does it:
        - Choose URL?
        - Apply method (GET/POST)?
        - Handle headers?
        - Handle timeouts & errors?

---

## 8. ROW-LEVEL SECURITY (USER1 CANNOT SEE USER2 DATA)

For each READ query touching business data (txn, file, account, etc.):

- List the **class + method** that runs the query.
- For each:
    - Does it include:
        - `WHERE owner_user_id = :userId`? YES/NO
        - Or `WHERE org_id = :orgId`? YES/NO

If there is NO central enforcement and every query manually adds userId/orgId, list that fact here.

If there is a **central helper** like `addOwnershipFilter(params, userContext)`:
- Provide its class and method.
- Show how often it is used.

---

## 9. JSON PATH RESPONSE MAPPING (IF IMPLEMENTED)

- Response mapping class:
    - **Class Name**:
    - **Method**: `map(Object rawResult, String scenarioCode)`
        - Does it:
            - Use JsonPath or manual mapping?
            - Use DB-stored mappings or hardcoded ones?

- Masking:
    - Masking service class:
        - **Class Name**:
        - **Methods**:
            - `maskAccountNumber(String)`, etc.
    - Where is it called in the flow?

- Confirm: Does Formatter AI **ever** receive raw DB row?  
  If YES:
    - List all scenarios where this happens.

---

## 10. FORMATTER AI – REAL FLOW

- Formatter client class:
    - **Class Name**:
    - **Method**: `formatResponse(scenarioCode, structuredData, userQuery)`
        - Which model uses (Ollama / OpenAI)?
        - Is the call blocking or streaming?

- Input structure given to formatter (example JSON payload):
  ```json
  {
    "scenario": "...",
    "userQuery": "...",
    "structuredData": {...}
  }
Output constraints:

Is the model instructed to:

Return plain text only? YES/NO

Avoid JSON? YES/NO

Avoid markdown? YES/NO

11. SSE STREAMING (BACKEND)
    SSE endpoint:

Controller Class Name:

Endpoint URL:

Return type:

SseEmitter / Flux<ServerSentEvent<...>> / other?

For streaming formatter (if implemented):

How do you:

Read streamed tokens?

Emit SSE events (message, done, error)?

Show sample SSE event payloads actually returned:

json
Copy code
data: {"event":"message","data":"Hello"}
12. FRONTEND CHAT & SSE HANDLING
    Chat component:

File path (e.g., /src/app/chat/chat.component.ts):

Where SSE is initialized?

How messages are appended?

Does frontend support:

Streaming tokens (append text)? YES/NO

Follow-up messages from backend? YES/NO

Unknown scenario options? YES/NO

13. ADMIN PANEL – REAL IMPLEMENTATION
    For each Admin page that exists (real, not planned):

13.1 Screen Name:
Route:

API endpoints used:

Does it have:

Pagination? How? (query params & backend)

Filters? List them.

Sorting? Which columns?

View Details modal? Fields shown?

Repeat for:

Intents

Scenarios

Audit Logs

File Registry

RBAC

etc.

14. AUDIT LOGGING
    Audit entity class:

Class Name:

Fields (list all):

executionId:

userId:

scenarioCode:

requestTime:

responseTime:

success:

errorMessage:

rawIntentJson:

rawResultJson:

Logger class:

Class Name:

Method: logAudit(...)

Where it is called in chat flow?

Are audit logs visible in Admin UI? YES/NO
If YES, which screen?

15. ERROR HANDLING & FALLBACKS
    Global exception handler:

Class Name:

Methods: which exceptions mapped to which responses?

LLM fallbacks:

If Ollama fails:

Which method/class handles?

What user message is returned?

DB failures:

Is there a generic “Something went wrong” message? Paste it.

16. KNOWN HARD-CODED OR MOCKED AREAS
    List all:

Mock DB calls:

Mock HTTP calls:

Hardcoded scenario configs:

Hardcoded follow-up texts:

Hardcoded mapping instead of JsonPath:

Hardcoded DataSource routing:

17. AUTOMATED TESTS (IF ANY)
    List test packages:

Any integration tests? YES/NO

Any tests for:

Intent parsing?

RBAC?

Row-level security?

Response mapping?

18. FINAL SELF-REPORTED GAPS (BY CODEBASE)
    Copilot: Based on actual implementation, list what is:

Partially implemented:

Implemented but not wired:

Not implemented though mentioned in specs:

Potentially insecure:

TODO comments in code that affect behavior: