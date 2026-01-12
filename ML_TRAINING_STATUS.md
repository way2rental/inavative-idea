# ML and Training Status

## 🎯 **Current ML Capabilities**

### ✅ **1. Embedding Model (Pre-trained, NOT trained by us)**

**Model**: `all-MiniLM-L6-v2` (from sentence-transformers)
- **Type**: Pre-trained embedding model
- **Provider**: Microsoft/sentence-transformers
- **Library**: langchain4j-embeddings-all-minilm-l6-v2
- **Dimensions**: 384-dimensional embeddings
- **Purpose**: Generate semantic embeddings for text similarity

**Usage:**
- ✅ **EmbeddingGenerationService**: Generates embeddings for scenarios (scenario name + description + examples)
- ✅ **EmbeddingMatcher**: Uses embeddings for semantic similarity-based intent detection
- ✅ **DomainRetriever**: Uses embeddings for RAG (Retrieval-Augmented Generation)

**Training Status:**
- ❌ **NOT trained by us** - This is a pre-trained model
- ✅ **Used for inference only** - We use it to generate embeddings
- ❌ **No fine-tuning** - We use it as-is
- ✅ **No training data required** - Pre-trained model works out-of-the-box

**How it works:**
1. Model is loaded automatically (lazy initialization)
2. Takes text as input (scenario name, description, user query)
3. Generates 384-dimensional embedding vector
4. Embeddings stored in database for fast similarity search

---

### ⏳ **2. ML Models Infrastructure (Ready, but no models yet)**

**Infrastructure:**
- ✅ **Database table**: `ai_ml_models` (created in V2 migration)
- ✅ **Entity**: `MlModel.java` (JPA entity exists)
- ✅ **Planned support**: ONNX models (BERT, DistilBERT for intent classification, NER)

**Status:**
- ❌ **No ML models trained yet**
- ❌ **No ML model files uploaded**
- ✅ **Infrastructure ready** - Table, entity, and placeholder code exist
- ⏳ **Future enhancement** - Can add ONNX models when needed

**Planned ML Models (Future):**
1. **Intent Classifier** (ONNX format)
   - Model type: INTENT
   - Format: ONNX
   - Purpose: ML-based intent detection (higher accuracy than rule-based)
   - Status: ⏳ Not implemented yet

2. **NER Model** (ONNX format)
   - Model type: NER (Named Entity Recognition)
   - Format: ONNX
   - Purpose: ML-based entity extraction (alternative to regex patterns)
   - Status: ⏳ Not implemented yet

3. **Coreference Resolution Model** (Future)
   - Model type: COREFERENCE
   - Purpose: Resolve "same account", "that transaction" references
   - Status: ⏳ Not implemented yet

---

### ✅ **3. Entity Extraction (Regex-based, NOT ML-based)**

**Current Implementation:**
- ✅ **Pattern-based extraction** - Uses regex patterns from database
- ✅ **Entity patterns table**: `ai_entity_patterns`
- ✅ **No ML training required** - Regex patterns configured manually
- ✅ **Admin Panel manageable** - Patterns can be added/updated via admin panel

**Status:**
- ✅ **Working** - Entity extraction uses regex patterns
- ⚠️ **Needs patterns** - Entity patterns table needs to be populated
- ✅ **No hardcoding** - All patterns from database
- ✅ **No training** - Patterns are rules, not ML models

**How it works:**
1. Patterns stored in database (regex patterns)
2. EntityExtractionService loads patterns
3. EntityPatternMatcher matches patterns against user query
4. Extracted entities validated by EntityValidator
5. Entities mapped to parameters

---

## 📊 **Training Status Summary**

| Component | ML Model | Training Status | Training Data | Notes |
|-----------|----------|-----------------|---------------|-------|
| **Embeddings** | all-MiniLM-L6-v2 | ✅ Pre-trained (external) | N/A | Pre-trained model, no training by us |
| **Intent Detection** | EmbeddingMatcher | ✅ No training needed | Scenario embeddings | Uses pre-computed embeddings |
| **Entity Extraction** | Regex patterns | ✅ No training needed | N/A | Rule-based, not ML |
| **Intent Classifier (ML)** | Planned (ONNX) | ❌ Not implemented | Future | Infrastructure ready, models not created |
| **NER Model (ML)** | Planned (ONNX) | ❌ Not implemented | Future | Infrastructure ready, models not created |

---

## 🔍 **What Training Exists?**

### ✅ **What We Have:**
1. **Pre-trained Embedding Model**
   - Model: all-MiniLM-L6-v2
   - Status: ✅ Working
   - Training: ❌ Not trained by us (external pre-trained model)
   - Usage: ✅ Generating embeddings for scenarios

2. **Infrastructure for ML Models**
   - Database table: `ai_ml_models`
   - Entity: `MlModel.java`
   - Status: ✅ Ready for future ML models

3. **Entity Patterns (Regex)**
   - Storage: `ai_entity_patterns` table
   - Status: ⚠️ Table exists but needs patterns
   - Training: ✅ No training needed (rules, not ML)

### ❌ **What We DON'T Have:**
1. **Custom-trained ML models**
   - No intent classifier model trained
   - No NER model trained
   - No custom training pipeline

2. **Training Data**
   - No labeled training dataset
   - No training data collection
   - No active learning system

3. **Training Infrastructure**
   - No training scripts
   - No model fine-tuning code
   - No model export pipeline

---

## 🎯 **How the System Works (Current State)**

### Current Approach: **Rule-based + Pre-trained Embeddings**

1. **Intent Detection:**
   - Uses pre-trained embeddings (all-MiniLM-L6-v2) for similarity
   - Pre-computes scenario embeddings (scenario name + description)
   - Compares user query embedding with scenario embeddings
   - Falls back to rule-based matching if embeddings fail

2. **Entity Extraction:**
   - Uses regex patterns (NOT ML)
   - Patterns stored in database
   - No training needed - patterns are rules

3. **No Custom Training:**
   - We don't train models
   - We use pre-trained models for embeddings
   - We use rule-based patterns for entities

---

## 🚀 **Future ML Enhancements (Not Implemented)**

1. **Fine-tune Intent Classifier**
   - Collect labeled data (user queries → scenarios)
   - Fine-tune BERT/DistilBERT
   - Export to ONNX
   - Replace rule-based intent detection

2. **Train NER Model**
   - Collect labeled entity data
   - Fine-tune BERT for NER
   - Export to ONNX
   - Use alongside regex patterns

3. **Active Learning**
   - Collect user queries
   - Collect user feedback
   - Retrain models periodically
   - Improve over time

---

## ✅ **Summary**

**Current ML Status:**
- ✅ **Pre-trained embeddings** (all-MiniLM-L6-v2) - Working
- ✅ **Infrastructure ready** (tables, entities) - Ready for future
- ❌ **No custom training** - No models trained by us
- ❌ **No training data** - No labeled datasets
- ✅ **Rule-based approach** - Entity extraction uses regex patterns

**Answer to "Where is our ML and what training we have provided to ML":**
- **ML Location**: Embedding model (all-MiniLM-L6-v2) in `EmbeddingGenerationService` and `EmbeddingMatcher`
- **Training Provided**: **NONE** - We use pre-trained models, no training by us
- **Training Data**: **NONE** - No training data collected or used
- **Status**: System uses pre-trained embeddings + rule-based patterns, NOT custom-trained ML models
