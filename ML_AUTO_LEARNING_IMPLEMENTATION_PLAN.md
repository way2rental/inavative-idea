# ML Auto-Learning Implementation Plan

## 🎯 **Goal: ML-Based System with Auto-Learning**

**Current Problem:**
- Too many rule-based configurations in database (patterns, rules, keywords)
- Requires manual configuration for each scenario/entity
- Difficult to maintain and scale

**Target Solution:**
- ML models that learn automatically from user interactions
- Minimal configuration needed
- Focus on core functionality instead of rules

---

## 📊 **ML Models to Implement**

### 1. **Intent Classifier Model** (Priority 1 - Highest Impact)
- **Purpose**: Automatically classify user queries to scenarios
- **Input**: User query + context
- **Output**: Scenario code + confidence
- **Auto-learning**: Learn from user queries and feedback
- **Replaces**: Rule-based intent detection (keywords, rules, patterns)

### 2. **NER Model** (Priority 2 - High Impact)
- **Purpose**: Automatically extract entities from queries
- **Input**: User query
- **Output**: Entities (ACCOUNT_ID, AMOUNT, DATE, etc.)
- **Auto-learning**: Learn from labeled entity examples
- **Replaces**: Regex patterns in database

### 3. **Response Generator Model** (Priority 3 - Medium Impact)
- **Purpose**: Generate natural responses automatically
- **Input**: Scenario + data + context
- **Output**: Natural language response
- **Auto-learning**: Learn from user feedback (good/bad responses)
- **Replaces**: Response templates in database

---

## 🏗️ **Architecture Design**

### ML Models Infrastructure

```
Training Pipeline:
  User Queries → Labeling → Training Data → Model Training → Model Export → Deployment

Active Learning:
  User Queries → Prediction → User Feedback → Training Queue → Retraining → New Model

Inference Pipeline:
  User Query → ML Models → Predictions → Post-processing → Response
```

### Components Needed

1. **Training Data Collection**
   - Collect user queries
   - Collect user feedback
   - Label data (queries → scenarios, entities)

2. **ML Model Training**
   - Fine-tune BERT/DistilBERT for intent classification
   - Fine-tune BERT for NER
   - Training pipeline (Python/Java)

3. **Model Storage & Versioning**
   - Store models (ONNX format)
   - Version management
   - A/B testing support

4. **Active Learning System**
   - Collect uncertain predictions
   - Collect user feedback
   - Retrain models periodically

5. **Model Inference**
   - Load models (ONNX Runtime)
   - Run inference
   - Cache predictions

---

## 🚀 **Implementation Phases**

### Phase 1: Training Data Collection ✅
- Create `TrainingDataCollectionService`
- Create `ai_training_data` table
- Collect user queries and labels
- Collect user feedback

### Phase 2: ML Model Training Pipeline ✅
- Create training scripts (Python)
- Fine-tune BERT for intent classification
- Fine-tune BERT for NER
- Export models to ONNX

### Phase 3: Model Storage & Management ✅
- Enhance `ai_ml_models` table
- Create `MlModelService`
- Model upload/download
- Model versioning

### Phase 4: ML Model Inference ✅
- Create `MlModelLoaderService` (ONNX Runtime)
- Create `MlIntentClassifierService`
- Create `MlNerService`
- Integrate with existing pipeline

### Phase 5: Active Learning ✅
- Create `ActiveLearningService`
- Collect uncertain predictions
- Retrain models automatically
- Deploy new models

### Phase 6: Integration & Migration ✅
- Replace rule-based intent detection with ML
- Replace regex patterns with ML NER
- Fallback to rules if ML fails
- A/B testing between ML and rules

---

## 📋 **Database Schema Updates**

### Training Data Table
```sql
CREATE TABLE ai_training_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_query TEXT NOT NULL,
    scenario_code VARCHAR(100),
    entities JSON,  -- Extracted entities
    labels JSON,    -- Labels from admin/user
    feedback BOOLEAN,  -- User feedback (good/bad)
    confidence DECIMAL(3,2),
    used_for_training BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scenario (scenario_code),
    INDEX idx_used_for_training (used_for_training)
);
```

### Model Training Queue
```sql
CREATE TABLE ai_model_training_queue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_type VARCHAR(50),  -- INTENT, NER, RESPONSE
    status VARCHAR(50),      -- PENDING, TRAINING, COMPLETED, FAILED
    training_data_count INT,
    accuracy_metrics JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
```

---

## 🎯 **Expected Benefits**

### Before (Rule-based):
- 100+ patterns in database
- 50+ rules in database
- 200+ keywords in database
- Manual configuration for each scenario
- Difficult to maintain

### After (ML-based):
- 3 ML models (intent, NER, response)
- Minimal configuration (just model files)
- Automatic learning from user data
- Self-improving over time
- Focus on core functionality

---

## ✅ **Next Steps**

1. Create training data collection system
2. Create ML model training pipeline
3. Implement ML model inference
4. Implement active learning
5. Migrate from rules to ML gradually
