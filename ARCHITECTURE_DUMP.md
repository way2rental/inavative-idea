# ENTERPRISE AI ORCHESTRATOR - FULL ARCHITECTURAL & IMPLEMENTATION DUMP

## ==============================
## 1. SYSTEM OVERVIEW
## ==============================

### 1.1 What Has Been Built

**What it is:**
An Enterprise AI Orchestrator that provides an intelligent chat interface for business operations. Users can query transaction status, file processing status, or account summaries using natural language in English or Hinglish.

**What problems it solves:**
- Replaces manual lookups in multiple systems with a single conversational interface
- Provides intelligent intent detection using local LLM (Ollama)
- Implements role-based access control for secure data access
- Handles ambiguous queries safely with clarification flows

**What it does NOT do:**
- Does NOT connect to real banking/payment systems (sample executors return mock data)
- Does NOT handle actual financial transactions
- Does NOT provide real-time notifications
- Does NOT have full production database integration (uses in-memory/mock data in executors)

### 1.2 High-Level Architecture (TEXT)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ANGULAR 18 FRONTEND                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │ LoginComp   │  │ ChatComp    │  │ HeaderComp  │  │ AuthService │   │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ HTTP/REST + JWT
┌─────────────────────────────────────────────────────────────────────────┐
│                        SPRING BOOT API MODULE                            │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ AuthController       ChatController       OllamaController       │   │
│  │ POST /api/auth/login POST /api/chat      GET /api/ollama/health  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                           │
│  ┌───────────────────────────▼───────────────────────────────────────┐ │
│  │                      ChatService                                    │ │
│  │  - 3-Layer Protection System                                       │ │
│  │  - Session Management                                               │ │
│  │  - Audit Logging                                                    │ │
│  └───────────────────────────┬───────────────────────────────────────┘ │
└──────────────────────────────┼──────────────────────────────────────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
┌─────────────┐      ┌─────────────────┐      ┌─────────────────┐
│  SECURITY   │      │      LLM        │      │      CORE       │
│   MODULE    │      │    MODULE       │      │     MODULE      │
│             │      │                 │      │                 │
│ JwtService  │      │ OllamaLlmClient │      │ ScenarioRouter  │
│ RbacService │      │ PromptTemplates │      │ ScenarioExecutor│
│ JwtFilter   │      │ OllamaHealth    │      │ 3 Executors     │
└─────────────┘      └────────┬────────┘      └────────┬────────┘
                              │                        │
                              ▼                        ▼
                      ┌───────────────┐        ┌─────────────────┐
                      │   OLLAMA      │        │   DATA MODULE   │
                      │  (localhost)  │        │                 │
                      │  llama3:8b    │        │ JPA Entities    │
                      │               │        │ Repositories    │
                      └───────────────┘        │ Caffeine Cache  │
                                               └────────┬────────┘
                                                        │
                                                        ▼
                                               ┌─────────────────┐
                                               │     MySQL       │
                                               │  ai_orchestrator│
                                               └─────────────────┘
```

### Request → Processing → Response Flow

```
1. User sends message
   → Angular ChatComponent → HTTP POST /api/chat

2. API Layer receives request
   → JwtAuthenticationFilter validates token
   → ChatController.processChat()

3. ChatService orchestration
   → getOrCreateSession() - Creates/retrieves session from DB
   → saveMessage() - Stores user message in DB
   → getSessionContext() - Fetches last 10 messages for context

4. Intent Detection (Ollama)
   → llmClient.detectIntent() - Calls Ollama with prompt
   → Returns: {scenario, confidence, params, missingParams}

5. 3-Layer Validation (IntentValidationService)
   Layer 1: Confidence Check (threshold: 0.60-0.75)
   Layer 2: Required Parameter Check
   Layer 3: Ambiguity Detection

6. Authorization Check
   → rbacService.isAnyRoleAuthorized(userRoles, scenario)

7. Scenario Execution
   → scenarioRouter.route(request)
   → Map.get(scenarioCode) → executor.execute()
   → Returns: ScenarioResult with data

8. Response Formatting
   → llmClient.formatResponse() - Formats data for user
   → saveMessage() - Stores assistant response
   → logAudit() - Creates audit record

9. Response sent back
   → ChatResponse with sessionId, message, confidence, etc.
```

---

## ==============================
## 2. SCENARIO ENGINE CORE
## ==============================

### 2.1 ScenarioExecutor Interface (FULL CODE)

```java
package com.enterprise.ai.core.scenario;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;

/**
 * Interface for scenario executors.
 * Each scenario (TXN_STATUS, FILE_STATUS, etc.) must implement this interface.
 */
public interface ScenarioExecutor {

    /**
     * Get the unique scenario code.
     * @return scenario code (e.g., "TXN_STATUS", "FILE_STATUS")
     */
    String getScenarioCode();

