# Enterprise AI Orchestrator – DEEP AS-BUILT System Dump

Author: Mahendra Malviya  
Filled by: GitHub Copilot (from actual codebase)  
Date: December 1, 2025

---

## 0. REPO & CONTEXT

- Repository URL / path: `way2rental/inavative-idea`
- Current branch: `copilot/implement-enterprise-ai-specs`
- Last commit hash: `3173d81134637323ff098a74e1625a0b7c57c6ca`
- Last commit message: `Changes and added file to check`

---

## 1. PROJECT STRUCTURE (MODULES + PACKAGES)

### 1.1 Maven/Gradle Modules

List ALL backend modules with paths:

- `ai-orchestrator-api` – `/ai-orchestrator-api` – REST API controllers, chat services, admin endpoints
- `ai-orchestrator-common` – `/ai-orchestrator-common` – Shared DTOs, exceptions, enums, context utilities
- `ai-orchestrator-core` – `/ai-orchestrator-core` – Scenario routing, dynamic executors, response mapping, SSE
- `ai-orchestrator-data` – `/ai-orchestrator-data` – JPA entities, repositories, DB config, caching
- `ai-orchestrator-llm` – `/ai-orchestrator-llm` – Spring AI LLM client, prompt builder
- `ai-orchestrator-security` – `/ai-orchestrator-security` – JWT authentication, RBAC service, security config
- `ai-orchestrator-ui` – `/ai-orchestrator-ui` – Angular 18 frontend with admin panel

### 1.2 Key Java Package Structure

List major packages and what they contain:

- `com.enterprise.ai.api.controller` – REST controllers (Chat, Admin, Auth, Health)
- `com.enterprise.ai.api.service` – Chat services, validation, performance logging
- `com.enterprise.ai.common.dto` – ChatRequest, ChatResponse, IntentResult, ScenarioRequest, ScenarioResult
- `com.enterprise.ai.common.exception` – LlmException, ScenarioNotFoundException, SecurityViolationException
- `com.enterprise.ai.common.context` – RequestContext, RequestContextHolder
- `com.enterprise.ai.core.router` – ScenarioRouter, DynamicScenarioRouter, DbDrivenScenarioRouter
- `com.enterprise.ai.core.scenario` – QueryExecutor, HttpCallExecutor, DynamicExecutor
- `com.enterprise.ai.core.mapper` – JsonPathResponseMapper, MaskingService
- `com.enterprise.ai.core.sse` – SsePublisherService
- `com.enterprise.ai.data.entity` – AiScenario, AiAuditLog, ChatSession, ChatMessage, PromptTemplate, IntentConfig
- `com.enterprise.ai.data.repository` – JPA repositories for all entities
- `com.enterprise.ai.data.service` – ConfigCacheService, AuditLogService, SessionService
- `com.enterprise.ai.llm.client` – SpringAiLlmClient, ReactiveLlmClient, LlmClient interfaces
- `com.enterprise.ai.llm.prompt` – DynamicPromptBuilder
- `com.enterprise.ai.security.jwt` – JwtAuthenticationFilter, JwtService
- `com.enterprise.ai.security.rbac` – RbacService

---

## 2. AUTHENTICATION & SECURITY (DETAILED)

### 2.1 JWT

- JWT filter class:
    - **Class Name**: `JwtAuthenticationFilter`
    - **Package**: `com.enterprise.ai.security.jwt`
    - **Key methods**:
        - `doFilterInternal(...)` – 
            - Extracts Authorization header
            - Checks for "Bearer " prefix
            - Extracts JWT token
            - Extracts username from token via JwtService
            - Checks if token is not expired
            - Extracts roles from token
            - Creates UsernamePasswordAuthenticationToken with authorities prefixed with "ROLE_"
            - Sets authentication in SecurityContextHolder

- JWT service class:
    - **Class Name**: `JwtService`
    - **Package**: `com.enterprise.ai.security.jwt`
    - **Methods**:
        - `extractUsername(token)` – Extracts subject claim from token
        - `extractRoles(token)` – Returns `List<String>` from "roles" claim
        - `validateToken(token, username)` – Checks if username matches and token is not expired
        - `generateToken(username, roles)` – Creates JWT with subject, roles claim, issuedAt, expiration

