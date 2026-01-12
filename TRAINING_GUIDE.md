# ML Model Training Guide - Simple Step-by-Step

## 🎯 **What This Does**

This guide will help you:
1. **Create training data** in the database
2. **Train ML models** (Intent Classifier and NER)
3. **Export models** to ONNX format
4. **Use models** in your Java application

---

## 📋 **Step 1: Create Training Data**

### **Option A: Use SQL Script (Easiest)**

Run the SQL script to create sample training data:

```bash
# Connect to your MySQL database
mysql -u root -p ai_orchestrator < create_training_data.sql
```

This creates:
- ✅ **50+ intent classification examples** (queries → scenario codes)
- ✅ **20+ NER examples** (queries → entity labels)

### **Option B: Add More Data Later**

You can add more training data through:
- **Admin Panel** - Label user queries
- **Database** - Insert into `ai_training_data` table
- **API** - Use `TrainingDataCollectionService`

---

## 🐍 **Step 2: Install Python Dependencies**

### **Install Python 3.8+**

```bash
# Check Python version
python --version  # Should be 3.8 or higher
```

### **Install Required Packages**

```bash
pip install torch transformers onnx transformers[onnx] datasets scikit-learn numpy pandas mysql-connector-python
```

**Note:** This might take a few minutes to download.

---

## ⚙️ **Step 3: Configure Database Connection**

Edit `train_models.py` and update the database configuration:

```python
DB_CONFIG = {
    'host': 'localhost',        # Your database host
    'port': 3306,               # Your database port
    'user': 'root',             # Your database user
    'password': 'your_password', # Your database password
    'database': 'ai_orchestrator' # Your database name
}
```

---

## 🚀 **Step 4: Train Models**

### **Run Training Script**

```bash
python train_models.py
```

### **What Happens:**

1. ✅ **Connects to database** - Loads training data
2. ✅ **Trains Intent Classifier** - Uses DistilBERT (faster than BERT)
3. ✅ **Exports to ONNX** - Creates `models/intent-classifier.onnx`
4. ✅ **Saves label mapping** - Creates `models/intent_label_mapping.json`
5. ✅ **Marks data as used** - Updates database

### **Expected Output:**

```
============================================================
🤖 ML Model Training Script
============================================================
✅ Connected to database
✅ Loaded 50 intent examples, 20 NER examples
✅ Intent labels: ['ACCOUNT_BALANCE', 'ACCOUNT_SUMMARY', ...]
✅ Number of classes: 5

🚀 Training Intent Classifier...
✅ Intent Classifier - Accuracy: 0.9500, F1: 0.9400
📦 Exporting to ONNX: ./models/intent-classifier.onnx
✅ Intent Classifier exported to ./models/intent-classifier.onnx

✅ Marked 50 training examples as used

============================================================
✅ Training Complete!
============================================================
📦 Model saved to: ./models/intent-classifier.onnx
📊 Accuracy: 0.9500
📊 F1 Score: 0.9400
```

---

## 📦 **Step 5: Deploy Model to Java Application**

### **Copy Model File**

Copy the ONNX model to your Java application:

```bash
# Copy model to your application's model directory
cp models/intent-classifier.onnx /path/to/your/app/models/intent-classifier-v1.onnx
```

### **Register Model in Database**

You can register the model using:

1. **Admin Panel** (if available)
2. **Database INSERT**:
```sql
INSERT INTO ai_ml_models (
    model_type, model_name, model_version, 
    model_path, provider, format, 
    active, enabled, created_at
) VALUES (
    'INTENT', 
    'Intent Classifier', 
    'v1.0', 
    '/path/to/models/intent-classifier-v1.onnx',
    'ONNX', 
    'ONNX', 
    true, 
    true, 
    NOW()
);
```

3. **Java Code** (using MlTrainingService):
```java
mlTrainingService.registerModel(
    "INTENT",
    "Intent Classifier",
    "v1.0",
    Paths.get("/path/to/models/intent-classifier-v1.onnx"),
    Map.of("accuracy", 0.95, "f1", 0.94)
);
```

---

## ✅ **Step 6: Verify Model Works**

### **Restart Your Java Application**

The model will be automatically loaded on startup by `MlModelLoaderService`.

### **Test Intent Classification**

Send a test query:
```
"What is my account balance?"
```

The ML Intent Classifier should:
1. ✅ Load the ONNX model
2. ✅ Tokenize the query
3. ✅ Run inference
4. ✅ Return `ACCOUNT_BALANCE` scenario with high confidence

---

## 🎓 **How It Works (Simple Explanation)**

### **1. Training Data**
- **Input**: User queries like "What is my balance?"
- **Output**: Scenario codes like "ACCOUNT_BALANCE"
- **Purpose**: Teach the model which queries match which scenarios

### **2. Model Training**
- **Uses**: DistilBERT (a smaller, faster version of BERT)
- **Process**: 
  - Reads training examples
  - Learns patterns (e.g., "balance" → ACCOUNT_BALANCE)
  - Adjusts internal weights to improve accuracy
- **Result**: A model that can classify new queries

### **3. ONNX Export**
- **Format**: ONNX (Open Neural Network Exchange)
- **Why**: Works with Java ONNX Runtime
- **Result**: A file that your Java app can use

### **4. Inference (Using the Model)**
- **Input**: New user query
- **Process**: 
  - Tokenize query
  - Run through model
  - Get probabilities for each scenario
- **Output**: Best matching scenario with confidence score

---

## 🔧 **Troubleshooting**

### **Problem: "Not enough training data"**
**Solution**: Run `create_training_data.sql` to create sample data

### **Problem: "Database connection failed"**
**Solution**: Check database credentials in `train_models.py`

### **Problem: "Module not found"**
**Solution**: Install missing packages: `pip install <package-name>`

### **Problem: "Model not loading in Java"**
**Solution**: 
- Check model path in database
- Verify ONNX file exists
- Check application logs for errors

---

## 📈 **Improving Model Performance**

### **Add More Training Data**
- More examples = Better accuracy
- Add diverse queries (different phrasings)
- Include edge cases

### **Fine-tune Hyperparameters**
Edit `train_models.py`:
```python
EPOCHS = 5  # More epochs = longer training, potentially better results
LEARNING_RATE = 1e-5  # Lower = slower but more stable
BATCH_SIZE = 32  # Larger = faster but needs more memory
```

### **Use Better Base Model**
```python
MODEL_NAME = "bert-base-uncased"  # Larger, more accurate
# or
MODEL_NAME = "distilbert-base-uncased"  # Smaller, faster (current)
```

---

## ✅ **Summary**

1. ✅ **Create training data** → Run `create_training_data.sql`
2. ✅ **Install Python packages** → `pip install ...`
3. ✅ **Configure database** → Edit `train_models.py`
4. ✅ **Train models** → Run `python train_models.py`
5. ✅ **Deploy model** → Copy ONNX file and register in database
6. ✅ **Test** → Restart app and test queries

**That's it!** Your ML models are now trained and ready to use! 🎉