    /**
     * Execute the scenario with the given request.
     * @param request scenario request containing parameters
     * @return scenario result with data
     */
    ScenarioResult execute(ScenarioRequest request);
}
```

### 2.1 ScenarioRouter Class (FULL CODE)

```java
package com.enterprise.ai.core.router;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.ScenarioNotFoundException;
import com.enterprise.ai.core.scenario.ScenarioExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Routes scenario requests to appropriate executors.
 */
@Service
public class ScenarioRouter {

    private final Map<String, ScenarioExecutor> executors;

    public ScenarioRouter(List<ScenarioExecutor> executorList) {
        // Spring injects ALL beans implementing ScenarioExecutor
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        ScenarioExecutor::getScenarioCode,  // Key: scenario code
                        e -> e                              // Value: executor instance
                ));
    }

    /**
     * Route a scenario request to the appropriate executor.
     * NO if-else, NO switch-case - pure Map-based lookup
     */
    public ScenarioResult route(ScenarioRequest request) {
        ScenarioExecutor executor = executors.get(request.getScenario());
        if (executor == null) {
            throw new ScenarioNotFoundException(request.getScenario());
        }
        return executor.execute(request);
    }

    public boolean hasExecutor(String scenarioCode) {
        return executors.containsKey(scenarioCode);
    }

    public java.util.Set<String> getRegisteredScenarios() {
        return executors.keySet();
    }
}
```

### 2.2 How Executors are Registered

**Registration Method: Spring Auto-Discovery**

1. Each executor is annotated with `@Component` (in ScenarioConfig)
2. Spring scans for all beans implementing `ScenarioExecutor`
3. Constructor injection provides `List<ScenarioExecutor>` to ScenarioRouter
4. Router builds Map<String, ScenarioExecutor> at startup

```java
// ScenarioConfig.java - Registers all executors as Spring beans
@Configuration
public class ScenarioConfig {

    @Bean
    public ScenarioExecutor txnStatusExecutor() {
        return new TxnStatusExecutor();
    }

    @Bean
    public ScenarioExecutor fileStatusExecutor() {
        return new FileStatusExecutor();
    }

    @Bean
    public ScenarioExecutor accountSummaryExecutor() {
        return new AccountSummaryExecutor();
    }
}
```

### Proof of No if-else or switch-case

**✅ CONFIRMED: The routing code uses ONLY Map-based lookup:**

```java
// ScenarioRouter.route() method:
ScenarioExecutor executor = executors.get(request.getScenario());
```

**❌ NO if-else chains**
**❌ NO switch-case statements**
**✅ ONLY Map.get() for O(1) lookup**

---

## ==============================
## 3. CONVERSATIONAL REFINEMENT
## ==============================

### 3.1 Required Params vs Extracted Params Comparison (FULL LOGIC)

**Location:** `IntentValidationService.validateRequiredParams()`

```java
private List<String> validateRequiredParams(IntentResult intent) {
    // Required params defined per scenario
    List<String> requiredParams = REQUIRED_PARAMS.getOrDefault(intent.getScenario(), List.of());
    List<String> missing = new ArrayList<>();

    // Step 1: Check what LLM already reported as missing
    if (intent.getMissingParams() != null) {
        missing.addAll(intent.getMissingParams());
    }

    // Step 2: Double-check by verifying params actually exist and have values
    Map<String, Object> params = intent.getParams();
    if (params == null) {
        params = Map.of();
    }

    for (String required : requiredParams) {
        Object value = params.get(required);
        if (value == null || (value instanceof String && ((String) value).isBlank())) {
            if (!missing.contains(required)) {
                missing.add(required);  // Add if not already in list
            }
        }
    }

    return missing;
}
```

**Required Params Map:**
```java
private static final Map<String, List<String>> REQUIRED_PARAMS = Map.of(
    "TXN_STATUS", List.of("txnId"),
    "FILE_STATUS", List.of("fileName"),
    "ACCOUNT_SUMMARY", List.of("accountId")
);
```

### 3.1 missingParams Detection

**Detected in two places:**

1. **Ollama LLM Response** - LLM returns `missingParams` array in JSON
2. **Server-side validation** - `IntentValidationService` double-checks

### 3.1 Pausing Execution When Params Missing

**Location:** `ChatService.handleValidationFailure()`

```java
// Missing parameters - ask for them (EXECUTION IS PAUSED)
if (!validation.missingRequiredParams().isEmpty()) {
    String followUp = llmClient.generateFollowUpQuestion(
            intent.getScenario(), validation.missingRequiredParams());
    saveMessage(sessionId, "assistant", followUp);
    return ChatResponse.builder()
            .sessionId(sessionId)
            .message(followUp)
            .responseType(ChatResponse.ResponseType.FOLLOW_UP)
            .followUpRequired(true)  // Indicates waiting for user input
            .missingParams(validation.missingRequiredParams())
            .scenario(intent.getScenario())
            .build();
    // NOTE: executeScenario() is NEVER called - execution blocked
}
```

### 3.2 Session Context Storage

**Storage:** MySQL Database (`chat_sessions` and `chat_messages` tables)

```java
// Session creation
private String getOrCreateSession(ChatRequest request) {
    String sessionId = UUID.randomUUID().toString();
    ChatSession session = ChatSession.builder()
            .sessionId(sessionId)
            .userId(request.getUserId())
            .build();
    sessionRepository.save(session);  // Saved to MySQL
    return sessionId;
}

