# DLM (Domain Language Model) Feasibility Analysis

## ✅ YES, WE CAN DO IT!

The architecture already supports this approach with minimal refactoring.

---

## Current Architecture vs DLM Requirements

### ✅ What Already Exists

| DLM Requirement | Current Implementation | Status |
|----------------|------------------------|--------|
| **Intent Identification** | `ReasoningPlanner` (Stage 1-5) via `FallbackLayerOrchestrator` | ✅ EXISTS |
| **Concept Extraction** | `ReasoningPlan.concepts` (Map<String, Double>) | ✅ EXISTS |
| **Entity Extraction** | `EntityExtractionService` → `ReasoningPlan.parameters` | ✅ EXISTS |
| **Confidence/Ambiguity Scoring** | `IntentResult.confidence`, `ReasoningPlan.confidence` | ✅ EXISTS |
| **Structured Output** | `ReasoningPlan` DTO (structured, auditable) | ✅ EXISTS |
| **Response Formatting** | `ResponseShaper` (Stage 10) - template-based | ✅ EXISTS |
| **No External LLM Calls** | Already removed from kernel | ✅ EXISTS |

---

## What Needs to Change

### 1. **Stage 8 (KernelLlmService) → Remove Text Generation**

**Current State:**
- `KernelLlmService` generates text responses using templates
- Uses RAG context + templates to produce final text

**New State:**
- **Option A:** Remove Stage 8 entirely (ReasoningPlanner already does DLM work)
- **Option B:** Transform to pure DLM that outputs structured JSON only
- **Option C:** Keep for future ML enhancement (offline training)

**Recommendation:** **Option A** - Remove Stage 8 entirely since ReasoningPlanner already does DLM work.

### 2. **ReasoningPlanner → Clarify as DLM**

**Current State:**
- Does intent identification, concept extraction, entity extraction
- Outputs structured `ReasoningPlan`

**New State:**
- **Rename/Clarify** that ReasoningPlanner = Domain Language Model (DLM)
- Ensure it outputs structured JSON-like data (already does via ReasoningPlan)
- Add explicit ambiguity scoring if missing

**Action:** Add documentation/clarification, minimal code changes.

### 3. **Response Generation → Move to Stage 10**

**Current State:**
- Stage 8 (KernelLlmService) generates responses
- Stage 10 (ResponseShaper) formats responses

**New State:**
- **Remove text generation from Stage 8**
- **Enhance Stage 10 (ResponseShaper)** to handle all response generation
- Use template-based, deterministic approach
- Use RAG context + ReasoningPlan + Tool results

**Action:** Refactor ResponseShaper to generate responses from templates.

---

## Proposed New Pipeline Flow

### Current (10 Stages):
1. Query Normalization ✅
2. Concept Extraction ✅
3. Intent Hypothesis (RAG-backed) ✅
4. Capability Decision ✅
5. Execution Plan Creation ✅
6. Context Assembly (RAG) ✅
7. Prompt Compilation ❌ (NOT NEEDED - was for LLM)
8. **LLM Reasoning** ❌ (REMOVE - generates text)
9. Post-Response Validation ✅
10. Final Response Shaping ✅

### New (9 Stages):
1. **Query Normalization** (ReasoningPlanner - Stage 1)
2. **Concept Extraction** (ReasoningPlanner - Stage 2) ← DLM work
3. **Intent Hypothesis** (ReasoningPlanner - Stage 3) ← DLM work
4. **Entity Extraction** (ReasoningPlanner - Stage 4) ← DLM work
5. **Ambiguity Scoring** (ReasoningPlanner - Stage 5) ← DLM work
6. **Capability Decision** (ReasoningPlanner - Stage 6)
7. **Execution Plan Creation** (ReasoningPlanner - Stage 7)
8. **Context Assembly (RAG)** (RagEngine - Stage 8)
9. **Post-Response Validation** (ComplianceGuard - Stage 9)
10. **Response Generation & Formatting** (ResponseShaper - Stage 10) ← Template-based

**OR simplify to 8 stages:**
1-5. **DLM (Domain Language Model)** = ReasoningPlanner (Intent, Concepts, Entities, Ambiguity)
6. **Context Assembly (RAG)** = RagEngine
7. **Capability Decision & Execution** = ToolDispatcher + ComplianceGuard
8. **Response Generation** = ResponseShaper (Template-based, deterministic)

---

## Implementation Plan

### Phase 1: Refactor Stage 8 (KernelLlmService)
- [ ] Remove text generation from `KernelLlmService`
- [ ] Either remove Stage 8 entirely OR transform to pure DLM interface
- [ ] Update `AxisAiKernelImpl` to skip Stage 8

### Phase 2: Enhance ResponseShaper
- [ ] Move response generation logic from KernelLlmService to ResponseShaper
- [ ] Use RAG context + ReasoningPlan + Tool results
- [ ] Template-based, deterministic approach
- [ ] No text generation, only template processing

### Phase 3: Clarify DLM Role
- [ ] Document that ReasoningPlanner = DLM
- [ ] Ensure ambiguity scoring is explicit
- [ ] Ensure structured JSON output (already via ReasoningPlan)

### Phase 4: Update Pipeline
- [ ] Update AxisAiKernelImpl to new flow
- [ ] Remove Stage 8 or make it optional
- [ ] Update documentation

---

## Benefits

1. ✅ **Cleaner Architecture** - DLM is separate from response generation
2. ✅ **Deterministic** - All responses are template-based
3. ✅ **Auditable** - DLM outputs structured data (ReasoningPlan)
4. ✅ **No External Dependencies** - No LLM calls, no text generation
5. ✅ **ML-Ready** - DLM can be improved offline via ML training
6. ✅ **Already Mostly Built** - 90% of the architecture exists

---

## Challenges & Solutions

### Challenge 1: Current Stage 8 generates responses
**Solution:** Move logic to ResponseShaper (Stage 10), make Stage 8 optional/remove

### Challenge 2: Terminology confusion (LLM vs DLM)
**Solution:** Rename KernelLlmService → DomainLanguageModel (DLM), clarify role

### Challenge 3: Ambiguity scoring needs to be explicit
**Solution:** Enhance ReasoningPlan to include explicit ambiguity_score field

### Challenge 4: Response generation needs RAG context
**Solution:** Pass RAG context to ResponseShaper (already available in pipeline)

---

## Conclusion

**✅ YES, THIS IS FEASIBLE!**

The architecture already supports 90% of the DLM approach. We just need to:

1. **Remove text generation from Stage 8** (KernelLlmService)
2. **Move response generation to Stage 10** (ResponseShaper)
3. **Clarify that ReasoningPlanner = DLM**
4. **Ensure structured output** (already exists via ReasoningPlan)

The codebase is well-positioned for this change. Most components already follow the DLM principles:
- Intent identification ✅
- Concept extraction ✅
- Entity extraction ✅
- Structured output ✅
- Template-based responses ✅

**Next Step:** Start refactoring Stage 8 and moving response generation to ResponseShaper.
