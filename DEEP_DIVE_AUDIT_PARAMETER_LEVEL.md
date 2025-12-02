# Deep Dive Parameter-Level Audit Report
## AI Orchestrator System - Field-by-Field Data Flow Analysis

**Audit Date:** December 1, 2025  
**Depth Level:** 3-Layer Deep (Parameter → Method → Database → Frontend)  
**Focus:** Parameter flow tracking, unused fields, method name vs implementation mismatches

### Verified Technology Stack (from actual config files)

| Component | Version/Details | Source File |
|-----------|-----------------|-------------|
| **Java** | 17 | pom.xml line 32-33 |
| **Spring Boot** | 3.2.0 | pom.xml line 18 |
| **Angular** | 18.2.0 | package.json line 14 |
| **TailwindCSS** | 3.4.18 | package.json line 42 |
| **TypeScript** | 5.5.2 | package.json line 43 |
| **Database** | MySQL 8.x | application.yml line 10: `jdbc:mysql://localhost:3306/ai_orchestrator` |
| **DB Driver** | com.mysql.cj.jdbc.Driver | application.yml line 13 |
| **Hibernate Dialect** | org.hibernate.dialect.MySQLDialect | application.yml line 28 |
| **LLM Provider** | OpenAI | application.yml line 48: `active-provider: openai` |
| **LLM Model** | gpt-4o-mini | application.yml line 78-79 |
| **Spring AI** | 1.0.0-M6 | ai-orchestrator-llm/pom.xml line 20 |
| **Cache** | Caffeine 3.1.8 | pom.xml line 43 |
| **JWT** | JJWT 0.12.3 | pom.xml line 44 |
| **Resilience** | Resilience4j 2.1.0 | pom.xml line 41 |

---

## Table of Contents