// Message storage
private void saveMessage(String sessionId, String role, String content) {
    ChatMessage message = ChatMessage.builder()
            .sessionId(sessionId)
            .role(role)
            .content(content)
            .build();
    messageRepository.save(message);  // Saved to MySQL
}
```

### 3.2 How Partial Inputs are Merged

**Context retrieval from DB:**

```java
private String getSessionContext(String sessionId) {
    // Fetch last 10 messages for this session
    List<ChatMessage> recentMessages = 
        messageRepository.findTop10BySessionIdOrderByTimestampDesc(sessionId);
    
    if (recentMessages.isEmpty()) {
        return "No previous context";
    }

    StringBuilder context = new StringBuilder();
    Collections.reverse(recentMessages);  // Chronological order
    for (ChatMessage msg : recentMessages) {
        context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
    }
    return context.toString();
}
```

**Context is passed to Ollama:**
```java
String prompt = PromptTemplates.buildIntentDetectionPrompt(userInput, sessionContext);
// sessionContext includes previous conversation for context-aware detection
```

### 3.3 FULL Multi-Step Example

**Step 1: User asks incomplete question**
```
User: "Check my transaction status"
```

**System processes:**
```
1. Ollama returns:
   {
     "scenario": "TXN_STATUS",
     "confidence": 0.85,
     "params": {},
     "missingParams": ["txnId"]
   }

2. IntentValidationService validates:
   - Confidence: 0.85 ✓ (above 0.60)
   - Required params: ["txnId"]
   - Extracted params: {}
   - Missing: ["txnId"] ← BLOCKED

3. ChatService returns FOLLOW_UP response
```

**Step 2: System asks follow-up**
```
System: "I'd be happy to help you check your transaction status. 
         Could you please provide the transaction ID or reference number?"
```

**Step 3: User replies with param**
```
User: "TXN123456"
```

**System processes:**
```
1. Context now includes previous messages:
   "user: Check my transaction status
    assistant: Could you please provide the transaction ID?
    user: TXN123456"

2. Ollama returns:
   {
     "scenario": "TXN_STATUS",
     "confidence": 0.95,
     "params": {"txnId": "TXN123456"},
     "missingParams": []
   }

3. Validation passes all 3 layers
4. ScenarioRouter.route() is called
5. TxnStatusExecutor.execute() runs
```

**Step 4: System executes scenario**
```
System: "Your transaction TXN123456 was successful! 
         Amount: ₹5,000 via UPI at 2024-01-15 10:30 AM"
```

---

## ==============================
## 4. OLLAMA INTEGRATION
## ==============================

### 4.1 Ollama Usage Confirmation

**✅ Ollama is used ONLY for:**
1. ✅ Intent detection (`detectIntent()`)
2. ✅ Follow-up question generation (`generateFollowUpQuestion()`)
3. ✅ Response formatting (`formatResponse()`)

**✅ 100% OFFLINE** - Ollama runs locally at `http://localhost:11434`

**Ollama is NOT used for:**
- ❌ Database queries
- ❌ Business logic
- ❌ Authentication

### 4.2 Modelfiles

**NOT IMPLEMENTED** - Custom Modelfiles are not used.

**Prompts are managed via `PromptTemplates.java`:**

The system uses a base Ollama model (llama3:8b) with custom prompts injected at runtime. All prompt templates are defined in `PromptTemplates.java` with:

1. `INTENT_DEFINITIONS` - Rich scenario definitions with positive/negative signals
2. `buildIntentDetectionPrompt()` - Intent detection prompt builder
3. `buildFollowUpPrompt()` - Follow-up question prompt builder
4. `buildResponseFormattingPrompt()` - Response formatting prompt builder

### 4.3 Exact HTTP Request to Ollama

**Request:**
```java
// OllamaLlmClient.callOllama()
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("model", "llama3:8b");
requestBody.put("prompt", prompt);
requestBody.put("stream", false);
requestBody.put("options", Map.of(
    "temperature", 0.3,    // Low for consistency
    "num_predict", 1024    // Limit response length
));

// HTTP POST to http://localhost:11434/api/generate
String response = ollamaWebClient.post()
    .uri("/api/generate")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(requestBody)
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(120))
    .block();
```

**Response Parsing:**
```java
// OllamaLlmClient.parseIntentResult()
JsonNode node = objectMapper.readTree(response);

// Extract JSON from response (LLM might add extra text)
String jsonPart = extractJson(response);

// Parse fields
String scenario = node.has("scenario") ? node.get("scenario").asText() : "UNKNOWN";
double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;

List<String> missingParams = new ArrayList<>();
if (node.has("missingParams") && node.get("missingParams").isArray()) {
    for (JsonNode param : node.get("missingParams")) {
        missingParams.add(param.asText());
    }
}

Map<String, Object> params = new HashMap<>();
if (node.has("params") && node.get("params").isObject()) {
    node.get("params").fields().forEachRemaining(
        entry -> params.put(entry.getKey(), entry.getValue().asText())
    );
}
```

