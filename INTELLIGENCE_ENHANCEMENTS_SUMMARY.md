# Intelligence Module Enhancement Summary
## What's "Too Basic" and How to Fix It

---

## 🔍 Current Limitations (Why It's "Too Basic")

### 1. **Concept Extraction - Very Basic**
**Current:**
```java
// Just extracts entities and maps to concepts
private Map<String, Double> extractConcepts(String query) {
    Map<String, EntityMatch> entities = entityExtractionService.extractEntities(query, null).block();
    // Simple mapping - entity type becomes concept
    return entityType → concept (just returns entityType as concept)
}
```

**Problems:**
- ❌ No banking domain knowledge
- ❌ No semantic understanding ("balance" doesn't map to ACCOUNT_BALANCE)
- ❌ No concept relationships
- ❌ No synonyms ("CC" doesn't map to CREDIT_CARD)
- ❌ No multi-word concepts ("credit card statement")

---

### 2. **Entity Extraction - Pattern-Only**
**Current:**
- Uses regex patterns from database
- No context awareness
- No reference resolution ("my account" → doesn't resolve)
- No validation (doesn't check if account exists)

**Problems:**
- ❌ Can't resolve "my account", "that transaction", "the previous one"
- ❌ No entity relationships (transaction belongs to account)
- ❌ No composite entities (date ranges, amount ranges)
- ❌ Basic regex only - no semantic matching

---

### 3. **Intent Detection - Basic Fallback Layers**
**Current:**
- Uses embedding matching (generic)
- Keyword matching (simple)
- Rule-based (basic)
- No banking-specific patterns

**Problems:**
- ❌ No banking domain expertise
- ❌ Generic embeddings don't understand banking context
- ❌ No multi-intent detection
- ❌ Limited ambiguity handling
- ❌ No context-aware intent (doesn't remember previous intents)

---

### 4. **Query Understanding - No Context**
**Current:**
- Basic normalization (trim, lowercase)
- No conversation understanding
- No follow-up query handling

**Problems:**
- ❌ Can't handle "show me more", "what about last month?"
- ❌ No reference resolution ("it", "that", "the previous")
- ❌ No implicit entity resolution ("for my account")
- ❌ No query expansion (abbreviations, synonyms)

---

## ✅ Enhancement Plan

### Priority 1: Banking Concept Dictionary (HIGHEST)

**What to Build:**
1. **Database Table: `ai_concept_dictionary`**
   - Banking concepts (ACCOUNT, TRANSACTION, CREDIT_CARD, etc.)
   - Synonyms and variations
   - Concept hierarchies
   - Concept relationships

2. **Service: `BankingConceptService`**
   - Load concepts from database
   - Map user terms to canonical concepts
   - Support synonyms ("CC" → CREDIT_CARD)
   - Support multi-word concepts

3. **Enhance: `extractConcepts()` in ReasoningPlannerImpl**
   - Use BankingConceptService
   - Semantic matching (not just entity type)
   - Multi-word concept extraction

**Impact:** ⭐⭐⭐⭐⭐ (Highest - fundamental to understanding)

---

### Priority 2: Context-Aware Entity Extraction

**What to Build:**
1. **Enhance: `EntityExtractionService`**
   - Add context memory resolution
   - Resolve references ("my account" → from session memory)
   - Entity validation (check against database)
   - Composite entity extraction (date ranges, amount ranges)

2. **New Service: `EntityReferenceResolver`**
   - Resolve pronouns ("it", "that")
   - Resolve implicit entities ("for my account")
   - Track entities across conversation

**Impact:** ⭐⭐⭐⭐ (High - enables natural conversation)

---

### Priority 3: Banking Intent Patterns

**What to Build:**
1. **Database Table: `ai_intent_patterns`**
   - Banking-specific intent patterns
   - Pattern variations
   - Context-aware patterns

2. **Enhance: Intent Detection**
   - Use banking-specific patterns
   - Multi-intent detection
   - Context-aware intent (remember previous)
   - Better ambiguity handling

**Impact:** ⭐⭐⭐⭐ (High - improves intent accuracy)

---

### Priority 4: Query Understanding Enhancement

**What to Build:**
1. **Query Expansion Service**
   - Expand abbreviations ("CC" → "Credit Card")
   - Handle synonyms ("balance" = "amount", "funds")
   - Domain-specific abbreviations

2. **Conversation State Manager**
   - Track conversation state
   - Handle follow-up queries
   - Reference resolution

**Impact:** ⭐⭐⭐ (Medium - improves user experience)

---

## 🚀 Implementation Order

### Step 1: Banking Concept Dictionary (Start Here)
**Why First:** Everything depends on understanding banking concepts

**Tasks:**
1. Create `ai_concept_dictionary` table
2. Create `BankingConcept` entity
3. Create `BankingConceptRepository`
4. Create `BankingConceptService`
5. Enhance `extractConcepts()` in ReasoningPlannerImpl

**Expected Result:**
- "Check my credit card balance" → Concepts: [CREDIT_CARD: 0.95, ACCOUNT_BALANCE: 0.90]
- "Show CC statement" → Concepts: [CREDIT_CARD: 0.95, STATEMENT: 0.90]
- Better concept understanding = better intent detection

---

### Step 2: Context-Aware Entity Extraction
**Why Second:** Needed for natural conversation

**Tasks:**
1. Enhance EntityExtractionService with context resolution
2. Create EntityReferenceResolver
3. Add entity validation
4. Test with "my account", "that transaction"

**Expected Result:**
- "Show balance for my account" → Resolves account from session memory
- "What about last month?" → Resolves date range and previous intent

---

### Step 3: Banking Intent Patterns
**Why Third:** Improves intent accuracy significantly

**Tasks:**
1. Create `ai_intent_patterns` table
2. Create `BankingIntentPatternService`
3. Enhance intent detection with banking patterns
4. Add multi-intent detection

**Expected Result:**
- Better intent accuracy (90%+)
- Handles complex banking queries
- Multi-intent support

---

## 📊 Database Schema for Enhancements

### 1. `ai_concept_dictionary`
```sql
CREATE TABLE ai_concept_dictionary (
    id BIGINT PRIMARY KEY,
    concept_code VARCHAR(100) NOT NULL,  -- ACCOUNT_BALANCE
    concept_name VARCHAR(200),            -- Account Balance
    parent_concept_code VARCHAR(100),     -- ACCOUNT (for hierarchies)
    synonyms TEXT,                        -- JSON array: ["balance", "amount", "funds"]
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0
);
```

### 2. `ai_intent_patterns`
```sql
CREATE TABLE ai_intent_patterns (
    id BIGINT PRIMARY KEY,
    scenario_code VARCHAR(100),
    pattern_type VARCHAR(50),             -- KEYWORD, SEMANTIC, CONTEXT
    pattern_value TEXT,                   -- Pattern definition
    priority INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT TRUE
);
```

---

## 🎯 Quick Wins (Can Do Immediately)

1. **Create Banking Concept Dictionary** (2-3 hours)
   - Database table + entity + service
   - Load banking concepts
   - Enhance concept extraction

2. **Enhance Entity Extraction with Context** (2-3 hours)
   - Use ContextMemoryService to resolve "my account"
   - Add reference resolution for pronouns

3. **Add Banking Intent Patterns** (2-3 hours)
   - Create pattern table
   - Add banking-specific patterns
   - Enhance intent detection

**Total: 6-9 hours for significant improvements**

---

## 📝 Next Actions

1. ✅ **API Integration** - COMPLETED (ChatService using kernel)
2. ⏭️ **Create Banking Concept Dictionary** - START HERE
3. ⏭️ **Enhance Entity Extraction**
4. ⏭️ **Add Banking Intent Patterns**
5. ⏭️ **Test End-to-End**

---

**Status:** Ready to start enhancements. Which should we tackle first?