1. [Complete Parameter Flow Map](#complete-parameter-flow-map)
2. [DTO Field-Level Analysis](#dto-field-level-analysis)
3. [Entity Field Usage Audit](#entity-field-usage-audit)
4. [Frontend-Backend Contract Mismatches](#frontend-backend-contract-mismatches)
5. [Method Name vs Implementation Audit](#method-name-vs-implementation-audit)
6. [Database Insert/Read Analysis](#database-insertread-analysis)
7. [Dead Code & Unused Fields Summary](#dead-code--unused-fields-summary)

---

## Complete Parameter Flow Map

### Full Request Journey: User Query → Database → Response

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 1: FRONTEND (Angular)                                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ chat.component.ts :: sendMessage()                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ const request: ChatRequest = {                                                          │ │
│ │   userId: authService.getCurrentUser()?.username || 'anonymous',  ← FROM localStorage   │ │
│ │   query: enhancedQuery,                                           ← FROM input field     │ │
│ │   sessionId: this.sessionId || undefined                          ← FROM component state │ │
│ │ };                                                                                       │ │
│ │                                                                                          │ │
│ │ ⚠️ MISSING FIELDS NOT SENT BY FRONTEND:                                                 │ │
│ │   - requestType: NEVER set (always undefined)                                           │ │
│ │   - confirmed: NEVER set                                                                │ │
│ │   - selectedOption: NEVER set                                                           │ │
│ │   - pendingActionParams: NEVER set                                                      │ │
│ │   - pendingScenario: NEVER set                                                          │ │
│ │   - dryRun: NEVER set                                                                   │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                             │
│ api.service.ts :: chatStream()                                                              │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ fetch('/api/v2/chat/stream', {                                                          │ │
│ │   headers: {                                                                             │ │
│ │     'Authorization': `Bearer ${token}`,  ← token FROM localStorage.currentUser.token    │ │
│ │     'Content-Type': 'application/json',                                                 │ │
│ │     'Accept': 'text/event-stream'                                                       │ │
│ │   },                                                                                     │ │
│ │   body: JSON.stringify(request)          ← ONLY userId, query, sessionId sent           │ │
│ │ })                                                                                       │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 2: CONTROLLER (Spring Boot)                                                           │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ ReactiveChatController :: processChatStreaming(@RequestBody ChatRequest request)           │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ RECEIVED ChatRequest:                                                                    │ │
│ │   userId: "testuser"           ← DESERIALIZED from JSON                                 │ │
│ │   query: "check balance"       ← DESERIALIZED from JSON                                 │ │
│ │   sessionId: "abc-123" | null  ← DESERIALIZED from JSON                                 │ │
│ │   requestType: null            ← NEVER sent by frontend                                 │ │
│ │   confirmed: null              ← NEVER sent by frontend                                 │ │
│ │   selectedOption: null         ← NEVER sent by frontend                                 │ │
│ │   pendingActionParams: null    ← NEVER sent by frontend                                 │ │
│ │   pendingScenario: null        ← NEVER sent by frontend                                 │ │
│ │   dryRun: false                ← @Builder.Default value                                 │ │
│ │                                                                                          │ │
│ │ ⚠️ CONSEQUENCE: request.isConfirmationResponse() ALWAYS returns false                   │ │
│ │ ⚠️ CONSEQUENCE: request.isClarificationResponse() ALWAYS returns false                  │ │
│ │ ⚠️ CONSEQUENCE: Confirmation/Clarification flow NEVER works via streaming endpoint     │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                             │
│ JwtAuthenticationFilter :: doFilterInternal()                                               │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ EXTRACTED FROM JWT:                                                                      │ │
│ │   username = jwtService.extractUsername(token)     ← FROM Authorization header          │ │
│ │   roles = jwtService.extractRoles(token)           ← FROM JWT "roles" claim             │ │
│ │   orgId = jwtService.extractOrgId(token)           ← FROM JWT "orgId" claim (OPTIONAL)  │ │
│ │                                                                                          │ │
│ │ SET TO ThreadLocal:                                                                      │ │
│ │   RequestContext.userId = username                                                       │ │
│ │   RequestContext.tenantId = orgId                                                        │ │
│ │   RequestContext.roles = roles                                                           │ │
│ │                                                                                          │ │
│ │ ⚠️ ISSUE: RequestContextHolder.clear() NOT called in finally block                      │ │
│ │ ⚠️ RISK: ThreadLocal leak in thread pool                                                │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 3: SERVICE (ReactiveChatService)                                                      │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ processChatStreaming(ChatRequest request)                                                   │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ STEP 1: Get user roles                                                                   │ │
│ │   userRoles = getCurrentUserRoles()                                                      │ │
│ │            → SecurityContextHolder.getContext().getAuthentication().getAuthorities()     │ │
│ │            → ["USER"] or ["ADMIN"] (stripped of "ROLE_" prefix)                         │ │
│ │                                                                                          │ │
│ │ STEP 2: Get/Create session                                                               │ │
│ │   sessionId = getOrCreateSessionSync(request)                                            │ │
│ │            → IF request.sessionId exists in DB: return it                               │ │
│ │            → ELSE: UUID.randomUUID().toString() + save new ChatSession                  │ │
│ │                                                                                          │ │
│ │ STEP 3: Save user message                                                                │ │
│ │   saveMessageSync(sessionId, "user", request.getQuery())                                 │ │
│ │            → INSERT INTO chat_messages (session_id, role, content, timestamp)           │ │
│ │            → VALUES (sessionId, "user", query, NOW())                                   │ │
│ │                                                                                          │ │
│ │ STEP 4: Get session context (last 10 messages)                                           │ │
│ │   sessionContext = getSessionContextSync(sessionId)                                      │ │
│ │            → SELECT * FROM chat_messages WHERE session_id=? ORDER BY timestamp DESC LIMIT 10 │
│ │            → Format as "role: content\n" string                                         │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 4: LLM CLIENT (SpringAiLlmClient)                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ detectIntent(userQuery, sessionContext)                                                     │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ INPUT PARAMETERS:                                                                        │ │
│ │   userQuery: "check balance for account 12345"                                          │ │
│ │   sessionContext: "user: hello\nassistant: Hi! How can I help?\n..."                   │ │
│ │                                                                                          │ │
│ │ PROMPT BUILDING (DynamicPromptBuilder):                                                  │ │
│ │   scenarios = configCacheService.getActiveScenarios()                                    │ │
│ │            → SELECT * FROM ai_scenarios WHERE active = true                             │ │
│ │            → USED FIELDS: scenario_code, description, required_params                   │ │
│ │            → UNUSED FIELDS: executor_bean, security_level, http_headers (not in prompt) │ │
│ │                                                                                          │ │
│ │ LLM CALL:                                                                                │ │
│ │   chatClient.prompt().user(prompt).call().content()                                     │ │
│ │   RETURNS: JSON string like:                                                             │ │
│ │   {                                                                                      │ │
│ │     "scenario": "ACCOUNT_BALANCE",                                                       │ │
│ │     "confidence": 0.95,                                                                  │ │
│ │     "params": {"accountId": "12345"},                                                   │ │
│ │     "missingParams": [],                                                                │ │
│ │     "reasoning": "User wants to check account balance"                                  │ │
│ │   }                                                                                      │ │
│ │                                                                                          │ │
│ │ OUTPUT: IntentResult                                                                     │ │
│ │   scenario: String          ← FROM LLM response                                         │ │
│ │   confidence: double        ← FROM LLM response                                         │ │
│ │   params: Map<String,Object> ← FROM LLM response (e.g., {"accountId": "12345"})        │ │
│ │   missingParams: List<String> ← FROM LLM response                                       │ │
│ │   possibleScenarios: List<String> ← FROM LLM response (when ambiguous)                 │ │
│ │   reasoning: String         ← FROM LLM response                                         │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 5: VALIDATION (IntentValidationService)                                               │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ validate(IntentResult intent, String userQuery)                                             │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ LAYER 1 CHECKS:                                                                          │ │
│ │   IF intent.scenario == null OR "UNKNOWN":                                              │ │
│ │      return ValidationResult.unknown()                                                   │ │
│ │   IF intent.confidence < 0.60 (lowConfidenceThreshold):                                 │ │
│ │      return ValidationResult.lowConfidence()                                             │ │
│ │   IF intent.scenario == "AMBIGUOUS" OR intent.isAmbiguous():                            │ │
│ │      return ValidationResult.ambiguous()                                                 │ │
│ │                                                                                          │ │
│ │ LAYER 2: Required Params (from DB)                                                       │ │
│ │   requiredParams = getRequiredParamsFromDb(scenario)                                     │ │
│ │                 → SELECT required_params FROM ai_scenarios WHERE scenario_code = ?      │ │
│ │                 → Parse JSON: ["accountId"]                                             │ │
│ │   FOR EACH param IN requiredParams:                                                      │ │
│ │      IF intent.params.get(param) == null:                                               │ │
│ │         missingParams.add(param)                                                         │ │
│ │   IF missingParams NOT EMPTY:                                                            │ │
│ │      return ValidationResult.missingParams(missingParams)                                │ │
│ │                                                                                          │ │
│ │ LAYER 3: Domain Sanity Checks                                                            │ │
│ │   checkDomainAmbiguity(intent, userQuery)                                                │ │
│ │   → Regex patterns for "balance pending", "generic status"                              │ │
│ │   IF matches: return ValidationResult.ambiguous()                                        │ │
│ │                                                                                          │ │
│ │   IF confidence < 0.75 (confirmationThreshold):                                         │ │
│ │      return ValidationResult.needsConfirmation()                                         │ │
│ │                                                                                          │ │
│ │ OUTPUT: ValidationResult                                                                 │ │
│ │   isValid: boolean                                                                       │ │
│ │   needsConfirmation: boolean                                                             │ │
│ │   isAmbiguous: boolean                                                                   │ │
│ │   hasLowConfidence: boolean                                                              │ │
│ │   isUnknown: boolean                                                                     │ │
│ │   missingRequiredParams: List<String>                                                    │ │
│ │   validationMessage: String                                                              │ │
│ │   suggestedScenarios: List<String>                                                       │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 6: AUTHORIZATION (RbacService)                                                        │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ anyRoleAuthorized(List<String> roles, String scenarioCode)                                  │ 
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ INPUT:                                                                                   │ │
│ │   roles: ["USER"] ← FROM SecurityContext                                                │ │
│ │   scenarioCode: "ACCOUNT_BALANCE" ← FROM IntentResult                                   │ │
│ │                                                                                          │ │
│ │ CACHE LOOKUP:                                                                            │ │
│ │   roleScenarioCache loaded FROM SELECT * FROM role_scenario_map                         │ │
│ │   Structure: { "USER": ["TXN_STATUS", "ACCOUNT_BALANCE", ...], "ADMIN": [...] }        │ │
│ │                                                                                          │ │
│ │ LOGIC:                                                                                   │ │
│ │   authorized = roles.stream().anyMatch(role -> isAuthorized(role, scenarioCode))        │ │
│ │   return !authorized;    ← ⚠️ BUG: INVERTED LOGIC!                                      │ │
│ │                                                                                          │ │
│ │ ⚠️ CRITICAL BUG:                                                                        │ │
│ │   Method returns TRUE when user is NOT authorized                                       │ │
│ │   Method returns FALSE when user IS authorized                                          │ │
│ │   This is opposite of what method name implies!                                         │ │
│ │                                                                                          │ │
│ │ CALLER USAGE (ReactiveChatService line 434):                                            │ │
│ │   if (rbacService.anyRoleAuthorized(userRoles, intent.getScenario())) {                │ │
│ │       // Show "not authorized" error  ← Works by accident due to double inversion!     │ │
│ │   }                                                                                      │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 7: SCENARIO ROUTING (DynamicScenarioRouter)                                           │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ routeReactive(ScenarioRequest request)                                                      │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ INPUT: ScenarioRequest                                                                   │ │
│ │   scenario: "ACCOUNT_BALANCE" ← FROM IntentResult                                       │ │
│ │   params: {"accountId": "12345"} ← FROM IntentResult                                    │ │
│ │   userId: "testuser" ← FROM ChatRequest                                                 │ │
│ │   sessionId: "abc-123" ← FROM ChatRequest                                               │ │
│ │                                                                                          │ │
│ │ STEP 1: Load scenario config                                                             │ │
│ │   scenario = configCacheService.getScenarioByCode("ACCOUNT_BALANCE")                    │ │
│ │          → FROM cache (backed by ai_scenarios table)                                    │ │
│ │                                                                                          │ │
│ │ STEP 2: Get executor by type                                                             │ │
│ │   executionType = scenario.getExecutionType()  → "DB_QUERY"                             │ │
│ │   executor = executors.stream()                                                          │ │
│ │              .filter(e -> e.supports(executionType))                                    │ │
│ │              .findFirst()                                                                │ │
│ │          → Returns QueryExecutor or HttpCallExecutor                                    │ │
│ │                                                                                          │ │
│ │ AiScenario FIELDS USED:                                                                  │ │
│ │   ✅ scenarioCode       → Lookup key                                                    │ │
│ │   ✅ executionType      → Router decision                                               │ │
│ │   ✅ sqlQuery           → Query execution (if DB_QUERY)                                 │ │
│ │   ✅ httpUrl            → HTTP call (if HTTP_CALL)                                      │ │
│ │   ✅ httpMethod         → HTTP method (if HTTP_CALL)                                    │ │
│ │   ✅ requestMapping     → Parameter mapping                                             │ │
│ │   ✅ responseMapping    → Response transformation                                       │ │
│ │   ✅ requiredParams     → Validation                                                    │ │
│ │   ✅ timeoutMs          → Execution timeout                                             │ │
│ │   ✅ llmPromptTemplate  → Response formatting                                           │ │
│ │   ✅ active             → Filter active scenarios                                       │ │
│ │   ✅ dbKey              → Multi-datasource routing (NOT actually used!)                │ │
│ │                                                                                          │ │
│ │ AiScenario FIELDS NEVER USED IN BUSINESS LOGIC:                                         │ │
│ │   ❌ executorBean       → Stored/displayed in admin only                               │ │
│ │   ❌ securityLevel      → Stored/displayed in admin only                               │ │
│ │   ❌ optionalParams     → Stored/displayed in sandbox only                             │ │
│ │   ❌ httpHeaders        → Stored but never parsed/used                                 │ │
│ │   ❌ promptVersion      → Stored but never read                                        │ │
│ │   ❌ promptHistory      → Stored but never read                                        │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 8: QUERY EXECUTION (QueryExecutor)                                                    │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ execute(ScenarioRequest request, AiScenario scenario)                                       │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ STEP 1: Validate SQL                                                                     │ │
│ │   readOnlyEnforcement.validateSqlQuery(scenario.getSqlQuery())                          │ │
│ │   → Checks query starts with "SELECT" (case-insensitive)                                │ │
│ │   → Blocks INSERT, UPDATE, DELETE, DROP, TRUNCATE, ALTER, CREATE                       │ │
│ │                                                                                          │ │
│ │ ⚠️ CRITICAL GAP: Row-level security NOT applied!                                        │ │
│ │   rowLevelSecurityService is INJECTED but NEVER CALLED                                  │ │
│ │   Expected: securedSql = rowLevelSecurityService.applyRowLevelSecurity(sqlQuery)        │ │
│ │   Actual: sqlQuery used directly without owner_user_id/org_id filters                  │ │
│ │                                                                                          │ │
│ │ STEP 2: Build SQL parameters                                                             │ │
│ │   buildSqlParameters(request, scenario)                                                  │ │
│ │   → Parse scenario.requestMapping as JSON: {"accountId": "$.params.accountId"}         │ │
│ │   → Extract values from request.params using JSONPath                                  │ │
│ │   → Returns: {"accountId": "12345"}                                                     │ │
│ │                                                                                          │ │
│ │ STEP 3: Execute query                                                                    │ │
│ │   jdbcTemplate.queryForList(sqlQuery, sqlParams)                                        │ │
│ │   → SELECT * FROM accounts WHERE account_id = :accountId                                │ │
│ │   → Returns: [{"account_id": "12345", "balance": 50000.00, "status": "ACTIVE"}]        │ │
│ │                                                                                          │ │
│ │ STEP 4: Apply response mapping                                                           │ │
│ │   IF responseMappingService.hasMappings(scenarioCode):                                  │ │
│ │      aiReadyData = responseMappingService.mapDbResultToAiRequest(scenarioCode, results) │ │
│ │      → Load mappings FROM ai_response_mappings WHERE scenario_code = ?                 │ │
│ │      → Apply JSONPath extraction + masking                                              │ │
│ │   ELSE:                                                                                  │ │
│ │      aiReadyData = applyResponseMapping(results, scenario)                              │ │
│ │      → Legacy inline mapping from scenario.responseMapping                              │ │
│ │                                                                                          │ │
│ │ OUTPUT: ScenarioResult                                                                   │ │
│ │   scenario: "ACCOUNT_BALANCE"                                                            │ │
│ │   success: true                                                                          │ │
│ │   data: {"accountId": "XXXX-XXXX-2345", "balance": 50000.00, "status": "ACTIVE"}       │ │
│ │   errorMessage: null                                                                     │ │
│ │                                                                                          │ │
│ │ ⚠️ UNUSED INJECTED SERVICE:                                                             │ │
│ │   - DataSourceRegistryService: Injected but never used for multi-DB routing            │ │
│ │   - RowLevelSecurityService: Injected but never called                                  │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 9: RESPONSE FORMATTING (SpringAiLlmClient)                                            │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ formatResponseStreaming(scenarioCode, ScenarioResult result, userQuery)                     │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ INPUT:                                                                                   │ │
│ │   scenarioCode: "ACCOUNT_BALANCE"                                                        │ │
│ │   result.data: {"accountId": "XXXX-XXXX-2345", "balance": 50000.00}                     │ │
│ │   userQuery: "check balance for account 12345"                                          │ │
│ │                                                                                          │ │
│ │ PROMPT BUILDING:                                                                         │ │
│ │   prompt = promptBuilder.buildResponseFormattingPrompt(scenarioCode, dataJson, userQuery)│
│ │         → Uses scenario.llmPromptTemplate if exists                                     │ │
│ │         → Appends standard formatting instructions                                       │ │
│ │                                                                                          │ │
│ │ LLM CALL:                                                                                │ │
│ │   chatClient.prompt().user(prompt).stream().content().collectList()                     │ │
│ │   → Collects all tokens, joins them                                                     │ │
│ │                                                                                          │ │
│ │ OUTPUT: JSON string (StructuredChatResponse format)                                      │ │
│ │   {                                                                                      │ │
│ │     "type": "KV",                                                                        │ │
│ │     "title": "Account Balance",                                                          │ │
│ │     "confidence": 1.0,                                                                   │ │
│ │     "payload": {                                                                         │ │
│ │       "Account": "XXXX-XXXX-2345",                                                       │ │
│ │       "Balance": "₹50,000.00",                                                          │ │
│ │       "Status": "Active"                                                                 │ │
│ │     }                                                                                    │ │
│ │   }                                                                                      │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 10: SSE STREAMING (SsePublisherService + Controller)                                  │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ SSE Event Stream:                                                                           │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ EVENT 1: start                                                                           │ │
│ │   event: start                                                                           │ │
│ │   data: Processing your request...                                                       │ │
│ │                                                                                          │ │
│ │ EVENT 2-N: progress                                                                      │ │
│ │   event: progress                                                                        │ │
│ │   data: 🔍 Analyzing your request...                                                    │ │
│ │   event: progress                                                                        │ │
│ │   data: ✅ Request understood - account balance                                         │ │
│ │   event: progress                                                                        │ │
│ │   data: 🔐 Verifying permissions...                                                     │ │
│ │   event: progress                                                                        │ │
│ │   data: 📊 Fetching your data...                                                        │ │
│ │                                                                                          │ │
│ │ EVENT FINAL: response                                                                    │ │
│ │   event: response                                                                        │ │
│ │   data: {"type":"KV","title":"Account Balance",...}                                     │ │
│ │                                                                                          │ │
│ │ EVENT DONE: done                                                                         │ │
│ │   event: done                                                                            │ │
│ │   data:                                                                                  │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ LAYER 11: FRONTEND RESPONSE HANDLING                                                        │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│ chat.component.ts :: streamMessage() -> subscription handler                                │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ EVENT HANDLING BY TYPE:                                                                  │ │
│ │                                                                                          │ │
│ │ case 'start':                                                                            │ │
│ │   assistantMessage.statusMessages = [chunk];                                            │ │
│ │   assistantMessage.isLoading = true;                                                    │ │
│ │                                                                                          │ │
│ │ case 'progress':                                                                         │ │
│ │   assistantMessage.statusMessages = [chunk.trim()];                                     │ │
│ │                                                                                          │ │
│ │ case 'response':                                                                         │ │
│ │   structuredResponse = JSON.parse(chunk);                                               │ │
│ │   assistantMessage.structured = structuredResponse;                                      │ │
│ │   assistantMessage.content = undefined;  ← Clear text, use structured                  │ │
│ │                                                                                          │ │
│ │   IF structuredResponse.type === 'FOLLOW_UP':                                           │ │
│ │      this.pendingContext = {                                                             │ │
│ │        scenario: structuredResponse.scenario,                                            │ │
│ │        params: {},                                                                       │ │
│ │        missingParams: payload.missingParams                                             │ │
│ │      };                                                                                  │ │
│ │      ⚠️ ISSUE: pendingContext saved but NEVER sent back to backend                     │ │
│ │      ⚠️ ISSUE: ChatRequest.requestType is NEVER set to CONFIRMATION/CLARIFICATION      │ │
│ │                                                                                          │ │
│ │ case 'done':                                                                             │ │
│ │   isLoading = false;                                                                     │ │
│ │   isStreaming = false;                                                                   │ │
│ │   assistantMessage.statusMessages = [];                                                  │ │
│ │                                                                                          │ │
│ │ case 'error':                                                                            │ │
│ │   assistantMessage.structured = {                                                        │ │
│ │     type: 'ERROR',                                                                       │ │
│ │     payload: { message: chunk, suggestions: [...] }                                     │ │
│ │   };                                                                                     │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                             │
│ RENDERING (chat.component.html):                                                            │
│ ┌─────────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ @if (message.structured) {                                                               │ │
│ │   @switch (message.structured.type) {                                                   │ │
│ │     @case ('TEXT')      { <app-chat-text [response]="message.structured"/> }           │ │
│ │     @case ('BULLET')    { <app-chat-bullet [response]="message.structured"/> }         │ │
│ │     @case ('KV')        { <app-chat-kv [response]="message.structured"/> }             │ │
│ │     @case ('TABLE')     { <app-chat-table [response]="message.structured"/> }          │ │
│ │     @case ('MIXED')     { <app-chat-mixed [response]="message.structured"/> }          │ │
│ │     @case ('FOLLOW_UP') { <app-chat-follow-up [response]="message.structured"/> }      │ │
│ │     @case ('ERROR')     { <app-chat-error [response]="message.structured"/> }          │ │
│ │   }                                                                                      │ │
│ │ } @else {                                                                                │ │
│ │   <div [innerHTML]="formatResponse(message.content)"/>                                  │ │
│ │ }                                                                                        │ │
│ └─────────────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## DTO Field-Level Analysis

### ChatRequest (Backend)

| Field | Type | Sent by Frontend | Used by Backend | Stored in DB | Notes |
|-------|------|------------------|-----------------|--------------|-------|
| `userId` | String | ✅ Yes | ✅ Yes | ✅ chat_sessions.user_id | Core field |
| `query` | String | ✅ Yes | ✅ Yes | ✅ chat_messages.content | Core field |
| `sessionId` | String | ✅ Yes (optional) | ✅ Yes | ✅ chat_sessions.session_id | Core field |
| `requestType` | Enum | ❌ Never | ✅ Checked | ❌ No | **DEAD FIELD** - isConfirmationResponse()/isClarificationResponse() always false |
| `confirmed` | Boolean | ❌ Never | ✅ Checked | ❌ No | **DEAD FIELD** - confirmation flow never triggered |
| `selectedOption` | Integer | ❌ Never | ✅ Checked | ❌ No | **DEAD FIELD** - clarification flow never triggered |
| `pendingActionParams` | Map | ❌ Never | ✅ Checked | ❌ No | **DEAD FIELD** - pending action flow never used |
| `pendingScenario` | String | ❌ Never | ✅ Checked | ❌ No | **DEAD FIELD** - pending scenario flow never used |
| `dryRun` | boolean | ❌ Never | ✅ Yes | ❌ No | Uses @Builder.Default=false |

### ChatRequest (Frontend - chat.model.ts)

```typescript
export interface ChatRequest {
  userId: string;      // ✅ Sent
  query: string;       // ✅ Sent  
  sessionId?: string;  // ✅ Sent
  // ❌ MISSING: requestType, confirmed, selectedOption, pendingActionParams, pendingScenario, dryRun
}
```

**⚠️ MISMATCH:** Frontend TypeScript interface missing 6 fields that backend expects.

### ChatResponse (Backend)

| Field | Type | Set by Backend | Used by Frontend | Notes |
|-------|------|----------------|------------------|-------|
| `sessionId` | String | ✅ Yes | ✅ Yes | Core field |
| `message` | String | ✅ Yes | ⚠️ Partially | Only used if structured is null |
| `responseType` | Enum | ✅ Yes | ❌ No | Frontend uses structured.type instead |
| `followUpRequired` | boolean | ✅ Yes | ❌ No | Checked but structured.type preferred |
| `missingParams` | List | ✅ Yes | ❌ No | Uses structured.payload.missingParams |
| `scenario` | String | ✅ Yes | ❌ No | Uses structured.scenario |
| `possibleScenarios` | List | ✅ Yes | ❌ No | **NEVER used** by frontend |
| `confidence` | Double | ✅ Yes | ❌ No | Frontend ignores this |
| `pendingAction` | Object | ✅ Yes | ❌ No | **NEVER used** - confirmation flow broken |
| `meta` | Object | ✅ Yes | ⚠️ Partial | Only executionId used for debugging |

### ChatResponse (Frontend - chat.model.ts)

```typescript
export interface ChatResponse {
  sessionId: string;       // ✅ Used
  message: string;         // ⚠️ Legacy only
  followUpRequired: boolean; // ❌ Ignored
  missingParams?: string[];  // ❌ Ignored
  scenario?: string;         // ❌ Ignored
  meta?: ResponseMeta;       // ⚠️ Partial (executionId only)
  // ❌ MISSING: responseType, possibleScenarios, confidence, pendingAction
}
```

---

## Entity Field Usage Audit

### AiScenario Entity

| Field | Column | Used in Business Logic | Used in Admin UI | Saved to DB | Notes |
|-------|--------|------------------------|------------------|-------------|-------|
| `id` | id | ✅ Lookup | ✅ Display | ✅ PK | Core |
| `scenarioCode` | scenario_code | ✅ Routing | ✅ Display | ✅ Yes | Core |
| `description` | description | ✅ Prompt building | ✅ Display | ✅ Yes | Core |
| `executionType` | execution_type | ✅ Router decision | ✅ Display | ✅ Yes | Core |
| `dbKey` | db_key | ❌ **NEVER used** | ✅ Display | ✅ Yes | **DEAD** - DataSourceRegistryService not called |
| `httpMethod` | http_method | ✅ HTTP executor | ✅ Display | ✅ Yes | For HTTP_CALL |
| `httpUrl` | http_url | ✅ HTTP executor | ✅ Display | ✅ Yes | For HTTP_CALL |
| `httpHeaders` | http_headers | ❌ **NEVER parsed** | ✅ Display | ✅ Yes | **DEAD** - stored but not used |
| `sqlQuery` | sql_query | ✅ Query executor | ✅ Display | ✅ Yes | For DB_QUERY |
| `requestMapping` | request_mapping | ✅ Param binding | ✅ Display | ✅ Yes | Core |
| `responseMapping` | response_mapping | ⚠️ Fallback only | ✅ Display | ✅ Yes | Superseded by ai_response_mappings |
| `timeoutMs` | timeout_ms | ✅ Timeout config | ✅ Display | ✅ Yes | Core |
| `executorBean` | executor_bean | ❌ **NEVER used** | ✅ Display | ✅ Yes | **DEAD** - old hardcoded pattern |
| `securityLevel` | security_level | ❌ **NEVER used** | ✅ Display | ✅ Yes | **DEAD** - no security check uses this |
| `requiredParams` | required_params | ✅ Validation | ✅ Display | ✅ Yes | Core |
| `optionalParams` | optional_params | ❌ **Not in validation** | ✅ Sandbox only | ✅ Yes | Display only |
| `llmPromptTemplate` | llm_prompt_template | ✅ Prompt building | ✅ Display | ✅ Yes | Core |
| `promptVersion` | prompt_version | ❌ **NEVER read** | ✅ Display | ✅ Yes | **DEAD** - versioning not implemented |
| `promptHistory` | prompt_history | ❌ **NEVER read** | ❌ No | ✅ Yes | **DEAD** - history not queried |
| `active` | active | ✅ Filtering | ✅ Display | ✅ Yes | Core |

**Summary: 7 of 19 fields are DEAD (never used in business logic)**

### ChatSession Entity

| Field | Column | Used | Notes |
|-------|--------|------|-------|
| `id` | id | ✅ Yes | PK |
| `sessionId` | session_id | ✅ Yes | Core - lookup key |
| `userId` | user_id | ✅ Yes | Core - session owner |
| `createdAt` | created_at | ✅ Yes | Auto-set on insert |
| `lastActivityAt` | last_activity_at | ✅ Yes | Updated on each message |

**All fields used ✅**

### ChatMessage Entity

| Field | Column | Used | Notes |
|-------|--------|------|-------|
| `id` | id | ✅ Yes | PK |
| `sessionId` | session_id | ✅ Yes | FK to session |
| `role` | role | ✅ Yes | "user" / "assistant" |
| `content` | content | ✅ Yes | Message text |
| `timestamp` | timestamp | ✅ Yes | Auto-set on insert |

**All fields used ✅**

### AiAuditLog Entity

| Field | Column | Written | Read | Notes |
|-------|--------|---------|------|-------|
| `id` | id | ✅ Auto | ✅ Admin | PK |
| `executionId` | execution_id | ✅ Yes | ✅ Admin | Unique per request |
| `userId` | user_id | ✅ Yes | ✅ Admin | From ChatRequest |
| `scenarioCode` | scenario_code | ✅ Yes | ✅ Admin | From IntentResult |
| `requestTime` | request_time | ✅ Yes | ✅ Admin | Start timestamp |
| `responseTime` | response_time | ✅ Yes | ✅ Admin | End timestamp |
| `success` | success | ✅ Yes | ✅ Admin | Success flag |
| `errorMessage` | error_message | ✅ Yes | ✅ Admin | Error details |
| `rawIntentJson` | raw_intent_json | ✅ Yes | ⚠️ **Never displayed** | JSON blob - stored but never shown |
| `rawResultJson` | raw_result_json | ✅ Yes | ⚠️ **Never displayed** | JSON blob - stored but never shown |
| `executionTimeMs` | execution_time_ms | ✅ Calculated | ✅ Admin | Auto-calculated |

**2 fields stored but never displayed in UI**

### PromptTemplate Entity (UNUSED)

| Field | Column | Used | Notes |
|-------|--------|------|-------|
| ALL FIELDS | - | ❌ **Admin CRUD only** | **ENTIRE ENTITY UNUSED** - prompts use AiScenario.llmPromptTemplate |

**⚠️ DEAD ENTITY:** Repository queries exist but values never used in LLM prompt building.

### IntentConfig Entity (UNUSED)

| Field | Column | Used | Notes |
|-------|--------|------|-------|
| ALL FIELDS | - | ❌ **Admin CRUD only** | **ENTIRE ENTITY UNUSED** - intent detection uses LLM not training phrases |

**⚠️ DEAD ENTITY:** trainingPhrases, confidenceThreshold, etc. never used in SpringAiLlmClient.

### PolicyRule Entity (UNUSED)

| Field | Column | Used | Notes |
|-------|--------|------|-------|
| ALL FIELDS | - | ❌ **Admin CRUD only** | **ENTIRE ENTITY UNUSED** - no policy evaluation engine exists |

**⚠️ DEAD ENTITY:** ruleExpression never evaluated, onFail never applied.

---

## Frontend-Backend Contract Mismatches

### 1. ChatRequest Fields Not Sent

```
BACKEND EXPECTS:        FRONTEND SENDS:
─────────────────       ────────────────
userId          ───────────▶ ✅ userId
query           ───────────▶ ✅ query  
sessionId       ───────────▶ ✅ sessionId
requestType     ───────────▶ ❌ undefined (defaults to null)
confirmed       ───────────▶ ❌ undefined (defaults to null)
selectedOption  ───────────▶ ❌ undefined (defaults to null)
pendingActionParams ──────▶ ❌ undefined (defaults to null)
pendingScenario ───────────▶ ❌ undefined (defaults to null)
dryRun          ───────────▶ ❌ undefined (defaults to false)
```

**CONSEQUENCE:** Confirmation and clarification flows are completely broken:
- `isConfirmationResponse()` ALWAYS returns false
- `isClarificationResponse()` ALWAYS returns false
- Pending action handling NEVER triggers

### 2. ChatResponse Fields Not Used

```
BACKEND SENDS:          FRONTEND USES:
─────────────────       ────────────────
sessionId       ───────────▶ ✅ sessionId
message         ───────────▶ ⚠️ Only if structured is null
responseType    ───────────▶ ❌ Ignored (uses structured.type)
followUpRequired ─────────▶ ❌ Ignored
missingParams   ───────────▶ ❌ Ignored (uses structured.payload)
scenario        ───────────▶ ❌ Ignored (uses structured.scenario)
possibleScenarios ────────▶ ❌ NEVER used
confidence      ───────────▶ ❌ Ignored
pendingAction   ───────────▶ ❌ NEVER used
meta            ───────────▶ ⚠️ Only executionId used
```

**CONSEQUENCE:** 7 of 10 response fields are ignored by frontend.

### 3. SSE Event Types

```
BACKEND SENDS:          FRONTEND HANDLES:
─────────────────       ────────────────
start           ───────────▶ ✅ Handled
progress        ───────────▶ ✅ Handled
response        ───────────▶ ✅ Handled
message         ───────────▶ ✅ Handled (legacy)
done            ───────────▶ ✅ Handled
error           ───────────▶ ✅ Handled
followup        ───────────▶ ✅ Handled
unknown         ───────────▶ ✅ Handled
```

**SSE events properly aligned ✅**

---

## Method Name vs Implementation Audit

### Critical Mismatches

| Class | Method | Name Implies | Actually Does | Bug? |
|-------|--------|--------------|---------------|------|
| `RbacService` | `anyRoleAuthorized(roles, scenario)` | Returns true if any role is authorized | Returns `!authorized` (true when NOT authorized) | **YES** |
| `ChatRequest` | `isConfirmationResponse()` | Checks if user confirmed | Returns `requestType == CONFIRMATION` but requestType is always null | **BROKEN** |
| `ChatRequest` | `isClarificationResponse()` | Checks if user clarified | Returns `requestType == CLARIFICATION` but requestType is always null | **BROKEN** |
| `RowLevelSecurityService` | `applyRowLevelSecurity()` | Applies security filters | Method exists but is NEVER called | **UNUSED** |
| `DataSourceRegistryService` | `resolveByDbKey()` | Resolves datasource | Method exists but is NEVER called | **UNUSED** |
| `JwtService` | `isInsecureSecret()` | Checks if secret is insecure | Always returns `false` due to `if(true)` block | **SECURITY BUG** |
| `IntentResult` | `isAmbiguous()` | Checks if intent is ambiguous | Correct implementation | OK |
| `IntentResult` | `hasAllRequiredParams()` | Checks if all params present | Correct implementation | OK |

### Methods with Misleading Names

| Class | Method | Issue |
|-------|--------|-------|
| `QueryExecutor` | `applyMandatoryMasking()` | Name suggests it's required, but method is NEVER called |
| `ConfigCacheService` | `refreshAllCaches()` | Suggests all caches, actually only refreshes 3 specific caches |
| `ReactiveChatService` | `processChatStreaming()` | Returns `Flux<String>` not `Flux<ServerSentEvent>` |

---

## Database Insert/Read Analysis

### What Gets INSERTed

| Table | When Inserted | Fields Set | Fields Left NULL |
|-------|--------------|------------|------------------|
| `chat_sessions` | On first message | sessionId, userId, createdAt, lastActivityAt | - |
| `chat_messages` | Every message | sessionId, role, content, timestamp | - |
| `ai_audit_logs` | After each request | executionId, userId, scenarioCode, requestTime, responseTime, success, errorMessage, rawIntentJson, rawResultJson | - |

### What Gets SELECTed

| Table | When Selected | Fields Used | Fields Ignored |
|-------|--------------|-------------|----------------|
| `ai_scenarios` | On each request (cached) | scenarioCode, description, executionType, sqlQuery, httpUrl, httpMethod, requestMapping, requiredParams, timeoutMs, llmPromptTemplate, active | dbKey, httpHeaders, executorBean, securityLevel, optionalParams, promptVersion, promptHistory |
| `role_scenario_map` | On startup + cache refresh | roleName, scenarioCode | - |
| `ai_response_mappings` | Per scenario | scenarioCode, sourceField, targetField, jsonPath, maskingType, displayOrder, active | sourceType |
| `chat_messages` | For context | sessionId, role, content, timestamp | - |

### Fields That Are Stored But Never Read

1. **ai_scenarios.db_key** - Stored, never used for routing
2. **ai_scenarios.http_headers** - Stored, never parsed
3. **ai_scenarios.executor_bean** - Stored, legacy field
4. **ai_scenarios.security_level** - Stored, never checked
5. **ai_scenarios.prompt_version** - Stored, never read
6. **ai_scenarios.prompt_history** - Stored, never read
7. **ai_audit_logs.raw_intent_json** - Stored, never displayed
8. **ai_audit_logs.raw_result_json** - Stored, never displayed
9. **ai_response_mappings.source_type** - Stored, never filtered

---

## Dead Code & Unused Fields Summary

### Complete Dead Code List

| Category | Item | Location | Type | Impact |
|----------|------|----------|------|--------|
| **Entity** | PromptTemplate | ai-orchestrator-data | Entire entity | Admin CRUD exists but values never used |
| **Entity** | IntentConfig | ai-orchestrator-data | Entire entity | Admin CRUD exists but values never used |
| **Entity** | PolicyRule | ai-orchestrator-data | Entire entity | Admin CRUD exists but values never used |
| **Field** | AiScenario.dbKey | ai-orchestrator-data | Column | DataSourceRegistryService never called |
| **Field** | AiScenario.httpHeaders | ai-orchestrator-data | Column | Never parsed or injected |
| **Field** | AiScenario.executorBean | ai-orchestrator-data | Column | Legacy hardcoded pattern |
| **Field** | AiScenario.securityLevel | ai-orchestrator-data | Column | No security check uses this |
| **Field** | AiScenario.promptVersion | ai-orchestrator-data | Column | Versioning not implemented |
| **Field** | AiScenario.promptHistory | ai-orchestrator-data | Column | History never queried |
| **Field** | ChatRequest.requestType | ai-orchestrator-common | DTO field | Frontend never sets it |
| **Field** | ChatRequest.confirmed | ai-orchestrator-common | DTO field | Frontend never sets it |
| **Field** | ChatRequest.selectedOption | ai-orchestrator-common | DTO field | Frontend never sets it |
| **Field** | ChatRequest.pendingActionParams | ai-orchestrator-common | DTO field | Frontend never sets it |
| **Field** | ChatRequest.pendingScenario | ai-orchestrator-common | DTO field | Frontend never sets it |
| **Field** | ChatResponse.possibleScenarios | ai-orchestrator-common | DTO field | Frontend never uses it |
| **Field** | ChatResponse.pendingAction | ai-orchestrator-common | DTO field | Frontend never uses it |
| **Method** | QueryExecutor.applyMandatoryMasking() | ai-orchestrator-core | Method | Never called |
| **Service** | DataSourceRegistryService | ai-orchestrator-core | Service | Injected but never used |
| **Service** | RowLevelSecurityService | ai-orchestrator-core | Service | Injected but never called |
| **Service** | UserRateLimitingService | ai-orchestrator-api | Service | Never applied to controllers |
| **Class** | ScenarioRouter | ai-orchestrator-core | Class | Superseded by DynamicScenarioRouter |
| **Class** | DataMaskingUtil | ai-orchestrator-common | Utility | Superseded by MaskingService |
| **Class** | IntentSchemaValidator | ai-orchestrator-llm | Validator | Never invoked |

### Impact Analysis

| Impact Level | Count | Action Required |
|--------------|-------|-----------------|
| **Critical** (Security/Data) | 3 | Immediate fix |
| **High** (Feature Broken) | 5 | Short-term fix |
| **Medium** (Code Bloat) | 10 | Cleanup recommended |
| **Low** (Minor) | 5 | Optional cleanup |

---

## Recommendations by Priority

### P0 - Critical (Fix Immediately)

1. **Fix RBAC inverted logic**
   ```java
   // RbacService.java line 174
   // BEFORE: return !authorized;
   // AFTER:  return authorized;
   ```

2. **Enable RowLevelSecurityService**
   ```java
   // QueryExecutor.java - add after line 70
   String securedSql = rowLevelSecurityService.applyRowLevelSecurity(sqlQuery, RequestContextHolder.getContext());
   // Use securedSql instead of sqlQuery
   ```

3. **Fix JWT security bypass**
   ```java
   // JwtService.java - remove lines 73-77
   // if(true){
   //     log.info("Remove this BLOCKER...");
   //     return false;
   // }
   ```

### P1 - High (Fix This Week)

1. **Fix frontend-backend contract for confirmation flow**
   - Add missing fields to frontend ChatRequest interface
   - Implement pendingContext sending in chat.component.ts
   
2. **Remove mock authentication fallback in production**
   - AuthService.ts catchError block should throw in production

3. **Add RequestContextHolder cleanup**
   - JwtAuthenticationFilter needs finally block

### P2 - Medium (Fix This Sprint)

1. **Remove dead entities**
   - PromptTemplate, IntentConfig, PolicyRule
   - Their repositories and admin controllers
   
2. **Remove dead AiScenario fields**
   - dbKey, httpHeaders, executorBean, securityLevel, promptVersion, promptHistory
   - Or implement their intended functionality

3. **Remove dead services**
   - DataMaskingUtil, IntentSchemaValidator
   - ScenarioRouter (keep DynamicScenarioRouter)

### P3 - Low (Technical Debt)

1. **Align ChatResponse fields with frontend usage**
2. **Add display for rawIntentJson, rawResultJson in admin**
3. **Implement UserRateLimitingService**

---

**End of Deep Dive Parameter-Level Audit**