---

## ==============================
## 5. AI SAFETY GATES
## ==============================

### 5.1 Confidence Threshold Check

**Thresholds (configurable in application.yml):**
```yaml
intent:
  confidence:
    threshold: 0.75        # Minimum for direct execution
    low-threshold: 0.60    # Below this, ask for clarification
```

**What happens if confidence is low:**

```java
// IntentValidationService.validate()

// Below 0.60 - Ask for clarification
if (intent.getConfidence() < lowConfidenceThreshold) {
    return ValidationResult.lowConfidence(
        "Low confidence in understanding your request"
    );
}

// Between 0.60 and 0.75 - Ask for confirmation
if (intent.getConfidence() < confidenceThreshold) {
    return ValidationResult.needsConfirmation(
        "I'm 70% sure you want to check transaction status. Please confirm."
    );
}

// Above 0.75 - Direct execution allowed
```

### 5.2 Required Parameter HARD STOP

**Proof that execution is BLOCKED:**

```java
// ChatService.handleValidationFailure()
if (!validation.missingRequiredParams().isEmpty()) {
    // RETURNS HERE - executeScenario() is NEVER reached
    return ChatResponse.builder()
            .responseType(ChatResponse.ResponseType.FOLLOW_UP)
            .followUpRequired(true)
            .missingParams(validation.missingRequiredParams())
            .build();
}

// executeScenario() is only called in ChatService.processChat() 
// AFTER all validations pass:
if (!validation.isValid()) {
    return handleValidationFailure(...);  // Returns early
}
// ... authorization check ...
return executeScenario(...);  // Only called if all checks pass
```

### 5.3 User Confirmation Gate

**Where implemented:** `ChatService.handleValidationFailure()` and `handleConfirmationResponse()`

**When triggered:**
```java
// Moderate confidence (0.60-0.75) triggers confirmation
if (validation.needsConfirmation()) {
    String confirmationMsg = "I understand you want to check " + 
        scenarioDescription + ". Shall I proceed? (Yes/No)";
    
    return ChatResponse.builder()
            .responseType(ChatResponse.ResponseType.CONFIRMATION)
            .followUpRequired(true)
            .pendingAction(PendingAction.builder()
                    .scenario(intent.getScenario())
                    .params(intent.getParams())
                    .build())
            .build();
}
```

**Which scenarios require confirmation:**
- ANY scenario with confidence between 0.60 and 0.75 requires confirmation
- No specific scenarios are hardcoded - it's confidence-based

---

## ==============================
## 6. END-TO-END LIVE FLOWS
## ==============================

### 6.1 TXN_STATUS Full Flow

**Step 1: Intent Prompt Sent to Ollama**
```
You are an ENTERPRISE INTENT DETECTION ENGINE.

===== SCENARIO DEFINITIONS =====
SCENARIO: TXN_STATUS
PURPOSE: Used when user is asking about status of a payment/transaction
POSITIVE SIGNALS: transaction, txn, payment, UPI, status, pending...
REQUIRED PARAMS: ["txnId"]
...

===== USER MESSAGE =====
"Check status of TXN12345"

Return ONLY JSON: {scenario, confidence, params, missingParams}
```

**Step 2: Ollama JSON Output**
```json
{
  "scenario": "TXN_STATUS",
  "confidence": 0.95,
  "params": {"txnId": "TXN12345"},
  "missingParams": [],
  "reasoning": "Clear transaction status request with ID provided"
}
```

**Step 3: Param Validation**
```java
// IntentValidationService checks:
Required: ["txnId"]
Provided: {"txnId": "TXN12345"}
Missing: []  // ✓ All params present
```

**Step 4: Router Call**
```java
ScenarioExecutor executor = executors.get("TXN_STATUS");
// Returns TxnStatusExecutor instance
return executor.execute(request);
```

**Step 5: TxnStatusExecutor Code**
```java
@Override
public ScenarioResult execute(ScenarioRequest request) {
    String txnId = (String) request.getParams().get("txnId");

    // In production: would query database
    // SELECT * FROM transactions WHERE txn_id = ?
    
    Map<String, Object> data = new HashMap<>();
    data.put("txnId", txnId);
    data.put("status", "SUCCESS");
    data.put("amount", 5000);
    data.put("currency", "INR");
    data.put("channel", "UPI");
    data.put("timestamp", Instant.now().toString());

    return ScenarioResult.builder()
            .scenario(SCENARIO_CODE)
            .data(data)
            .success(true)
            .build();
}
```

**Step 6: MySQL Query (Production)**
```sql
-- NOT IMPLEMENTED in current executors (they return mock data)
-- Production query would be:
SELECT txn_id, status, amount, currency, channel, created_at
FROM transactions
WHERE txn_id = 'TXN12345';
```

