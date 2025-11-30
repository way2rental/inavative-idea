# 🚀 Dynamic AI Model Upgrade – Specification for Copilot Agent

## 1. Problem Statement

The current AI implementation is **weak and rigid** due to:

* ❌ Hardcoded prompts
* ❌ Static intent detection
* ❌ Fixed follow-up questions
* ❌ No runtime configurability
* ❌ Difficult to scale across 200+ file processing types
* ❌ Poor user-level access validation
* ❌ No dynamic learning or adaptation

This document defines how to convert the system into a **fully dynamic, configuration-driven, self-evolving AI architecture**.

---

## 2. Core Goals

✅ Zero hardcoded prompts
✅ Fully dynamic follow-up system
✅ Runtime policy-based access control
✅ Multi-user validation & isolation
✅ 200+ file processing support without code changes
✅ GPT + Rule Engine Hybrid
✅ DB + JSON + YAML configuration
✅ Scalable for SaaS

---

## 3. Architecture Overview

```text
[ User Request ]  
      ↓
[ Access Validator ]  
      ↓
[ Intent Engine (Dynamic) ]  
      ↓
[ Follow-up Generator (DB Driven) ]  
      ↓
[ Policy Engine ]  
      ↓
[ Prompt Builder (Template Driven) ]  
      ↓
[ LLM Provider (OpenAI/Ollama) ]  
      ↓
[ Response Formatter + Audit Logger ]
```

---

## 4. Prompt Must Never Be Hardcoded

### ✅ Prompt Storage Options

| Source    | Purpose            |
| --------- | ------------------ |
| Database  | Runtime updates    |
| JSON/YAML | Versioned fallback |
| Admin UI  | Business teams     |

### Example DB Prompt Structure

```json
{
  "promptKey": "TXN_STATUS",
  "systemPrompt": "You are a banking assistant",
  "userTemplate": "Check transaction status of {{txnId}}",
  "responseFormat": "JSON",
  "enabled": true
}
```

---

## 5. Dynamic Intent Detection

### ✅ Current Issue

* Hardcoded conditions
* Fixed enum mapping

### ✅ New Flow

```text
User Message → Embedding → DB Intent Vectors → Similarity Match → Confidence Scoring
```

### ✅ Intent DB Model

```json
{
  "intentKey": "TXN_STATUS",
  "trainingPhrases": [
    "check my transaction",
    "payment status",
    "where is my money"
  ],
  "confidenceThreshold": 0.72,
  "followUpGroup": "TXN_FOLLOWUPS"
}
```

---

## 6. Fully Dynamic Follow-Up System

### ✅ Follow-ups Must Never Be Hardcoded

```json
{
  "groupKey": "TXN_FOLLOWUPS",
  "questions": [
    {
      "key": "txnId",
      "question": "Please provide your Transaction ID",
      "type": "string",
      "required": true
    },
    {
      "key": "date",
      "question": "Transaction date (optional)",
      "type": "date",
      "required": false
    }
  ]
}
```

✅ Order control
✅ Optional vs Mandatory
✅ Field type validation
✅ Re-asking on failure

---

## 7. File Processing (200+ Types)

### ✅ Current Risk

* If/else chains
* Manual mapping

### ✅ Dynamic Model

```json
{
  "fileType": "XML",
  "fileNamePattern": "*.xml",
  "allowedIntents": ["STATUS_CHECK", "REPROCESS"],
  "validationRules": ["SCHEMA_VALID", "SIZE_LIMIT"],
  "processingFlow": "XML_CORE_FLOW"
}
```

---

## 8. User Access & Security Layer

### ✅ Mandatory Runtime Checks

| Check           | Purpose                      |
| --------------- | ---------------------------- |
| User Ownership  | Prevent cross-account access |
| Role Validation | ADMIN / USER / AUDITOR       |
| Data Scope      | Same tenant                  |
| Rate Limit      | Abuse protection             |

```text
User → Token → Role → Tenant → Resource → Decision
```

---

## 9. Policy Engine (Rules Based)

```json
{
  "policyKey": "CROSS_USER_PROTECTION",
  "rule": "request.userId == resource.ownerId",
  "onFail": "BLOCK",
  "message": "Unauthorized access attempt detected"
}
```

---

## 10. Dynamic Prompt Builder

```text
System Prompt   := From DB
User Template   := From DB
Follow Up Data  := Runtime
Policy Flags    := Runtime
Final Prompt    := Rendered via Template Engine
```

✅ Supports variables
✅ Supports multi-step workflows
✅ Supports multilingual
✅ Supports role-based output differences