- Actual JWT payload fields used:
    - `userId` claim: NO – Username extracted from `subject`
    - `orgId` claim: NO – NOT_IMPLEMENTED
    - `roles` claim: YES – Extracted via `claims.get("roles", List.class)`

### 2.2 RBAC (Role-Based Access Control)

- RBAC service class:
    - **Class Name**: `RbacService`
    - **Package**: `com.enterprise.ai.security.rbac`
    - **Method**: `isAuthorized(String role, String scenarioCode)` and `anyRoleAuthorized(List<String> roles, String scenarioCode)`
        - Logic:
            - Loads role-scenario mappings from `role_scenario_map` table via `RoleScenarioMapRepository`
            - Uses in-memory cache (`ConcurrentHashMap`) for performance
            - Normalizes scenario code to uppercase for comparison
            - Returns true if role's allowed scenarios contain the scenario code
        - Sample code snippet:
          ```java
          public boolean isAuthorized(String role, String scenarioCode) {
              String normalizedScenario = scenarioCode.toUpperCase();
              Set<String> allowedScenarios = roleScenarioCache.get(role);
              return allowedScenarios != null && allowedScenarios.contains(normalizedScenario);
          }
          ```

- Where RBAC is called in the chat flow:
    - **Class**: `ChatService` and `ReactiveChatService`
    - **Method**: `processChat()` / `processIntent()`
    - **Line of flow**: After intent validation passes, before scenario execution:
      ```java
      if (rbacService.anyRoleAuthorized(userRoles, intent.getScenario())) {
          return buildResponse(sessionId, "You don't have permission...", ERROR, ...);
      }
      ```

- Scenarios executed **without** RBAC check: NONE – All scenarios go through RBAC check in chat flow.

---

## 3. INTENT DETECTION – IMPLEMENTATION DETAILS

### 3.1 DB Schema Actually Used

- Does table `ai_intents_master` exist? NO
- **Actual table**: `ai_scenarios` with JPA entity `AiScenario`

- List **all fields** in `AiScenario` entity:
    - `id` (Long)
    - `scenarioCode` (String) – e.g., "TXN_STATUS"
    - `description` (String)
    - `executionType` (String) – "DB_QUERY", "HTTP_CALL", etc.
    - `httpMethod` (String)
    - `httpUrl` (String)
    - `httpHeaders` (String, JSON)
    - `sqlQuery` (String, TEXT)
    - `requestMapping` (String, JSON)
    - `responseMapping` (String, JSON)
    - `timeoutMs` (Integer)
    - `executorBean` (String)
    - `securityLevel` (String)
    - `requiredParams` (String, JSON array)
    - `optionalParams` (String, JSON array)
    - `llmPromptTemplate` (String, TEXT)
    - `promptVersion` (Integer)
    - `promptHistory` (String, JSON)
    - `active` (Boolean)

### 3.2 Intent Detection Client

- Intent client class:
    - **Class Name**: `SpringAiLlmClient`
    - **Package**: `com.enterprise.ai.llm.client`
    - **Method**: `detectIntent(String userInput, String sessionContext)` → `Mono<IntentResult>`
        - Step-by-step:
            1. Builds prompt via `DynamicPromptBuilder.buildIntentDetectionPrompt()`
            2. Calls Spring AI ChatClient: `chatClient.prompt().user(prompt).call().content()`
            3. Parses JSON from response via `parseIntentResult()`
        - Model: Configurable via Spring AI (Ollama, OpenAI, Azure OpenAI)
        - URL: Configured via `spring.ai.ollama.base-url` or similar
        - Synchronous or reactive: **Reactive** (Mono wrapped from blocking call)
        - Expected JSON structure:
          ```json
          {
            "scenario": "TXN_STATUS",
            "confidence": 0.85,
            "params": {"txnId": "UTR123"},
            "missingParams": [],
            "possibleScenarios": [],
            "reasoning": "User asking about transaction"
          }
          ```

### 3.3 Prompt Builder