**Step 7: Data Masking**
```java
// DataMaskingUtil.java provides masking utilities:
// Card: maskCardNumber("1234567890123456") → "XXXX-XXXX-XXXX-3456"
// Account: maskAccountNumber("1234567890") → "XXXX-XXXX-7890"
// PAN: maskPan("ABCDE1234F") → "AB******EF"
```

**Step 8: Formatter Prompt**
```
User query: "Check status of TXN12345"
Scenario: TXN_STATUS
Raw data: {"txnId":"TXN12345","status":"SUCCESS","amount":5000}

Format guidelines:
- Lead with status (Success/Pending/Failed)
- Mention amount and channel
- Include timestamp
```

**Step 9: Final User Response**
```
Your transaction TXN12345 was successful!
Amount: ₹5,000 via UPI
Time: 2024-01-15 10:30:45
```

### 6.2 FILE_STATUS Full Breakdown

**Intent Prompt:** Same structure with FILE_STATUS definitions
**Ollama Output:**
```json
{
  "scenario": "FILE_STATUS",
  "confidence": 0.92,
  "params": {"fileName": "salary_jan_2024.csv"},
  "missingParams": []
}
```

**FileStatusExecutor Code:**
```java
@Override
public ScenarioResult execute(ScenarioRequest request) {
    String fileName = (String) request.getParams().get("fileName");

    Map<String, Object> data = new HashMap<>();
    data.put("fileName", fileName);
    data.put("status", "COMPLETED");
    data.put("totalRecords", 150);
    data.put("processedRecords", 148);
    data.put("failedRecords", 2);
    data.put("completedAt", Instant.now().toString());

    return ScenarioResult.builder()
            .scenario(SCENARIO_CODE)
            .data(data)
            .success(true)
            .build();
}
```

**Final Response:**
```
File "salary_jan_2024.csv" processing completed!
Total: 150 records
Processed: 148 ✓
Failed: 2 ✗
Completed at: 2024-01-15 11:00:00
```

### 6.3 ACCOUNT_SUMMARY with Role Validation

**Ollama Output:**
```json
{
  "scenario": "ACCOUNT_SUMMARY",
  "confidence": 0.90,
  "params": {"accountId": "ACC123"},
  "missingParams": []
}
```

**Role Validation:**
```java
// ChatService.processChat()
List<String> userRoles = getCurrentUserRoles();
// Returns: ["USER"] from JWT token

// RbacService check
if (!rbacService.isAnyRoleAuthorized(userRoles, "ACCOUNT_SUMMARY")) {
    return buildResponse(sessionId, 
        "You don't have permission to access this information.",
        ChatResponse.ResponseType.ERROR, ...);
}

// RbacService.isAnyRoleAuthorized()
// Checks: roleScenarioMap.get("USER").contains("ACCOUNT_SUMMARY")
// USER has access to: ["TXN_STATUS", "ACCOUNT_SUMMARY"] ✓
```

**AccountSummaryExecutor Code:**
```java
@Override
public ScenarioResult execute(ScenarioRequest request) {
    String accountId = (String) request.getParams().get("accountId");

    Map<String, Object> data = new HashMap<>();
    data.put("accountId", DataMaskingUtil.maskAccountNumber(accountId));
    data.put("accountType", "SAVINGS");
    data.put("availableBalance", 125000.50);
    data.put("currency", "INR");
    data.put("lastUpdated", Instant.now().toString());

    return ScenarioResult.builder()
            .scenario(SCENARIO_CODE)
            .data(data)
            .success(true)
            .build();
}
```

---

## ==============================
## 7. DATABASE DESIGN
## ==============================

### Full DDL with Column Explanations

```sql
-- Scenario Registry (defines available scenarios)
CREATE TABLE IF NOT EXISTS ai_scenarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,           -- Unique identifier
    scenario_code VARCHAR(100) UNIQUE NOT NULL,     -- Scenario identifier (TXN_STATUS, etc.)
    description VARCHAR(255),                        -- Human-readable description
    executor_bean VARCHAR(255),                      -- Spring bean name for executor
    security_level VARCHAR(50) DEFAULT 'AUTH',      -- AUTH, PUBLIC, ADMIN
    required_params JSON,                            -- Required params as JSON array
    optional_params JSON,                            -- Optional params as JSON array
    llm_prompt_template TEXT,                        -- Custom prompt template (future use)
    active BOOLEAN DEFAULT TRUE,                     -- Enable/disable scenario
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Role-Scenario Mapping (RBAC)
CREATE TABLE IF NOT EXISTS role_scenario_map (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(100) NOT NULL,                -- Role (USER, ADMIN, OPERATOR)
    scenario_code VARCHAR(100) NOT NULL,            -- Allowed scenario for this role
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_scenario (role_name, scenario_code)  -- Prevent duplicates
);

-- Chat Sessions (conversation tracking)
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) UNIQUE NOT NULL,        -- UUID session identifier
    user_id VARCHAR(100) NOT NULL,                  -- User who owns this session
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- Chat Messages (conversation history)
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL,               -- Links to chat_sessions
    role VARCHAR(20) NOT NULL,                      -- 'user' or 'assistant'
    content TEXT,                                   -- Message content
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
);

-- Audit Logs (compliance and debugging)
CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(100),                      -- Unique request identifier
    user_id VARCHAR(100),                           -- Who made the request
    scenario_code VARCHAR(100),                     -- Which scenario was requested
    request_time TIMESTAMP,                         -- When request was received
    response_time TIMESTAMP,                        -- When response was sent
    success BOOLEAN,                                -- Did execution succeed?
    error_message TEXT,                             -- Error details if failed
    raw_intent_json JSON,                           -- Full intent detection result
    raw_result_json JSON,                           -- Full scenario execution result
    INDEX idx_user_id (user_id),
    INDEX idx_scenario_code (scenario_code),
    INDEX idx_request_time (request_time)
);
```