---

## 11. Multi-LLM Support

| Provider | Use Case      |
| -------- | ------------- |
| OpenAI   | Production    |
| Ollama   | Local Dev     |
| Azure    | Enterprise    |
| Bedrock  | AWS Regulated |

Dynamic switching via:

```yaml
spring.ai.provider=ollama
```

---

## 12. Observability & Audit

✅ Every request must log:

* User ID
* Intent
* Prompt Key
* Tokens Used
* Model Used
* Response Size
* Time Taken

Stored in:

* DB
* Elasticsearch
* S3

---

## 12A. ✅ Internal Context Parameter Management (Security Critical)

### ✅ Purpose

These parameters are **STRICTLY INTERNAL** and must **never be exposed to LLMs or end users directly**:

* userId
* corpCode
* tenantId
* role
* ipAddress
* deviceId
* sessionId

### ✅ Rules

✅ Must be injected at **Request Ingress Layer**
✅ Must be stored in **RequestContextHolder (ThreadLocal / Reactive Context)**
✅ Must never be accepted from frontend blindly
✅ Must never be logged in plain text to LLM prompts
✅ Must be used only for:

* Access validation
* Policy evaluation
* Ownership verification
* Tenant isolation

### ✅ Example Context Object

```json
{
  "userId": "U123",
  "corpCode": "ICICI",
  "tenantId": "BANK01",
  "role": "USER",
  "ip": "10.20.30.40"
}
```

---

## 13. Admin Control Panel (Required)

| Feature                | Purpose         |
| ---------------------- | --------------- |
| Prompt Editor          | Runtime updates |
| Intent Trainer         | No redeploy     |
| Follow-up Form Builder | Dynamic UX      |
| Access Rules           | Security        |
| Cost Tracking          | Billing         |

---

## 13A. ✅ Centralized Cache Management (Mandatory)

### ✅ Current Issue

* Cache defined in multiple places
* No single source of truth
* High inconsistency risk

### ✅ New Rule

All cache must be managed **from one centralized module only**.

### ✅ Central Cache Responsibilities

* Prompt cache
* Intent cache
* Follow-up cache
* Policy cache
* File processing config cache
* Model config cache

### ✅ Allowed Cache Types

* Redis (Prod)
* Caffeine (Local Dev)

### ✅ Hard Rules

✅ No local caches inside feature modules
✅ No duplicate TTL definitions
✅ No manual eviction outside cache manager
✅ All cache keys must follow naming convention

Example:

```
AI:PROMPT:TXN_STATUS
AI:INTENT:PAYMENT
```

---------|---------|
| Prompt Editor | Runtime updates |
| Intent Trainer | No redeploy |
| Follow-up Form Builder | Dynamic UX |
| Access Rules | Security |
| Cost Tracking | Billing |

---

## 14. Mandatory Technical Rules

✅ No hardcoded prompt anywhere in code
✅ No hardcoded intent rules
✅ No hardcoded follow-up questions
✅ No direct model calls without policy verification
✅ All LLM calls must go via Prompt Builder
✅ All cache must come from centralized cache manager
✅ All internal context must come from RequestContext only
✅ Frontend is never source of truth for user identity

---

## 15. ✅ Code Hygiene & Dead Feature Control

### ✅ Current Problem

* CRUD is implemented but unused
* Cache duplicated
* Old services still active

### ✅ Mandatory Actions

✅ Every CRUD API must be mapped to at least one active workflow
✅ If unused for 2 sprints → auto deprecation
✅ Dead services must be removed, not commented
✅ Feature flags must control partial releases

---

## 16. ✅ Platform-Level Enhancements (Strongly Recommended)

✅ Conversation Memory Manager
✅ Prompt Versioning & Rollback
✅ Canary Prompt Deployment
✅ Shadow Mode Model Testing
✅ Auto cost circuit breaker
✅ LLM hallucination detection
✅ PII masking before prompt send
✅ Model fallback on failure
✅ Intent confidence auto tuning
✅ AI SLA monitoring

---

## ✅ Final Instruction for Copilot Agent

> Implement a fully dynamic AI orchestration engine where:
>
> * Nothing is hardcoded
> * All logic is driven using DB + JSON + Policy + Templates
> * Context parameters are internal and secured
> * Cache is centralized and controlled from one module
> * Dead code is automatically eliminated
> * The system scales for 200+ workflows
> * The system blocks cross-user data leakage
> * The system auto adapts without redeployments

---

✅ This document is approved as the **single source of truth** for all future AI upgrades.