- Prompt builder class:
    - **Class Name**: `DynamicPromptBuilder`
    - **Package**: `com.enterprise.ai.llm.prompt`
    - **Method**: `buildIntentDetectionPrompt(String userInput, String sessionContext)`
        - Loads all active scenarios from `ConfigCacheService`
        - Builds prompt with scenario list, required params
        - Injects scenario data from `ai_scenarios` table
        - Template (summarized):
          ```
          You are an AI assistant for a banking application.
          Available scenarios and their descriptions:
          - TXN_STATUS: Check transaction status
            Required params: ["txnId"]
          [... all scenarios from DB ...]
          
          Session context: [previous messages]
          User message: "[user input]"
          
          Analyze the user's intent and return JSON with scenario, confidence, params, missingParams, reasoning.
          ```

### 3.4 Intent JSON Parsing

- Intent parsing class:
    - **Class Name**: `SpringAiLlmClient`
    - **Method**: `parseIntentResult(String response)`
        - Does it use regex? YES – `extractJson()` finds first `{` to last `}`
        - Use ObjectMapper? YES – `objectMapper.readTree(jsonPart)`
        - Handle invalid JSON? YES – Returns `UNKNOWN` intent with 0.0 confidence

- If parsing fails:
    - What scenario is returned? `"UNKNOWN"`
    - Fallback JSON:
      ```java
      IntentResult.builder()
          .scenario("UNKNOWN")
          .confidence(0.0)
          .params(new HashMap<>())
          .missingParams(new ArrayList<>())
          .build();
      ```

---

## 4. SERVER-SIDE VALIDATION OF INTENT

### 4.1 Required Parameter Validation

- Validation class:
    - **Class Name**: `IntentValidationService`
    - **Package**: `com.enterprise.ai.api.service`
    - **Method**: `validateRequiredParams(IntentResult intent)`
        - Logic:
            - Loads required params from DB via `ConfigCacheService.getScenarioByCode()`
            - Parses JSON array from `scenario.requiredParams`
            - Checks both LLM's `missingParams` AND verifies params exist with values
            - Returns list of actually missing params

- Example for scenario `TXN_STATUS`:
    - Required params: `["txnId"]`
    - Test case: If LLM misses reporting `txnId` as missing but params map is empty:
        - Method double-checks and adds `txnId` to missing list

### 4.2 Confidence Validation

- Class:
    - **Class Name**: `IntentValidationService`
    - **Fields** (from config):
        - `confidenceThreshold`: 0.75 (default)
        - `lowConfidenceThreshold`: 0.60 (default)

- Method `validate(IntentResult intent, String userQuery)`:
    - What happens if:
        - `confidence >= confidenceThreshold` (0.75): Proceeds to execution
        - `lowConfidenceThreshold <= confidence < confidenceThreshold`: Returns `needsConfirmation`
        - `confidence < lowConfidenceThreshold` (0.60): Returns `lowConfidence`

---

## 5. FOLLOW-UP & MULTI-STEP CONVERSATIONS

### 5.1 Follow-Up Detection & Building

- Follow-up service class:
    - **Class Name**: `SpringAiLlmClient`
    - **Method**: `generateFollowUpQuestion(String scenarioCode, List<String> missingParams)`
        - Where are questions coming from?
            - DB: Uses `DynamicPromptBuilder.buildFollowUpPrompt()` which loads `llmPromptTemplate` from `ai_scenarios` table
            - LLM generates the actual question based on prompt

- If no custom follow-up exists:
    - Default fallback string: `"Please provide the following information: " + missingParams.join(", ")`

### 5.2 Multi-Step Context Handling

- Chat service class:
    - **Class Name**: `ChatService` / `ReactiveChatService`
    - **Method**: `processChat(ChatRequest req)`
        - Exact flow:
            - Step 1: Load session via `sessionRepository.findBySessionId()`
            - Step 2: Save user message via `saveMessage(sessionId, "user", content)`
            - Step 3: Build context via `getSessionContext(sessionId)`
            - Step 4: Call LLM for intent via `llmClient.detectIntent(query, sessionContext)`
            - Step 5: Validate and route via `validationService.validate()` then `scenarioRouter.route()`

- Method that builds session context:
    - **Name**: `getSessionContext(String sessionId)` / `getSessionContextSync()`
    - **Logic**:
        - Fetches top 10 messages: `messageRepository.findTop10BySessionIdOrderByTimestampDesc()`
        - Format: `user: [message]\nassistant: [message]\n...`

---

## 6. UNKNOWN SCENARIO HANDLING