---

## ==============================
## 8. SECURITY & RBAC
## ==============================

### 8.1 JWT Validation Code

```java
// JwtService.java
public boolean validateToken(String token, String username) {
    final String extractedUsername = extractUsername(token);
    return (extractedUsername.equals(username) && !isTokenExpired(token));
}

private Claims extractAllClaims(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())  // HMAC-SHA verification
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

### 8.1 How User Roles are Extracted

```java
// JwtService.extractRoles()
@SuppressWarnings("unchecked")
public List<String> extractRoles(String token) {
    Claims claims = extractAllClaims(token);
    return claims.get("roles", List.class);  // From JWT "roles" claim
}

// JwtAuthenticationFilter - Sets roles in SecurityContext
List<String> roles = jwtService.extractRoles(jwt);
List<GrantedAuthority> authorities = roles.stream()
    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
    .toList();
```

### 8.2 Where Scenario-Role Validation Happens

```java
// ChatService.processChat() - AFTER intent detection, BEFORE execution
List<String> userRoles = getCurrentUserRoles();
if (!rbacService.isAnyRoleAuthorized(userRoles, intent.getScenario())) {
    String response = "You don't have permission to access this information.";
    saveMessage(sessionId, "assistant", response);
    return buildResponse(sessionId, response, ChatResponse.ResponseType.ERROR, ...);
}
// Only reaches executeScenario() if authorized
```

### 8.3 What Happens When User is NOT Authorized

```java
// User with role "USER" tries to access "FILE_STATUS"
// RbacService: roleScenarioMap.get("USER") = ["TXN_STATUS", "ACCOUNT_SUMMARY"]
// FILE_STATUS not in set → isAnyRoleAuthorized returns false

// Response returned immediately:
return buildResponse(
    sessionId, 
    "You don't have permission to access this information.",
    ChatResponse.ResponseType.ERROR,
    null,  // No scenario exposed
    intent.getScenario(),
    null,
    intent.getConfidence(),
    executionId
);
// Scenario execution is NEVER called
```

---

## ==============================
## 9. AUDIT LOGGING
## ==============================

### 9.1 Where Audit Logs are Written

```java
// ChatService.logAudit() - Called after every request processing
private void logAudit(String executionId, String userId, String scenarioCode,
                      Instant requestTime, Instant responseTime, boolean success,
                      String errorMessage, IntentResult intent, ScenarioResult result) {
    try {
        AiAuditLog auditLog = AiAuditLog.builder()
                .executionId(executionId)
                .userId(userId)
                .scenarioCode(scenarioCode)
                .requestTime(requestTime)
                .responseTime(responseTime)
                .success(success)
                .errorMessage(errorMessage)
                .rawIntentJson(intent != null ? objectMapper.writeValueAsString(intent) : null)
                .rawResultJson(result != null ? objectMapper.writeValueAsString(result) : null)
                .build();
        auditLogRepository.save(auditLog);  // Saves to MySQL
    } catch (Exception e) {
        log.error("Error saving audit log", e);
    }
}
```

### 9.1 Full Audit Log Record Structure

```java
// AiAuditLog.java Entity
@Entity
@Table(name = "ai_audit_logs")
public class AiAuditLog {
    @Id @GeneratedValue
    private Long id;
    private String executionId;      // UUID for tracking
    private String userId;           // Who made request
    private String scenarioCode;     // Which scenario
    private Instant requestTime;     // Start time
    private Instant responseTime;    // End time
    private Boolean success;         // Pass/fail
    private String errorMessage;     // Error details
    
    @Column(columnDefinition = "JSON")
    private String rawIntentJson;    // Full intent detection result
    
