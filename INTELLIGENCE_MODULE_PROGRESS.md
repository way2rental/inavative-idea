# Intelligence Module - Implementation Progress

## ✅ Completed Tasks

### 1. Backup Existing LLM Module
- ✅ Backed up `ai-orchestrator-llm` → `ai-orchestrator-llm-backup`
- ✅ Original LLM module preserved for reference

### 2. Database Schema (Fully DB-Driven)
- ✅ Created migration: `V2__intelligence_fallback_layers.sql`
- ✅ Tables created:
  - `ai_fallback_layers` - Fallback layer configuration
  - `ai_ml_models` - ML model configuration
  - `ai_scenario_embeddings` - Pre-computed scenario embeddings
  - `ai_entity_patterns` - Entity recognition patterns (DB-driven NER)
  - `ai_rule_engine_rules` - Rule engine rules (DB-driven)
  - `ai_keyword_patterns` - Keyword matching patterns
  - `ai_response_templates` - Response templates (Freemarker)
  - `ai_context_memory` - Session-based context memory
  - `ai_followup_templates` - Follow-up question templates
  - `ai_conversational_responses` - Conversational responses (greetings, etc.)
  - `ai_failure_logs` - Failure tracking & edge case logging
  - `ai_training_queue` - Active learning queue
  - `ai_model_metrics` - Model performance metrics

### 3. JPA Entities Created
- ✅ `FallbackLayer.java` - Fallback layer configuration entity
- ✅ `MlModel.java` - ML model configuration entity
- ✅ `ScenarioEmbedding.java` - Scenario embedding entity
- ✅ `RuleEngineRule.java` - Rule engine rule entity
- ✅ `KeywordPattern.java` - Keyword pattern entity
- ✅ `ConversationalResponse.java` - Conversational response entity

### 4. Module Structure
- ✅ Created `ai-orchestrator-intelligence` module
- ✅ Created `pom.xml` with all required dependencies:
  - ONNX Runtime (ML models)
  - Deep Java Library (Model loading)
  - Sentence Transformers (Embeddings)
  - HNSWLib (Vector search)
  - Freemarker (Templates)
  - Commons Text/Math (Utilities)
  - Natty (Date parsing)
- ✅ Added module to parent POM

## 🔄 In Progress

### 5. Repositories (Next)
- ⏳ `FallbackLayerRepository.java`
- ⏳ `MlModelRepository.java`
- ⏳ `ScenarioEmbeddingRepository.java`
- ⏳ `RuleEngineRuleRepository.java`
- ⏳ `KeywordPatternRepository.java`
- ⏳ `ConversationalResponseRepository.java`

### 6. Intelligence Client Interface
- ⏳ `ReactiveIntelligenceClient.java` - Implements `ReactiveLlmClient` interface
- ⏳ DB-driven fallback architecture

### 7. Fallback Layer Implementations
- ⏳ `MlIntentClassifier.java` - ML-based intent detection
- ⏳ `EmbeddingMatcher.java` - Embedding-based similarity
- ⏳ `RuleEngineMatcher.java` - Rule-based matching
- ⏳ `KeywordMatcher.java` - Keyword-based matching
- ⏳ `ConversationalHandler.java` - Catch-all conversational handler

## 📋 Architecture Overview

### Fully DB-Driven Fallback Architecture

```
User Query
    ↓
┌─────────────────────────────────────┐
│ Layer 1: ML Intent Classifier       │
│ ─────────────────────────────────── │
│ • Config: ai_fallback_layers        │
│ • Model: ai_ml_models               │
│ • Threshold: confidence_threshold   │
│ • Fallback if: confidence < 0.85    │
└─────────────────────────────────────┘
    ↓ [Success]           ↓ [Failure]
    │                     │
    ✓                  ┌─────────────────────────────────────┐
                       │ Layer 2: Embedding Similarity       │
                       │ ─────────────────────────────────── │
                       │ • Config: ai_fallback_layers        │
                       │ • Embeddings: ai_scenario_embeddings│
                       │ • Threshold: confidence_threshold   │
                       │ • Fallback if: similarity < 0.80    │
                       └─────────────────────────────────────┘
                           ↓ [Success]       ↓ [Failure]
                           │                 │
                           ✓              ┌─────────────────────────────────────┐
                                          │ Layer 3: Rule Engine                │
                                          │ ─────────────────────────────────── │
                                          │ • Config: ai_fallback_layers        │
                                          │ • Rules: ai_rule_engine_rules       │
                                          │ • Threshold: confidence_threshold   │
                                          │ • Fallback if: no matching rule     │
                                          └─────────────────────────────────────┘
                                              ↓ [Success]   ↓ [Failure]
                                              │             │
                                              ✓          ┌─────────────────────────────────────┐
                                                         │ Layer 4: Keyword Matcher            │
                                                         │ ─────────────────────────────────── │
                                                         │ • Config: ai_fallback_layers        │
                                                         │ • Keywords: ai_keyword_patterns     │
                                                         │ • Threshold: confidence_threshold   │
                                                         │ • Always confirm if confidence low  │
                                                         └─────────────────────────────────────┘
                                                             ↓ [Success]   ↓ [Failure]
                                                             │             │
                                                             ✓          ┌─────────────────────────────────────┐
                                                                        │ Layer 5: Conversational Handler      │
                                                                        │ ─────────────────────────────────── │
                                                                        │ • Config: ai_fallback_layers        │
                                                                        │ • Responses: ai_conversational_      │
                                                                        │   responses                         │
                                                                        │ • Templates: Freemarker with         │
                                                                        │   SystemConfig variables             │
                                                                        └─────────────────────────────────────┘
                                                                            ↓
                                                                            ✓
                                                                    [Never Fails]
```

### Key Principles

1. **NO HARDCODING** - Everything configurable via database
2. **Admin Panel Manageable** - All configuration via Admin Panel
3. **Fully Generic** - No domain-specific hardcoding (no "banking", "account", etc.)
4. **Multi-Layer Fallback** - Never fails completely
5. **DB-Driven Templates** - Freemarker templates with SystemConfig variables
6. **Active Learning** - Automatic failure tracking and training queue

## 🎯 Next Steps

1. Create repositories for all entities
2. Implement `ReactiveIntelligenceClient` interface
3. Implement fallback layer services (ML, Embedding, Rules, Keywords, Conversational)
4. Create service layer for fallback layer management
5. Remove hardcoded "banking"/"account" references from existing code
6. Update ChatService to use new intelligence client
7. Create Admin Panel UI for fallback layer configuration

## 📝 Notes

- All fallback layers are configured via `ai_fallback_layers` table
- Priority determines execution order (1=highest)
- Each layer can be enabled/disabled via `enabled` flag
- Confidence thresholds are configurable per layer
- All responses use Freemarker templates with SystemConfig variables ({{assistantName}}, {{orgName}}, etc.)