- Where is `scenario = UNKNOWN` handled?
    - **Class**: `IntentValidationService`
    - **Method**: `validate()`
    - Line:
      ```java
      if (intent.getScenario() == null || "UNKNOWN".equals(intent.getScenario())) {
          return ValidationResult.lowConfidence("Unable to understand the request");
      }
      ```

- What user-visible message is returned?
    - From `ChatService.handleValidationFailure()`:
      ```
      I'm not fully sure what you're asking for. Could you please:
      • Be more specific about what you want to check
      • Include relevant IDs or names
      • Or tell me if you want to check:
        1. Transaction Status
        2. Account Summary
        ...
      ```

- Do you return suggested options? YES
    - Where do option labels come from? `IntentValidationService.getCommonScenarios()` loads from DB via `ConfigCacheService.getActiveScenarios()`

---

## 7. EXECUTION LAYER (SCENARIOS & EXECUTORS)

### 7.1 Scenario Router

- Router class:
    - **Class Name**: `DynamicScenarioRouter`
    - **Package**: `com.enterprise.ai.core.router`
    - **Method**: `route(ScenarioRequest request)` / `routeReactive(ScenarioRequest request)`
        - How does it choose execution path?
            - Loads scenario from DB via `ConfigCacheService.getScenarioByCode()`
            - Checks `scenario.executionType`
            - Routes to appropriate executor based on type (QueryExecutor, HttpCallExecutor, etc.)

- Registered executors (implementing `DynamicExecutor`):
    - `QueryExecutor` – DB_QUERY execution (currently commented out with `//@Component`)
    - `HttpCallExecutor` – HTTP_CALL execution (currently commented out with `//@Component`)
    - `ReactiveScenarioExecutor` – Generic reactive executor

### 7.2 Dynamic Executors

- DB Query Executor:
    - **Class Name**: `QueryExecutor`
    - **Method**: `execute(ScenarioRequest request, AiScenario scenario)`
    - How does it:
        - Choose DataSource? Uses `NamedParameterJdbcTemplate` (single datasource)
        - Build SQL? Gets from `scenario.getSqlQuery()`
        - Bind parameters? Via `buildSqlParameters()` using `request_mapping` JSON
        - Enforce row-level security? Via `ReadOnlyEnforcementService.validateSqlQuery()` – checks SELECT only

- HTTP Call Executor:
    - **Class Name**: `HttpCallExecutor`
    - **Method**: `execute(ScenarioRequest request, AiScenario scenario)`
    - How does it:
        - Choose URL? From `scenario.getHttpUrl()` with variable substitution
        - Apply method? Gets from `scenario.getHttpMethod()` (GET/POST only)
        - Handle headers? Parses `scenario.getHttpHeaders()` JSON
        - Handle timeouts & errors? Uses `WebClient` with `.timeout()` and circuit breaker

---

## 8. ROW-LEVEL SECURITY (USER1 CANNOT SEE USER2 DATA)

### Central Row-Level Security Service

- **Class Name**: `RowLevelSecurityService`
- **Package**: `com.enterprise.ai.core.security`
- **Method**: `applyRowLevelSecurity(String rawSql, RequestContext userContext)`

### Implementation Details:

1. **SELECT-Only Enforcement**:
   - If query does NOT start with SELECT → throws `SecurityViolationException`
   - Message: "Only SELECT queries are allowed in READ-ONLY mode"

2. **Automatic owner_user_id Injection**:
   - If SQL does NOT contain `owner_user_id` → injects `AND owner_user_id = :userId`
   - Value bound from `RequestContext.getUserId()`

3. **Automatic org_id Injection**:
   - If SQL does NOT contain `org_id` → injects `AND org_id = :orgId`
   - Value bound from `RequestContext.getOrgId()` or `RequestContext.getTenantId()`

4. **Injection Rules**:
   - If SQL has WHERE → append AND
   - If SQL has no WHERE → create WHERE
   - Applies to OUTERMOST query only

5. **User Context Source**:
   - `RequestContext` contains: userId, orgId/tenantId, role
   - Set via `RequestContextHolder.setContext()` from JWT at request ingress
   - Never passed manually by controller