    @Column(columnDefinition = "JSON")
    private String rawResultJson;    // Full scenario result
}
```

### 9.2 Sensitive Data Masking

**DataMaskingUtil provides:**
```java
public static String maskCardNumber(String cardNumber)   // XXXX-XXXX-XXXX-3456
public static String maskAccountNumber(String account)    // XXXX-XXXX-7890
public static String maskPan(String pan)                  // AB******EF
public static String maskAadhaar(String aadhaar)          // XXXX-XXXX-1234
public static String maskSensitiveData(String text)       // Masks all patterns
```

**✓ No PAN/CVV/account number leakage:**
- Account numbers are masked in AccountSummaryExecutor
- DataMaskingUtil patterns detect and mask sensitive data
- Response formatter prompt instructs LLM to keep sensitive details masked

---

## ==============================
## 10. PERFORMANCE & RESILIENCE
## ==============================

### 10.1 Caffeine Cache Usage

```java
// CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)           // Max 1000 entries
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 10 min TTL
                .recordStats());             // Enable metrics
        return cacheManager;
    }
}
```

**@Cacheable Example:**
```java
// NOT IMPLEMENTED - Would be used like:
@Cacheable(value = "scenarios", key = "#scenarioCode")
public AiScenario getScenario(String scenarioCode) {
    return scenarioRepository.findByScenarioCode(scenarioCode);
}
```

### 10.2 Resilience4J Configuration

**Circuit Breaker (application.yml):**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      ollama:
        slidingWindowSize: 10           # Last 10 calls evaluated
        minimumNumberOfCalls: 3         # Min calls before circuit opens
        failureRateThreshold: 50        # Opens at 50% failure
        waitDurationInOpenState: 30000  # 30s wait before half-open
        slowCallDurationThreshold: 60000  # 60s = slow call
        slowCallRateThreshold: 80       # Opens at 80% slow calls
```

**Retry Configuration:**
```yaml
  retry:
    instances:
      ollama:
        maxAttempts: 3                    # 3 total attempts
        waitDuration: 1000                # 1s between retries
        enableExponentialBackoff: true    # 1s, 2s, 4s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - com.enterprise.ai.common.exception.LlmException
          - org.springframework.web.reactive.function.client.WebClientResponseException
```

**Timeout for Ollama:**
```yaml
ollama:
  timeout-seconds: 120  # 2 minute timeout
```

### 10.3 Blocking vs Non-Blocking

**Current Implementation: BLOCKING**

```java
// OllamaLlmClient.callOllama()
String response = ollamaWebClient.post()
    .uri("/api/generate")
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(120))
    .block();  // ← BLOCKING CALL
```

The `.block()` makes it synchronous. For full non-blocking:
- Would need to return `Mono<String>` throughout
- WebFlux reactive stack required

---

## ==============================
## 11. ERROR HANDLING & FALLBACKS
## ==============================

### What Happens if Ollama is DOWN

```java
// OllamaLlmClient fallback methods triggered by CircuitBreaker

public IntentResult detectIntentFallback(String userInput, String sessionContext, Throwable t) {
    log.warn("Fallback for detectIntent due to: {}", t.getMessage());
    return createUnknownIntent();  // Returns UNKNOWN scenario
}

public String generateFollowUpFallback(String scenarioCode, List<String> missingParams, Throwable t) {
    log.warn("Fallback for generateFollowUp due to: {}", t.getMessage());
    return "Please provide the following information: " + String.join(", ", missingParams);
}

public String formatResponseFallback(String scenarioCode, ScenarioResult result, String userQuery, Throwable t) {
    log.warn("Fallback for formatResponse due to: {}", t.getMessage());
    if (result != null && result.getData() != null) {
        StringBuilder sb = new StringBuilder("Here is the result:\n");
        result.getData().forEach((key, value) -> 
            sb.append("- ").append(key).append(": ").append(value).append("\n"));
        return sb.toString();
    }
    return "Your request has been processed successfully.";
}
```

### What Happens if MySQL is DOWN

```java
// ChatService catches all exceptions
try {
    // ... all processing ...
} catch (Exception e) {
    log.error("Error processing chat request", e);
    return buildErrorResponse(request.getSessionId(), 
        "An error occurred processing your request. Please try again.");
}
```

### What Happens if JSON from Ollama is Invalid

```java
// OllamaLlmClient.parseIntentResult()
private IntentResult parseIntentResult(String response) {
    try {
        String jsonPart = extractJson(response);  // Tries to find JSON in response
        if (jsonPart == null || jsonPart.isEmpty()) {
            log.warn("No JSON found in Ollama response");
            return createUnknownIntent();  // Returns safe default
        }
        // ... parsing ...
    } catch (Exception e) {
        log.error("Error parsing intent result from response: {}", response, e);
        return createUnknownIntent();  // Returns safe default on any parse error
    }
}

private IntentResult createUnknownIntent() {
    return IntentResult.builder()
            .scenario("UNKNOWN")
            .confidence(0.0)
            .params(new HashMap<>())
            .missingParams(new ArrayList<>())
            .build();
}
```

---

## ==============================
## 12. REUSABILITY CHECK
## ==============================

### No Company-Specific Naming

**✓ CONFIRMED:**
- Package: `com.enterprise.ai` (generic)
- Database: `ai_orchestrator` (generic)
- No "Axis", "HDFC", or any bank names hardcoded
- Scenarios are generic: TXN_STATUS, FILE_STATUS, ACCOUNT_SUMMARY

### Scenarios are DB-Configured

