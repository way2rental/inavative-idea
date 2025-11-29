# Enterprise Scenario-Driven AI Orchestrator (Ollama-First)
### Secure Offline AI for Live Enterprise Systems (Java + Spring Boot + Ollama)
---

## 1. PURPOSE & VISION

### 1.1 What We Are Building

We are building a **generic, reusable AI orchestration platform** that:

- Uses **Ollama** as the *only* LLM runtime (self-hosted, offline).
- Uses **Spring Boot (Java)** as the main orchestrator.
- Connects to **live systems** (MySQL, batch/file processing, services) in a secure, controlled way.
- Provides **natural language access** to runtime data through **scenarios** (transaction status, file status, account summary, etc.).
- Supports **conversational refinement**: if required information is missing, the system asks follow-up questions (like ChatGPT) before executing anything.

This is **not just a chatbot**.  
It is a **Scenario-Driven AI Execution Framework** for any enterprise (bank, fintech, NBFC, ERP, SaaS, etc.).

---

### 1.2 Key Capabilities

1. **Scenario-Driven Engine**
   - All business flows are modeled as **scenarios** (TXN_STATUS, FILE_STATUS, ACCOUNT_SUMMARY, etc.).
   - Each scenario has its own executor (Java class) and metadata (required params, roles, templates, etc.).

2. **Ollama-based AI Layer**
   - Ollama runs fully **on-prem / offline**.
   - Used only for:
     - Intent detection
     - Parameter extraction
     - Follow-up question generation
     - Response formatting (natural language)
   - Ollama **does not** access DB or any core system directly.

3. **Runtime Data Integration**
   - Executors can read **live data** from MySQL and other internal systems.
   - Read-only use case for now (status checks, summaries, etc.).

4. **Conversational Refinement**
   - If a scenario needs 3 parameters and user provides only 1 or 2:
     - The system asks follow-up questions.
     - It does **not execute** until all required data is available.

5. **Security, RBAC & Compliance**
   - JWT-based authentication
   - Role-based scenario access
   - Strict masking and redaction rules
   - Full audit trail of all interactions

6. **Performance & Resilience**
   - Streaming responses (SSE) for better UX.
   - Caching (Redis + local).
   - Circuit breaker and timeouts for Ollama and DB calls.

---

### 1.3 Non-Goals

This platform will **not**:

- Execute financial transactions (no funds transfer, no irreversible actions).
- Handle OTP, PIN, password reset or similar sensitive operations.
- Give direct database access to Ollama.
- Replace existing core banking/transaction engines.

---

## 2. HIGH-LEVEL ARCHITECTURE

### 2.1 Logical Architecture

