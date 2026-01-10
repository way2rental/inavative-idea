# Frontend Updates Needed

## ✅ Completed Updates

1. **Login Component** - Updated "Powered by Ollama LLM" → "Powered by Intelligent AI System"
2. **Scenarios Component** - Updated "LLM Prompt Template" → "Response Template (Freemarker)"
3. **Settings Component** - Updated "LLM Configuration" → "Intelligence System Configuration"

## 📋 Remaining Frontend Updates

### 1. Admin Panel - Intelligence Configuration

**File:** `ai-orchestrator-ui/src/app/components/admin/intelligence/`

**New Component Needed:**
- `intelligence-layers.component.ts/html` - Configure fallback layers
- `intelligence-rules.component.ts/html` - Manage rules
- `intelligence-keywords.component.ts/html` - Manage keywords
- `intelligence-embeddings.component.ts/html` - Generate/view embeddings
- `intelligence-models.component.ts/html` - Manage ML models

**Features:**
- Enable/disable fallback layers
- Configure confidence thresholds
- Set layer priorities
- View layer performance metrics
- Add/edit/delete rules
- Add/edit/delete keywords
- Generate embeddings for scenarios
- Upload/configure ML models

### 2. Admin Panel - Entity Patterns

**New Component:** `entity-patterns.component.ts/html`

**Features:**
- Configure entity patterns (regex, NLP patterns)
- Add custom entity types
- Test entity extraction
- View extraction examples

### 3. Admin Panel - Training Data

**New Component:** `training-data.component.ts/html`

**Features:**
- View collected training data
- Label queries (intent + entities)
- Export labeled data
- Trigger model retraining
- View model performance metrics

### 4. Admin Panel - Context Memory

**New Component:** `context-memory.component.ts/html`

**Features:**
- View stored context memory
- Manage entity references
- Clear old memory
- Configure memory TTL

### 5. Chat Component Enhancements

**File:** `ai-orchestrator-ui/src/app/components/chat/chat.component.ts`

**Updates Needed:**
- Show which intelligence layer matched (for debugging)
- Display entity extraction results (optional)
- Show confidence scores (optional admin view)
- Better error messages when entity extraction fails

### 6. Models Updates

**File:** `ai-orchestrator-ui/src/app/models/admin.model.ts`

**Updates Needed:**
- Add `FallbackLayer` model
- Add `RuleEngineRule` model
- Add `KeywordPattern` model
- Add `ScenarioEmbedding` model
- Add `MlModel` model
- Add `EntityPattern` model
- Add `ContextMemory` model
- Add `TrainingData` model

### 7. Admin Service Updates

**File:** `ai-orchestrator-ui/src/app/services/admin.service.ts`

**New Methods Needed:**
```typescript
// Fallback Layers
getFallbackLayers(): Observable<FallbackLayer[]>
updateFallbackLayer(layer: FallbackLayer): Observable<FallbackLayer>
refreshLayers(): Observable<void>

// Rules
getRules(): Observable<RuleEngineRule[]>
createRule(rule: RuleEngineRule): Observable<RuleEngineRule>
updateRule(id: number, rule: RuleEngineRule): Observable<RuleEngineRule>
deleteRule(id: number): Observable<void>

// Keywords
getKeywords(scenarioCode?: string): Observable<KeywordPattern[]>
createKeyword(keyword: KeywordPattern): Observable<KeywordPattern>
updateKeyword(id: number, keyword: KeywordPattern): Observable<KeywordPattern>
deleteKeyword(id: number): Observable<void>

// Embeddings
getEmbeddings(): Observable<ScenarioEmbedding[]>
generateEmbedding(scenarioCode: string): Observable<ScenarioEmbedding>
generateAllEmbeddings(): Observable<void>

// ML Models
getMlModels(): Observable<MlModel[]>
uploadModel(model: File, metadata: MlModel): Observable<MlModel>
activateModel(id: number): Observable<MlModel>

// Entity Patterns
getEntityPatterns(): Observable<EntityPattern[]>
createEntityPattern(pattern: EntityPattern): Observable<EntityPattern>
updateEntityPattern(id: number, pattern: EntityPattern): Observable<EntityPattern>
deleteEntityPattern(id: number): Observable<void>

// Training Data
getTrainingData(): Observable<TrainingData[]>
labelQuery(id: number, label: TrainingLabel): Observable<TrainingData>
exportTrainingData(): Observable<Blob>
triggerRetraining(): Observable<void>

// Context Memory
getContextMemory(sessionId?: string): Observable<ContextMemory[]>
clearContextMemory(sessionId?: string): Observable<void>
```

### 8. API Service Updates

**File:** `ai-orchestrator-ui/src/app/services/api.service.ts`

**Verify:** API endpoints match backend:
- `/api/v2/chat` - ✅ Already exists
- `/api/v2/chat/stream` - ✅ Already exists
- `/api/admin/intelligence/layers` - ❌ Need to add
- `/api/admin/intelligence/rules` - ❌ Need to add
- `/api/admin/intelligence/keywords` - ❌ Need to add
- `/api/admin/intelligence/embeddings` - ❌ Need to add
- `/api/admin/intelligence/models` - ❌ Need to add
- `/api/admin/entity-patterns` - ❌ Need to add
- `/api/admin/training-data` - ❌ Need to add
- `/api/admin/context-memory` - ❌ Need to add

---

## 🎯 Priority

### High Priority (Week 1)
1. ✅ Update text references (LLM → Intelligence System)
2. Create intelligence layers admin component
3. Create rules admin component
4. Create keywords admin component

### Medium Priority (Week 2)
5. Create embeddings admin component
6. Create entity patterns admin component
7. Update admin service with new methods

### Low Priority (Week 3+)
8. Create training data admin component
9. Create context memory admin component
10. Create ML models admin component
11. Chat component enhancements

---

## 📝 Notes

- Frontend is mostly independent of backend changes (uses REST API)
- Most updates are cosmetic (LLM → Intelligence System)
- New admin components needed for configuration
- No breaking changes to chat functionality
- Backward compatible with existing API

---

**Frontend updates are mainly for admin panel configuration of the new intelligence system!**