6. **Enforcement Location**:
   - ONLY called in `QueryExecutor.execute()`
   - Flow: `rawSql → RowLevelSecurityService.applyRowLevelSecurity() → Safe SQL → NamedParameterJdbcTemplate`

7. **Failure Behavior**:
   - If userId or orgId is missing → blocks query
   - Returns: "Security context missing. Access denied."

---

## 9. JSON PATH RESPONSE MAPPING (IF IMPLEMENTED)

- Response mapping class:
    - **Class Name**: `JsonPathResponseMapper`
    - **Package**: `com.enterprise.ai.core.mapper`
    - **Method**: `mapResponse(Object rawData, List<ResponseMappingConfig> mappings)`
        - Does it use JsonPath? YES – via `com.jayway.jsonpath.JsonPath`
        - Use DB-stored mappings? YES – from `ai_response_mappings` table

- Masking:
    - Masking service class:
        - **Class Name**: `MaskingService`
        - **Package**: `com.enterprise.ai.core.mapper`
        - **Methods**:
            - `mask(Object value, String maskingType)` – Main entry point
            - `maskAccount(String)` – Returns `XXXX-XXXX-1234`
            - `maskPan(String)` – Returns `AA******Z`
            - `maskAadhaar(String)` – Returns `XXXX-XXXX-1234`
            - `maskCard(String)` – Returns `XXXX-XXXX-XXXX-1234`
            - `maskEmail(String)` – Returns `a***@domain.com`
            - `maskPhone(String)` – Returns `XXXXX-12345`
            - `detectMaskingType(String fieldName, Object value)` – Auto-detects based on field name
    - Where called? 
        - In `QueryExecutor.applyMandatoryMasking()` – MANDATORY for all DB results
        - Automatically detects masking type from field names

- Does Formatter AI receive raw DB row?
    - **NO** – Blocked by strict enforcement:
        - If `response_mapping` is missing AND no mappings in `ai_response_mappings` table
        - Throws `SecurityViolationException`: "Response mapping not configured for this scenario"
        - Raw data is NEVER exposed to AI formatter

---

## 10. FORMATTER AI – REAL FLOW

- Formatter client class:
    - **Class Name**: `SpringAiLlmClient`
    - **Package**: `com.enterprise.ai.llm.client`
    - **Method**: `formatResponse(String scenarioCode, ScenarioResult result, String userQuery)`
        - Which model? Spring AI (provider-agnostic: Ollama / OpenAI)
        - Is the call blocking or streaming? BOTH available:
            - `formatResponseBlocking()` – Blocking
            - `formatResponseStreaming()` – Streaming via `Flux<String>`

- Input structure given to formatter:
  ```json
  Scenario: TXN_STATUS
  User query: "check my transaction"
  Raw data: {"txnId": "UTR123", "status": "SUCCESS", "amount": 5000}
  
  [scenario-specific llm_prompt_template from DB]
  
  Format this data into a natural, helpful response.
  Keep it under 150 words. Use emojis appropriately.
  ```

- Output constraints:
    - Return plain text only? YES
    - Avoid JSON? YES (prompt says "natural, helpful response")
    - Avoid markdown? NO – Markdown is allowed

---

## 11. SSE STREAMING (BACKEND)

- SSE endpoint:
    - **Controller Class Name**: `ReactiveChatController`
    - **Endpoint URL**: `POST /api/v2/chat/stream`
    - **Return type**: `Flux<ServerSentEvent<String>>`

- For streaming formatter:
    - How do you read streamed tokens?
        - `SpringAiLlmClient.formatResponseStreaming()` uses `chatClient.prompt().stream().content()`
    - Emit SSE events?
        - Wrapped in `ServerSentEvent.builder().data(chunk).build()`

- Sample SSE event payloads:
  ```
  data: 🔍 Analyzing your request...
  
  data: ✅ Request understood - transaction status
  
  data: 🔐 Verifying permissions...
  
  data: ✅ Access granted
  
  data: 📊 Fetching your data...
  
  data: Your transaction UTR123 is SUCCESSFUL...
  ```

---

## 12. FRONTEND CHAT & SSE HANDLING

- Chat component:
    - **File path**: `/ai-orchestrator-ui/src/app/components/chat/chat.component.ts`
    - Where SSE is initialized? `streamMessage(request)` method via `apiService.chatStream()`
    - How messages are appended? Accumulates in `finalResponseBuffer`, updates `assistantMessage.content`