```text
User (Web / Mobile / Internal Tool)
  ↓
API Gateway (optional)
  ↓
Spring Boot AI Orchestrator
  ↓
  ├── Authentication & RBAC
  ├── Conversational Session / Context
  ├── Intent Detection (via Ollama)
  ├── Parameter Extraction / Slot Filling
  ├── Scenario Router
  │      └── Scenario Executors (Java)
  │            ├── TxnStatusExecutor
  │            ├── FileStatusExecutor
  │            ├── AccountSummaryExecutor
  │            └── ...
  ├── LlmClient (Ollama HTTP client)
  ├── Caching Layer (Redis + local)
  ├── Audit & Logging
  ↓
Data Sources (Read-Only)
  ├── MySQL: transactions, files, accounts, etc.
  ├── Other internal services (via REST/GRPC, optional)
  └── Optional: Document store for RAG (future)

2.2 Ollama Integration Overview

Ollama runs on a host inside internal network.

Exposes an HTTP API like http://ollama-host:11434.

The orchestrator uses LlmClient to call:

/api/chat or /api/generate with a message prompt.

No internet access is required.

3. TECHNOLOGY STACK
3.1 Backend

Language: Java 17+

Framework: Spring Boot 3.x

Web Layer: Spring WebFlux (preferred) or Spring MVC

Security: Spring Security + JWT

Persistence: Spring Data JPA + JDBC

Connection Pool: HikariCP

3.2 AI Runtime

LLM Runtime: Ollama

Models (examples, configurable):

llama3:8b for intent + formatting

Optional smaller model for quick classification (if needed)

All models run on-premise / offline.

3.3 Data Layer

Relational DB: MySQL (or PostgreSQL)

Cache: Redis for distributed cache

Local Cache: Caffeine or Spring Cache for hot data

4. CORE CONCEPTS & DATA MODEL
4.1 ChatRequest / ChatResponse DTOs

ChatRequest:

{
  "userId": "string",
  "query": "string",
  "sessionId": "string"
}


ChatResponse:

{
  "sessionId": "string",
  "message": "string",
  "followUpRequired": true,
  "missingParams": ["txnId"],
  "scenario": "TXN_STATUS",
  "meta": {
    "sources": [],
    "executionId": "uuid"
  }
}

4.2 AI Intent Response (from Ollama)

LLM must return strict JSON:

{
  "scenario": "TXN_STATUS",
  "confidence": 0.94,
  "params": {
    "txnId": "TXN123",
    "date": null
  },
  "missingParams": ["date"]
}

4.3 ScenarioRequest & ScenarioResult

ScenarioRequest:

{
  "scenario": "TXN_STATUS",
  "params": {
    "txnId": "TXN123",
    "date": "2025-11-29"
  },
  "userId": "string",
  "sessionId": "string"
}


ScenarioResult:

{
  "scenario": "TXN_STATUS",
  "data": {
    "status": "SUCCESS",
    "amount": 5000,
    "currency": "INR",
    "channel": "UPI",
    "timestamp": "2025-11-29T10:15:00Z"
  }
}

5. SCENARIO ENGINE DESIGN
5.1 Scenario Executor Interface
public interface ScenarioExecutor {

    String getScenarioCode();   // e.g. "TXN_STATUS"

    ScenarioResult execute(ScenarioRequest request);
}


Each scenario (TXN_STATUS, FILE_STATUS, ACCOUNT_SUMMARY, etc.) will have its own Executor class implementing this interface.

5.2 Scenario Router

Responsibilities:

Accept ScenarioRequest.

Find the correct ScenarioExecutor bean by scenarioCode.

Validate permissions (RBAC).

Execute and return ScenarioResult.

High-level pseudo-code:

@Service
public class ScenarioRouter {

    private final Map<String, ScenarioExecutor> executors;

    public ScenarioRouter(List<ScenarioExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                    ScenarioExecutor::getScenarioCode,
                    e -> e
                ));
    }

    public ScenarioResult route(ScenarioRequest request) {
        ScenarioExecutor executor = executors.get(request.getScenario());
        if (executor == null) {
            throw new IllegalStateException("No executor for scenario: " + request.getScenario());
        }
        return executor.execute(request);
    }
}

6. SCENARIO REGISTRY & METADATA
6.1 Scenario Registry Table
CREATE TABLE ai_scenarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) UNIQUE,
    description VARCHAR(255),
    executor_bean VARCHAR(255),
    security_level VARCHAR(50),       -- "PUBLIC", "AUTH", "INTERNAL"
    required_params JSON,             -- e.g. ["txnId","date"]
    optional_params JSON,             -- e.g. ["channel"]
    llm_prompt_template TEXT,         -- template for formatting response
    active BOOLEAN DEFAULT TRUE
);

6.2 Role-to-Scenario Mapping
CREATE TABLE role_scenario_map (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(100),
    scenario_code VARCHAR(100),
    UNIQUE(role_name, scenario_code)
);


Purpose:

Only specific roles can execute specific scenarios.

Enforced in the Scenario Router or a separate RBAC layer.

7. CONVERSATIONAL REFINEMENT / SLOT FILLING
7.1 Required vs Optional Parameters

Each scenario defines:

required_params: must be present before execution

optional_params: may refine behavior but are not mandatory

Example (TXN_STATUS):

"required_params": ["txnId"],
"optional_params": ["date", "channel"]

7.2 Flow When Parameters Are Missing

User sends initial query:

"Check my transaction status"

LLM (Ollama) returns intent:

{
  "scenario": "TXN_STATUS",
  "confidence": 0.90,
  "params": {
    "txnId": null
  },
  "missingParams": ["txnId"]
}


Orchestrator does not execute scenario.

Orchestrator uses LLM again to generate a follow-up question:

Prompt to Ollama:

You are a helpful assistant.
Scenario: TXN_STATUS
Missing parameter: txnId
Ask the user to provide transaction ID in one simple sentence.
Return ONLY the question text.


Ollama:

"Please provide your transaction ID so I can check the status."

Response sent back to user with followUpRequired = true.

User replies with:

"TXN12345"

System merges:

Existing session context

New user message

Fills txnId = "TXN12345"

Now all required parameters are available → Scenario execution proceeds.

8. OLLAMA INTEGRATION DESIGN
8.1 LlmClient Abstraction
public interface LlmClient {

    IntentResult detectIntent(String userInput, String sessionContext);

    String generateFollowUpQuestion(String scenarioCode, List<String> missingParams);

    String formatResponse(String scenarioCode, ScenarioResult result, String userQuery);
}

8.2 OllamaLlmClient Implementation (HTTP Example)

Base URL: configurable, e.g. http://localhost:11434

Endpoint: /api/generate or /api/chat (depending on chosen pattern)

Model name: configurable, e.g. llama3:8b

Pseudo-request body:

{
  "model": "llama3:8b",
  "prompt": "<FULL PROMPT STRING HERE>",
  "stream": false
}


Ollama returns plain text → we parse JSON part from it (intent, etc.).

8.3 Prompt Patterns
Intent Detection Prompt
You are an intent detection engine for an enterprise system.
You MUST output a strict JSON object with this schema:
{
  "scenario": "string",
  "confidence": number,
  "params": { },
  "missingParams": []
}

Available scenarios:
1. TXN_STATUS - Check the status of a transaction by its transaction ID.
   Required params: ["txnId"]
2. FILE_STATUS - Check file processing status by fileName and/or date.
   Required params: ["fileName"]
3. ACCOUNT_SUMMARY - Show account summary.
   Required params: ["accountId"]

User message:
"<USER_INPUT>"

Now detect the best scenario and extract parameters.
If any required parameter is missing, include it in "missingParams".
Return ONLY the JSON, no extra text.

Response Formatting Prompt
You are a professional assistant speaking to a banking/enterprise user.
You will be given:
- Scenario code
- Raw data (JSON)
- Original user query

Your job:
- Explain the result in clear, friendly language.
- Do not hallucinate.
- Do not mention internal field names.
- If any important data is missing, say that clearly.

Scenario: <SCENARIO_CODE>
Data (JSON): <SCENARIO_RESULT_JSON>
User query: <USER_QUERY>

Now generate a single response message for the user.

9. SECURITY & COMPLIANCE DESIGN
9.1 Authentication

JWT-based authentication for all protected endpoints.

Public scenarios (e.g., generic FAQs) can be marked as PUBLIC.

9.2 Authorization / RBAC

Every scenario has a security_level and may require roles.

Before executing any scenario:

Check user’s roles from JWT.

Verify that the role is allowed for that scenario (from role_scenario_map).

9.3 Data Masking & Redaction

Rules:

Full account numbers → mask as XXXX-XXXX-1234.

Full card numbers → mask as XXXX-XXXX-XXXX-1234.

Do not send PAN, Aadhaar, CVV, PIN, passwords to Ollama.

Logs must not store full sensitive values.

Masking happens in:

Scenario executors (before logging)

Before passing any data to LlmClient.

9.4 Ollama Isolation

Ollama server is deployed inside a trusted network segment.

Only orchestrator can call it (IP whitelisted / firewall rules).

No external inbound connections allowed to Ollama host.

10. DATABASE DESIGN (MINIMUM SET)
10.1 Scenario Registry
CREATE TABLE ai_scenarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(100) UNIQUE,
    description VARCHAR(255),
    executor_bean VARCHAR(255),
    security_level VARCHAR(50),
    required_params JSON,
    optional_params JSON,
    llm_prompt_template TEXT,
    active BOOLEAN DEFAULT TRUE
);

10.2 Role-Scenario Map
CREATE TABLE role_scenario_map (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(100),
    scenario_code VARCHAR(100),
    UNIQUE(role_name, scenario_code)
);

10.3 Conversation / Session (Optional but Recommended)
CREATE TABLE chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) UNIQUE,
    user_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP
);

CREATE TABLE chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100),
    role VARCHAR(20),       -- "user" | "assistant" | "system"
    content TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

10.4 Audit Log
CREATE TABLE ai_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(100),
    user_id VARCHAR(100),
    scenario_code VARCHAR(100),
    request_time TIMESTAMP,
    response_time TIMESTAMP,
    success BOOLEAN,
    error_message TEXT,
    raw_intent_json JSON,
    raw_result_json JSON
);

11. SAMPLE SCENARIOS
11.1 Transaction Status (TXN_STATUS)

Description: Check status of a single transaction.

Required Params:

txnId

Executor:

TxnStatusExecutor

Data Source:

MySQL transactions table (read-only).

Example SQL:

SELECT txn_id, status, amount, currency, channel, created_at
FROM transactions
WHERE txn_id = :txnId AND user_id = :userId;

11.2 File Processing Status (FILE_STATUS)

Description: Check processing status of a file (salary, NEFT, etc.).

Required Params:

fileName

Executor:

FileStatusExecutor

Data Source:

MySQL file_processing_log table.

Example SQL:

SELECT file_name, status, total_records, success_records, failed_records, processed_at
FROM file_processing_log
WHERE file_name = :fileName;

11.3 Account Summary (ACCOUNT_SUMMARY)

Description: Show summary for a specific account.

Required Params:

accountId

Executor:

AccountSummaryExecutor

Example SQL:

SELECT account_id, masked_account_no, balance, currency, account_type, status
FROM accounts
WHERE account_id = :accountId AND user_id = :userId;

12. PERFORMANCE & RESILIENCE
12.1 Streaming Responses (SSE)

Use Server-Sent Events (SSE) to:

Immediately acknowledge user query.

Stream progress updates:

“Understanding your request…”

“Fetching live data…”

“Generating response…”

12.2 Parallel Execution

Intent detection and pre-fetching can run in parallel when possible.

Use CompletableFuture / Reactor Mono.zip to combine results.

12.3 Caching

Cache static/product info.

Cache short-lived runtime data where safe (e.g., file status for 10–30 seconds).

Use:

Caffeine for in-memory cache.

Redis for shared cluster-level cache.

12.4 Circuit Breakers

Apply Resilience4J for:

Ollama calls

DB calls (optional)

Fail fast and provide fallback messages if AI is overloaded.

13. TESTING & VALIDATION
13.1 Unit Tests

Scenario executors (pure Java + DB mocks).

LlmClient stubs (mock Ollama calls).

Intent parsing & validating JSON.

13.2 Integration Tests

Full flow:

User query → Intent → Refinement → Scenario execution → Formatting.

Test with a local Ollama instance.

13.3 Security Tests

Ensure unauthorized roles cannot access restricted scenarios.

Ensure no sensitive fields are ever sent to Ollama.

14. EXTENSIBILITY GUIDELINES

Adding a new scenario requires:

New executor implementing ScenarioExecutor.

New entry in ai_scenarios.

Optional role mapping in role_scenario_map.

Update intent prompt to include the scenario description.

Replacing Ollama with another LLM in future:

Implement new LlmClient.

Keep the same interface: detectIntent, generateFollowUpQuestion, formatResponse.

No change to scenario or business logic.

15. SUMMARY

This document defines a complete blueprint for:

A Scenario-Driven AI Orchestrator using Ollama as the LLM.

A secure, auditable way to let users query live enterprise data via natural language.

A system where:

Spring Boot controls all logic and data access.

Ollama is a local, offline language engine.

Every action is configurable, logged, and safe.

Any developer or AI agent implementing from this specification should:

Follow the architecture layers.

Implement the core interfaces (ScenarioExecutor, LlmClient).

Respect the security and masking rules.

Use Ollama only for intent + refinement + formatting.

This ensures the platform is:

Reusable

Compliant

Performant

Enterprise-ready

And can grow from a few scenarios to 150+ without redesign.


::contentReference[oaicite:0]{index=0}
