# Intelligence Module - Implementation Plan

## Progress Summary

✅ **Completed:**
1. Database schema (V2 migration) - 13 tables
2. JPA entities - 8 entities
3. Repositories - 7 repositories
4. Module structure and pom.xml
5. ReactiveIntelligenceClient interface

🔄 **In Progress:**
- Fallback layer services implementation

## Implementation Strategy

### Phase 1: Core Fallback Services (Current)
1. ✅ EmbeddingMatcher - Vector similarity search
2. ✅ RuleEngineMatcher - DB-driven rules
3. ✅ KeywordMatcher - Keyword patterns
4. ✅ ConversationalHandler - Catch-all responses
5. ⏳ MlIntentClassifier - ML model (placeholder for now)

### Phase 2: Intelligence Client
- ReactiveIntelligenceClient implementation
- Multi-layer fallback orchestration
- Confidence-based routing
- Failure logging and tracking

### Phase 3: Integration
- Update ChatService to use ReactiveIntelligenceClient
- Update ReactiveChatService
- Remove Spring AI dependencies
- Update application.yml

### Phase 4: Cleanup
- Remove hardcoded "banking"/"account" references
- Update all prompts to be DB-driven
- Create Admin Panel UI for configuration

## Architecture

```
ReactiveIntelligenceClient
    ↓
FallbackLayerOrchestrator
    ↓
┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐
│   ML    │→│Embedding │→│  Rules   │→│ Keywords │→│Conversational│
│ Layer   │  │  Layer   │  │  Layer   │  │  Layer   │  │   Layer     │
└─────────┘  └──────────┘  └──────────┘  └──────────┘  └─────────────┘
    ↓              ↓              ↓              ↓              ↓
DB Config    DB Embeddings   DB Rules    DB Keywords   DB Templates
```

Each layer:
- Reads configuration from `ai_fallback_layers`
- Returns `Optional<IntentResult>` (None = fallback to next layer)
- Logs failures for active learning
- Uses DB-driven configuration (no hardcoding)