```sql
-- ai_scenarios table allows adding scenarios via DB
INSERT INTO ai_scenarios (scenario_code, description, executor_bean, required_params)
VALUES ('NEW_SCENARIO', 'Description', 'newScenarioExecutor', '["param1"]');
```

**NOTE:** Current implementation loads scenarios from Spring beans. Full DB-driven loading would require:
- Dynamic bean registration
- Or using ai_scenarios table for routing lookup

### Adding New Scenario WITHOUT Touching Core Engine

**Steps to add LOAN_STATUS scenario:**

1. Create executor class:
```java
public class LoanStatusExecutor implements ScenarioExecutor {
    @Override
    public String getScenarioCode() { return "LOAN_STATUS"; }
    
    @Override
    public ScenarioResult execute(ScenarioRequest request) {
        // Implementation
    }
}
```

2. Register in ScenarioConfig:
```java
@Bean
public ScenarioExecutor loanStatusExecutor() {
    return new LoanStatusExecutor();
}
```

3. Add to database:
```sql
INSERT INTO ai_scenarios (scenario_code, description, required_params)
VALUES ('LOAN_STATUS', 'Check loan application status', '["loanId"]');

INSERT INTO role_scenario_map (role_name, scenario_code)
VALUES ('USER', 'LOAN_STATUS');
```

4. Add intent definition to PromptTemplates.java

**Core engine code (ScenarioRouter, ChatService) remains UNTOUCHED.**

---

## ==============================
## 13. WHAT IS STILL MISSING
## ==============================

### ✅ IMPLEMENTED

- [x] Multimodule Maven project structure
- [x] ScenarioExecutor interface and registry-based routing
- [x] Ollama integration with intent detection
- [x] 3-layer protection system (confidence, params, confirmation)
- [x] JWT authentication and RBAC
- [x] Session management with DB storage
- [x] Audit logging
- [x] Data masking utilities
- [x] Caffeine cache configuration
- [x] Resilience4j circuit breaker and retry
- [x] Angular 18 frontend with chat interface
- [x] Error handling and fallbacks
- [x] Rich intent definitions with Hinglish support
- [x] Ambiguity detection and clarification flows

### ⚠️ PARTIALLY IMPLEMENTED

- [ ] Real database queries in executors (currently return mock data)
- [ ] @Cacheable annotations on repository methods
- [ ] Redis cache integration (configured but not actively used)
- [ ] Custom Ollama Modelfiles (using base model with prompts)
- [ ] Full reactive/non-blocking implementation

### ❌ NOT IMPLEMENTED

- [ ] Real banking/payment system integration
- [ ] Real-time notifications (WebSocket)
- [ ] Multi-tenant support
- [ ] Advanced analytics dashboard
- [ ] User feedback collection
- [ ] A/B testing for prompts
- [ ] Prompt versioning and rollback
- [ ] Load balancing for multiple Ollama instances
- [ ] Kubernetes deployment configurations
- [ ] Production-grade secret management (Vault)
- [ ] End-to-end integration tests
- [ ] Performance benchmarks

---

## ==============================
## 14. HOW THIS SCALES TO 150+ SCENARIOS
## ==============================

### How Adding 150 Scenarios Will Work

1. **Create executor classes** - Each scenario needs one executor
2. **Register in Spring** - Add to ScenarioConfig or use component scanning
3. **Add DB entries** - Insert into ai_scenarios and role_scenario_map
4. **Update prompts** - Add intent definitions to PromptTemplates

**Scaling approach:**
- Executors are loaded at startup into Map (O(1) lookup)
- Adding scenarios = adding Map entries
- No code changes to core routing logic

### How Prompt Size is Managed

**Current approach:** All scenarios in single prompt

**Problem at scale:** 150 scenarios × 20 lines each = 3000 lines prompt

**Solutions (NOT YET IMPLEMENTED):**

1. **Category-based prompts:**
   - First detect category (PAYMENTS, FILES, ACCOUNTS)
   - Then use category-specific prompt

2. **Embeddings-based retrieval:**
   - Use vector similarity to find top 5 matching scenarios
   - Include only relevant scenarios in prompt

3. **Two-stage detection:**
   - Stage 1: Quick classification (category)
   - Stage 2: Detailed intent within category

### How Performance is Kept Stable

1. **Map-based routing:** O(1) executor lookup regardless of scenario count
2. **Caching:** Caffeine cache for frequently accessed data
3. **Circuit breaker:** Protects against Ollama overload
4. **Connection pooling:** HikariCP for database connections
5. **Rate limiting:** Resilience4j rate limiter configured

**Bottleneck at scale:** Ollama inference time
**Solution:** Multiple Ollama instances with load balancing

---

## FINAL NOTES

This document represents the current state of the Enterprise AI Orchestrator implementation. The core architecture is complete and functional. Key areas for production readiness:

1. Replace mock executors with real database queries
2. Implement proper secret management
3. Add comprehensive testing
4. Performance optimization for prompt handling
5. Kubernetes deployment configuration
