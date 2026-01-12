# Entity Usage Audit - Comprehensive Analysis

## 📋 **All Entities (29 total)**

1. AiAuditLog
2. AiResponseMapping
3. AiScenario
4. BankingConcept
5. ChatMessage
6. ChatSession
7. ContextMemory
8. ConversationalResponse
9. CorporateDatabaseMapping
10. DomainDocument
11. EntityPattern
12. FallbackLayer
13. FollowUpGroup
14. FollowUpTemplate
15. HttpUrlWhitelist
16. KeywordPattern
17. MessageFeedback
18. MlModel
19. PolicyRule
20. PromptTemplate
21. ResponseTemplate
22. RoleScenarioMap
23. RuleEngineRule
24. ScenarioEmbedding
25. ScenarioTestResult
26. SystemConfig
27. TrainingData
28. UserCorporateAssignment
29. UserTypeConfig

---

## ✅ **Entities Used in Business Logic (Intelligence Services)**

### **Core Business Logic:**
1. ✅ **AiScenario** - Used in: FallbackLayerOrchestrator, ParameterExtractionService, ScenarioTriggerMatcher, MlIntentClassifierService
2. ✅ **EntityPattern** - Used in: EntityExtractionService, EntityPatternMatcher
3. ✅ **ContextMemory** - Used in: ContextMemoryService, ReferenceResolver
4. ✅ **ScenarioEmbedding** - Used in: EmbeddingMatcher, EmbeddingGenerationService
5. ✅ **RuleEngineRule** - Used in: RuleEngineMatcher
6. ✅ **KeywordPattern** - Used in: KeywordMatcher
7. ✅ **ConversationalResponse** - Used in: ConversationalHandler
8. ✅ **FallbackLayer** - Used in: FallbackLayerOrchestrator
9. ✅ **ResponseTemplate** - Used in: ResponseFormatterService
10. ✅ **FollowUpGroup** - Used in: FollowUpQuestionService
11. ✅ **FollowUpTemplate** - Used in: FollowUpQuestionService
12. ✅ **BankingConcept** - Used in: BankingConceptService, ReasoningPlannerImpl
13. ✅ **DomainDocument** - Used in: DomainRetriever, RagEngineImpl
14. ✅ **PolicyRule** - Used in: ComplianceGuardImpl
15. ✅ **MlModel** - Used in: MlModelLoaderService, MlIntentClassifierService, MlNerService, MlTrainingService
16. ✅ **TrainingData** - Used in: TrainingDataCollectionService, ActiveLearningService, MlTrainingService

### **Used in API Services (Business Logic):**
17. ✅ **ChatSession** - Used in: ChatService, ReactiveChatService
18. ✅ **ChatMessage** - Used in: ChatService, ReactiveChatService
19. ✅ **AiAuditLog** - Used in: AuditService, AuditLogService
20. ✅ **AiResponseMapping** - Used in: ResponseMappingAdminService
21. ✅ **RoleScenarioMap** - Used in: RbacService, RbacManagementService
22. ✅ **SystemConfig** - Used in: SystemConfigService, ConfigCacheService
23. ✅ **HttpUrlWhitelist** - Used in: HTTP executor security checks
24. ✅ **MessageFeedback** - Used in: FeedbackController

---

## ⚠️ **Potentially Unused Entities (Need Verification)**

### **Check These:**
1. ⚠️ **PromptTemplate** - Used in: PromptAdminController (CRUD only), NOT in business logic?
2. ⚠️ **ScenarioTestResult** - Used in: ScenarioSandboxController (testing only), NOT in business logic?
3. ⚠️ **CorporateDatabaseMapping** - Used in: CorporateDatabaseService (CRUD only), NOT in business logic?
4. ⚠️ **UserCorporateAssignment** - Used in: ? (Need to check)
5. ⚠️ **UserTypeConfig** - Used in: ? (Need to check)

---

## 🔍 **Detailed Usage Check Required**