- Does frontend support:
    - Streaming tokens (append text)? YES
    - Follow-up messages from backend? YES – via `ChatResponse.ResponseType.FOLLOW_UP`
    - Unknown scenario options? YES – Shows numbered options from `possibleScenarios`

---

## 13. ADMIN PANEL – REAL IMPLEMENTATION

### 13.1 Dashboard
- **Route**: `/admin`
- **API endpoints used**: 
    - `GET /api/admin/stats`
    - `GET /api/admin/performance`
    - `GET /api/admin/analytics/*`
- Has pagination? NO
- Has filters? NO
- Has sorting? NO

### 13.2 Scenarios
- **Route**: `/admin/scenarios`
- **API endpoints used**:
    - `GET /api/admin/scenarios`
    - `POST /api/admin/scenarios`
    - `PUT /api/admin/scenarios/{id}`
    - `DELETE /api/admin/scenarios/{id}`
- Has pagination? Partial (frontend pagination)
- Has filters? YES – by status, execution type
- Has sorting? YES – by name, created date

### 13.3 Audit Logs
- **Route**: `/admin/audit-logs`
- **API endpoints used**: `GET /api/admin/audit-logs`
- Has pagination? YES – page/size params
- Has filters? YES – by userId, scenarioCode, success, dateRange
- Has sorting? YES – by timestamp

### 13.4 Sessions
- **Route**: `/admin/sessions`
- **API endpoints used**: 
    - `GET /api/admin/sessions`
    - `DELETE /api/admin/sessions/{id}`
- Has pagination? YES
- Has filters? YES – by userId, active status

### 13.5 RBAC
- **Route**: `/admin/rbac`
- **API endpoints used**:
    - `GET /api/admin/rbac/mappings`
    - `POST /api/admin/rbac/mappings`
    - `DELETE /api/admin/rbac/mappings`
    - `POST /api/admin/rbac/refresh`
- Has pagination? NO
- Has filters? NO

### 13.6 Prompts
- **Route**: `/admin/prompts`
- **API endpoints used**:
    - `GET /api/admin/prompts`
    - `POST /api/admin/prompts`
    - `PUT /api/admin/prompts/{id}`
    - `DELETE /api/admin/prompts/{id}`
    - `GET /api/admin/prompts/{id}/history`
    - `POST /api/admin/prompts/{id}/rollback/{version}`

### 13.7 Intents
- **Route**: `/admin/intents`
- **API endpoints used**:
    - `GET /api/admin/intents`
    - `POST /api/admin/intents`
    - `PUT /api/admin/intents/{id}`
    - `DELETE /api/admin/intents/{id}`

---

## 14. AUDIT LOGGING

- Audit entity class:
    - **Class Name**: `AiAuditLog`
    - **Package**: `com.enterprise.ai.data.entity`
    - **Fields**:
        - `id`: Long
        - `executionId`: String
        - `userId`: String
        - `scenarioCode`: String
        - `requestTime`: Instant
        - `responseTime`: Instant
        - `success`: Boolean
        - `errorMessage`: String (TEXT)
        - `rawIntentJson`: String (JSON)
        - `rawResultJson`: String (JSON)
        - `executionTimeMs`: Integer (calculated)

- Logger class:
    - **Class Name**: `ChatService` / `ReactiveChatService`
    - **Method**: `logAudit(...)` / `logAuditAsync(...)`
    - Where called? After scenario execution (success or failure)

- Are audit logs visible in Admin UI? YES
    - Which screen? `/admin/audit-logs` (AuditLogsComponent)

---

## 15. ERROR HANDLING & FALLBACKS

- Global exception handler:
    - **Class Name**: `ReactiveExceptionHandler`
    - **Package**: `com.enterprise.ai.api.config`
    - **Methods**: Handles various exceptions with appropriate HTTP responses

- LLM fallbacks:
    - If Ollama/LLM fails:
        - **Class/Method**: `SpringAiLlmClient.detectIntentFallback()`
        - Returns: `UNKNOWN` intent with 0.0 confidence
    - User message: "I couldn't process your request. Please try rephrasing."

- DB failures:
    - Generic message: "An error occurred processing your request. Please try again."

