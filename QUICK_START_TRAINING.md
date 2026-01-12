# Quick Start: Train Your ML Models in 5 Minutes! 🚀

## 🎯 **What You Need**

1. ✅ **Python 3.8+** installed
2. ✅ **MySQL database** running
3. ✅ **5 minutes** of your time

---

## 📝 **Step-by-Step (Copy & Paste)**

### **Step 1: Install Python Packages**

```bash
pip install -r requirements.txt
```

**Takes:** ~2-3 minutes (downloads packages)

---

### **Step 2: Create Training Data**

```bash
# Connect to your database and run:
mysql -u root -p ai_orchestrator < create_training_data.sql
```

**What it does:** Creates 70+ labeled examples in your database

---

### **Step 3: Configure Database**

Edit `train_models.py` line 25-30:

```python
DB_CONFIG = {
    'host': 'localhost',        # Your database host
    'port': 3306,               # Your database port  
    'user': 'root',             # Your database user
    'password': 'your_password', # ⚠️ CHANGE THIS
    'database': 'ai_orchestrator' # Your database name
}
```

---

### **Step 4: Train Models**

```bash
python train_models.py
```

**What happens:**
- ✅ Loads training data from database
- ✅ Trains Intent Classifier (takes ~2-5 minutes)
- ✅ Exports to ONNX format
- ✅ Creates `models/intent-classifier.onnx`

**Expected output:**
```
✅ Connected to database
✅ Loaded 50 intent examples
🚀 Training Intent Classifier...
✅ Intent Classifier - Accuracy: 0.95, F1: 0.94
📦 Exporting to ONNX...
✅ Training Complete!
```

---

### **Step 5: Use the Model**

1. **Copy model to your app:**
   ```bash
   cp models/intent-classifier.onnx /path/to/your/app/models/
   ```

2. **Register in database:**
   ```sql
   INSERT INTO ai_ml_models (
       model_type, model_name, model_version, 
       model_path, provider, format, active, enabled
   ) VALUES (
       'INTENT', 'Intent Classifier', 'v1.0',
       '/path/to/models/intent-classifier.onnx',
       'ONNX', 'ONNX', true, true
   );
   ```

3. **Restart your Java app** - Model loads automatically!

---

## ✅ **That's It!**

Your ML model is now trained and ready to use! 🎉

The model will automatically:
- ✅ Load on application startup
- ✅ Classify user queries
- ✅ Return scenarios with confidence scores
- ✅ Fallback to other layers if confidence is low

---

## 🆘 **Troubleshooting**

**"Module not found"** → Run: `pip install -r requirements.txt`

**"Database connection failed"** → Check credentials in `train_models.py`

**"Not enough training data"** → Run: `mysql -u root -p ai_orchestrator < create_training_data.sql`

**"Model not loading"** → Check model path in database matches actual file location

---

## 📚 **Want More Details?**

See `TRAINING_GUIDE.md` for comprehensive documentation.
