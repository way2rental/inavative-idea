# Unused Entities Analysis - Business Logic Verification

## 🔍 **Analysis Methodology**

1. ✅ Checked usage in **Intelligence Services** (business logic)
2. ✅ Checked usage in **API Controllers** (REST endpoints)
3. ✅ Checked usage in **Core Services** (QueryExecutor, etc.)
4. ✅ Checked usage in **Frontend** (Angular services/components)

---

## ❌ **UNUSED ENTITIES (Not Used in Business Logic)**

### **1. PromptTemplate** ❌
- **Status:** UNUSED in business logic
- **Used in:** Only `PromptAdminController` (CRUD only)
- **Not used in:** Intelligence services, core services
- **Reason:** System uses `AiScenario.llmPromptTemplate` instead
- **Action:** Can be removed (or keep for future use if planned)

### **2. CorporateDatabaseMapping** ❌
- **Status:** UNUSED in business logic
- **Used in:** Only `CorporateDatabaseService` (CRUD only)
- **Not used in:** `QueryExecutor`, `DataSourceRegistryService` (doesn't use it)
- **Reason:** Multi-database routing is deferred, `DataSourceRegistryService` doesn't use this entity
- **Action:** Can be removed (or keep if multi-database feature is planned)

### **3. UserCorporateAssignment** ❌
- **Status:** UNUSED
- **Used in:** NO controllers, NO services
- **Not used in:** Anywhere
- **Reason:** Feature not implemented
- **Action:** REMOVE

### **4. UserTypeConfig** ❌
- **Status:** UNUSED
- **Used in:** NO controllers, NO services
- **Not used in:** Anywhere
- **Reason:** Feature not implemented
- **Action:** REMOVE

---

## ⚠️ **TESTING/ADMIN ONLY (Not Business Logic)**

### **5. ScenarioTestResult** ⚠️
- **Status:** Used in testing only
- **Used in:** `ScenarioSandboxController` (testing/debugging)
- **Not used in:** Business logic (intelligence services)
- **Reason:** Testing/debugging feature
- **Action:** KEEP (useful for admin testing)

---

## ✅ **USED ENTITIES (Keep These)**

### **Core Business Logic:**
1. ✅ **AiScenario** - Used in: FallbackLayerOrchestrator, ParameterExtractionService, etc.
2. ✅ **EntityPattern** - Used in: EntityExtractionService
3. ✅ **ContextMemory** - Used in: ContextMemoryService
4. ✅ **ScenarioEmbedding** - Used in: EmbeddingMatcher
5. ✅ **RuleEngineRule** - Used in: RuleEngineMatcher
6. ✅ **KeywordPattern** - Used in: KeywordMatcher
7. ✅ **ConversationalResponse** - Used in: ConversationalHandler
8. ✅ **FallbackLayer** - Used in: FallbackLayerOrchestrator
9. ✅ **ResponseTemplate** - Used in: ResponseFormatterService
10. ✅ **FollowUpGroup** - Used in: FollowUpQuestionService
11. ✅ **FollowUpTemplate** - Used in: FollowUpQuestionService
12. ✅ **BankingConcept** - Used in: BankingConceptService, ReasoningPlanner
13. ✅ **DomainDocument** - Used in: DomainRetriever, RagEngine
14. ✅ **PolicyRule** - Used in: ComplianceGuardImpl
15. ✅ **MlModel** - Used in: MlModelLoaderService, MlIntentClassifierService
16. ✅ **TrainingData** - Used in: TrainingDataCollectionService, MlTrainingService

### **API/Business Services:**
17. ✅ **ChatSession** - Used in: ChatService, ReactiveChatService
18. ✅ **ChatMessage** - Used in: ChatService
19. ✅ **AiAuditLog** - Used in: AuditService
20. ✅ **AiResponseMapping** - Used in: ResponseMappingAdminService
21. ✅ **RoleScenarioMap** - Used in: RbacService, RbacManagementService
22. ✅ **SystemConfig** - Used in: SystemConfigService
23. ✅ **HttpUrlWhitelist** - Used in: HTTP executor security
24. ✅ **MessageFeedback** - Used in: FeedbackController

---

## 📊 **Summary**

**Total Entities:** 29
**Used in Business Logic:** 24
**Unused:** 4 (PromptTemplate, CorporateDatabaseMapping, UserCorporateAssignment, UserTypeConfig)
**Testing Only:** 1 (ScenarioTestResult)

---

## 🎯 **Recommendation**

### **Remove These Entities:**
1. ❌ **UserCorporateAssignment** - Not used anywhere
2. ❌ **UserTypeConfig** - Not used anywhere

### **Keep But Mark as Deferred:**
3. ⚠️ **CorporateDatabaseMapping** - For future multi-database feature
4. ⚠️ **PromptTemplate** - For future prompt management (currently using AiScenario.llmPromptTemplate)

### **Keep:**
5. ✅ **ScenarioTestResult** - Useful for admin testing/debugging

---

## ✅ **Frontend-Backend Alignment**

### **Frontend Uses:**
- ✅ Scenarios
- ✅ AuditLogs
- ✅ ChatSessions
- ✅ PromptTemplates (admin CRUD)
- ✅ ResponseTemplates
- ✅ FollowUpGroups
- ✅ PolicyRules
- ✅ ResponseMappings
- ✅ RBAC
- ✅ EntityPatterns
- ✅ ContextMemory
- ✅ DomainDocuments
- ✅ BankingConcepts
- ✅ ScenarioTestResult

### **Backend Provides:**
- ✅ All above entities have corresponding controllers
- ✅ Alignment is good

---

## 🔧 **Next Steps**

1. Remove unused entities (UserCorporateAssignment, UserTypeConfig)
2. Remove unused repositories
3. Remove unused migrations (if any)
4. Update documentation
5. Verify compilation