---

## 16. KNOWN HARD-CODED OR MOCKED AREAS

- Mock DB calls: NONE – Uses real H2/MySQL via JPA
- Mock HTTP calls: NONE – Uses WebClient
- Hardcoded scenario configs: NONE – All from `ai_scenarios` table
- Hardcoded follow-up texts: YES – Fallback in `SpringAiLlmClient.generateFollowUpFallback()`
- Hardcoded mapping instead of JsonPath: NO – Uses JsonPath
- Hardcoded DataSource routing: NO – Dynamic routing via `DataSourceRegistryService` and `dbKey`

**Fallback mappings in RbacService**:
```java
// When DB is empty:
roleScenarioCache.put("USER", Set.of("TXN_STATUS", "ACCOUNT_SUMMARY", "AMBIGUOUS", "UNKNOWN"));
roleScenarioCache.put("ADMIN", Set.of("TXN_STATUS", "ACCOUNT_SUMMARY", "FILE_STATUS", "BATCH_STATUS", "AMBIGUOUS", "UNKNOWN"));
```

**Ambiguity patterns in IntentValidationService**:
```java
AMBIGUITY_PATTERNS = Map.of(
    "BALANCE_WITH_PENDING", List.of(Pattern.compile("(?i)balance.*pending"), ...),
    "GENERIC_STATUS", List.of(Pattern.compile("(?i)^status\\s*$"), ...)
);
```

---

## 17. AUTOMATED TESTS (IF ANY)

- List test packages: NOT_IMPLEMENTED – No test files found
- Any integration tests? NO
- Any tests for:
    - Intent parsing? NO
    - RBAC? NO
    - Row-level security? NO
    - Response mapping? NO

---

## 18. FINAL SELF-REPORTED GAPS (BY CODEBASE)

### NEWLY IMPLEMENTED (Production Closure Chunk 1 + Chunk 2):

**Chunk 1:**
1. **RowLevelSecurityService** - Central row-level security with automatic injection of `owner_user_id = :userId` and `org_id = :orgId`
2. **QueryExecutor now @Component** - Auto-wired with all security dependencies, enforces RLS
3. **HttpCallExecutor now @Component** - Auto-wired with security validation
4. **Mandatory Masking** - All DB results go through `MaskingService` before reaching AI formatter
5. **JWT Secret Externalization** - Loaded from `JWT_SECRET` environment variable, fails fast if missing or insecure

**Chunk 2:**
6. **Global User Context Propagation** - `RequestContext` populated from JWT in `JwtAuthenticationFilter`
7. **RequestContextHolder** - ThreadLocal-based with `set()`, `get()`, `clear()` methods
8. **Dynamic Multi-Datasource Routing** - `DataSourceRegistryService` with `resolveByDbKey()` method
9. **dbKey field in AiScenario** - Each scenario specifies which database to use
10. **HTTP Security Headers** - `HttpCallExecutor` injects `X-User-Id`, `X-Org-Id`, `X-Roles` from context
11. **Context Cleanup** - `RequestContextHolder.clear()` called in finally block to prevent thread-leak

### Still partially implemented:

1. `SsePublisherService` – Created but `ReactiveChatService` handles SSE directly
2. `PromptTemplate` entity – Created but `DynamicPromptBuilder` uses `AiScenario.llmPromptTemplate`
3. `IntentConfig` entity – Created but intent detection uses `AiScenario`
4. `FollowUpGroup` entity – Created but not integrated
5. `PolicyRule` entity – Created but not integrated

### Not implemented though mentioned in specs:

1. **Token/cost tracking** – NOT_IMPLEMENTED
2. **Two-stage intent detection** – Code exists but returns same as single-stage

### Previously Insecure (NOW FIXED):

1. ✅ **Row-level data isolation** – Now enforced centrally via `RowLevelSecurityService`
2. ✅ **Raw DB data exposure** – Now blocked if no response_mapping defined
3. ✅ **JWT secret hardcoded** – Now loaded from environment variable with fail-fast

### TODO comments in code that affect behavior:

1. `// Two-stage detection not needed with Spring AI` – in SpringAiLlmClient
2. `// Could be moved to DB in future` – for AMBIGUITY_PATTERNS in IntentValidationService
