#!/usr/bin/env python3
"""
Simple ML Model Training Script for Banking AI
Trains Intent Classifier and NER models using BERT, exports to ONNX

Requirements:
    pip install torch transformers onnx transformers[onnx] datasets scikit-learn numpy pandas mysql-connector-python

Usage:
    python train_models.py
"""

import os
import json
import mysql.connector
from datetime import datetime
from transformers import (
    AutoTokenizer, 
    AutoModelForSequenceClassification,
    AutoModelForTokenClassification,
    TrainingArguments,
    Trainer,
    DataCollatorWithPadding
)
from transformers.onnx import export
from datasets import Dataset
import numpy as np
from sklearn.metrics import accuracy_score, f1_score, classification_report
import torch

# Database configuration (update with your database credentials)
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'password',  # Update this
    'database': 'ai_orchestrator'  # Update this
}

# Model configuration
MODEL_NAME = "distilbert-base-uncased"  # Lighter than BERT, faster training
OUTPUT_DIR = "./models"
BATCH_SIZE = 16
EPOCHS = 3
LEARNING_RATE = 2e-5

def connect_db():
    """Connect to MySQL database"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        print("✅ Connected to database")
        return conn
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        raise

def load_training_data(conn):
    """Load training data from database"""
    cursor = conn.cursor(dictionary=True)
    
    # Load intent classification data
    cursor.execute("""
        SELECT user_query, scenario_code 
        FROM ai_training_data 
        WHERE scenario_code IS NOT NULL 
        AND labeled_at IS NOT NULL
        AND used_for_training = false
        LIMIT 1000
    """)
    intent_data = cursor.fetchall()
    
    # Load NER data
    cursor.execute("""
        SELECT user_query, entities 
        FROM ai_training_data 
        WHERE entities IS NOT NULL 
        AND labeled_at IS NOT NULL
        AND used_for_training = false
        LIMIT 500
    """)
    ner_data = cursor.fetchall()
    
    cursor.close()
    
    print(f"✅ Loaded {len(intent_data)} intent examples, {len(ner_data)} NER examples")
    return intent_data, ner_data

def prepare_intent_dataset(intent_data):
    """Prepare intent classification dataset"""
    texts = [item['user_query'] for item in intent_data]
    labels = [item['scenario_code'] for item in intent_data]
    
    # Get unique labels and create label mapping
    unique_labels = sorted(list(set(labels)))
    label_to_id = {label: idx for idx, label in enumerate(unique_labels)}
    id_to_label = {idx: label for label, idx in label_to_id.items()}
    
    # Convert labels to IDs
    label_ids = [label_to_id[label] for label in labels]
    
    print(f"✅ Intent labels: {unique_labels}")
    print(f"✅ Number of classes: {len(unique_labels)}")
    
    # Create dataset
    dataset = Dataset.from_dict({
        'text': texts,
        'labels': label_ids
    })
    
    # Split into train/val (80/20)
    dataset = dataset.train_test_split(test_size=0.2, seed=42)
    
    return dataset, label_to_id, id_to_label

def train_intent_classifier(dataset, label_to_id, id_to_label):
    """Train intent classification model"""
    print("\n🚀 Training Intent Classifier...")
    
    # Load tokenizer and model
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME,
        num_labels=len(label_to_id)
    )
    
    # Tokenize dataset
    def tokenize_function(examples):
        return tokenizer(
            examples['text'],
            truncation=True,
            padding='max_length',
            max_length=128
        )
    
    tokenized_train = dataset['train'].map(tokenize_function, batched=True)
    tokenized_val = dataset['test'].map(tokenize_function, batched=True)
    
    # Training arguments
    training_args = TrainingArguments(
        output_dir=f"{OUTPUT_DIR}/intent_classifier",
        num_train_epochs=EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        per_device_eval_batch_size=BATCH_SIZE,
        learning_rate=LEARNING_RATE,
        weight_decay=0.01,
        logging_dir=f"{OUTPUT_DIR}/intent_classifier/logs",
        logging_steps=10,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
    )
    
    # Data collator
    data_collator = DataCollatorWithPadding(tokenizer=tokenizer)
    
    # Metrics function
    def compute_metrics(eval_pred):
        predictions, labels = eval_pred
        predictions = np.argmax(predictions, axis=1)
        return {
            'accuracy': accuracy_score(labels, predictions),
            'f1': f1_score(labels, predictions, average='weighted')
        }
    
    # Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_train,
        eval_dataset=tokenized_val,
        data_collator=data_collator,
        compute_metrics=compute_metrics,
    )
    
    # Train
    trainer.train()
    
    # Evaluate
    eval_results = trainer.evaluate()
    print(f"✅ Intent Classifier - Accuracy: {eval_results['eval_accuracy']:.4f}, F1: {eval_results['eval_f1']:.4f}")
    
    # Export to ONNX
    onnx_path = f"{OUTPUT_DIR}/intent-classifier.onnx"
    print(f"📦 Exporting to ONNX: {onnx_path}")
    export(
        model=model,
        tokenizer=tokenizer,
        output=onnx_path,
        opset=11
    )
    print(f"✅ Intent Classifier exported to {onnx_path}")
    
    # Save label mapping
    with open(f"{OUTPUT_DIR}/intent_label_mapping.json", 'w') as f:
        json.dump({
            'label_to_id': label_to_id,
            'id_to_label': id_to_label
        }, f, indent=2)
    
    return onnx_path, eval_results

def prepare_ner_dataset(ner_data):
    """Prepare NER dataset (simplified - full NER requires BIO tagging)"""
    # For simplicity, we'll create a basic NER model
    # In production, you'd need proper BIO tagging
    texts = [item['user_query'] for item in ner_data]
    
    # Parse entities JSON
    entity_labels = []
    for item in ner_data:
        entities = json.loads(item['entities']) if isinstance(item['entities'], str) else item['entities']
        # Create simple labels (in production, use BIO tagging)
        labels = ['O'] * len(item['user_query'].split())
        entity_labels.append(labels)
    
    print(f"✅ NER examples: {len(texts)}")
    print("⚠️  Note: Full NER training requires BIO tagging - this is a simplified version")
    
    return texts, entity_labels

def train_ner_model(texts, entity_labels):
    """Train NER model (simplified)"""
    print("\n🚀 Training NER Model...")
    print("⚠️  Note: Full NER requires BIO tagging. This is a simplified implementation.")
    print("⚠️  For production, use proper NER training with token-level labels.")
    
    # For now, we'll create a placeholder
    # In production, implement proper NER training with BIO tagging
    print("✅ NER model training placeholder - implement full BIO tagging for production")
    
    return None

def mark_data_as_used(conn, intent_count, ner_count):
    """Mark training data as used"""
    cursor = conn.cursor()
    
    # Mark intent data
    cursor.execute("""
        UPDATE ai_training_data 
        SET used_for_training = true 
        WHERE scenario_code IS NOT NULL 
        AND labeled = true 
        AND used_for_training = false
        LIMIT %s
    """, (intent_count,))
    
    # Mark NER data
    cursor.execute("""
        UPDATE ai_training_data 
        SET used_for_training = true 
        WHERE entities IS NOT NULL 
        AND labeled = true 
        AND used_for_training = false
        LIMIT %s
    """, (ner_count,))
    
    conn.commit()
    cursor.close()
    print(f"✅ Marked {intent_count + ner_count} training examples as used")

def main():
    """Main training function"""
    print("=" * 60)
    print("🤖 ML Model Training Script")
    print("=" * 60)
    
    # Create output directory
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Connect to database
    conn = connect_db()
    
    try:
        # Load training data
        intent_data, ner_data = load_training_data(conn)
        
        if len(intent_data) < 10:
            print("❌ Not enough training data! Need at least 10 examples.")
            print("💡 Run create_training_data.sql first to create sample data.")
            return
        
        # Train intent classifier
        dataset, label_to_id, id_to_label = prepare_intent_dataset(intent_data)
        onnx_path, metrics = train_intent_classifier(dataset, label_to_id, id_to_label)
        
        # Train NER (simplified)
        texts, entity_labels = prepare_ner_dataset(ner_data)
        train_ner_model(texts, entity_labels)
        
        # Mark data as used
        mark_data_as_used(conn, len(intent_data), len(ner_data))
        
        print("\n" + "=" * 60)
        print("✅ Training Complete!")
        print("=" * 60)
        print(f"📦 Model saved to: {onnx_path}")
        print(f"📊 Accuracy: {metrics['eval_accuracy']:.4f}")
        print(f"📊 F1 Score: {metrics['eval_f1']:.4f}")
        print("\n💡 Next steps:")
        print("1. Copy the ONNX model to your Java application's model directory")
        print("2. Register the model in the database using MlTrainingService.registerModel()")
        print("3. The model will be automatically loaded and used by MlIntentClassifierService")
        
    except Exception as e:
        print(f"❌ Training failed: {e}")
        import traceback
        traceback.print_exc()
    finally:
        conn.close()

if __name__ == "__main__":
    main()
