# AXIS AI KERNEL IMPLEMENTATION PLAN

## Overview

This document tracks the implementation of the AXIS AI KERNEL architecture, ensuring all components are manageable via admin panel and all disconnected components are properly connected.

## Current State Analysis

### ✅ Already Exists and Connected:
1. **FallbackLayerOrchestrator** → Will become **Reasoning Planner**
2. **EmbeddingMatcher** → Part of **RAG Engine**
3. **ContextMemoryService** → **Memory Manager** (already works!)
4. **ResponseFormatterService** → **Response Shaper** (already works!)
5. **DynamicPromptBuilder** (in LLM module) → Part of **Prompt Compiler**
6. **Admin Controllers**: Already exist for scenarios, rules, keywords, embeddings

### ⚠️ Exists but Needs Connection/Enhancement:
1. **Entity Extraction** → Needs to be part of Concept Extraction in Reasoning Planner
2. **Parameter Extraction** → Needs to be connected to Reasoning Planner
3. **Reference Resolution** → Part of Memory Manager (already connected)
4. **Response Templates** → Part of Response Shaper (needs admin panel management)

### ❌ Missing (Need to Create):
1. **ReasoningPlan DTO** - Structured, auditable planning output
2. **RAG Engine with 4 retrievers**:
   - Domain Document Retriever (NEW - need DB entity)
   - Intent Definition Retriever (can use AiScenario + new fields)
   - Tool Definition Retriever (can use AiScenario execution config)
   - Response Pattern Retriever (can use ResponseTemplate)
3. **Prompt Compiler** (enhance DynamicPromptBuilder)
4. **Policy & Compliance Guard** (partially exists in IntentValidationService)
5. **10-stage pipeline orchestrator**

### 🔍 Need to Check for Unused/Disconnected:
1. ML Model entities/services (MlModel entity exists - check if used)
2. EmbeddingGenerationService - Check if connected
3. EntityValidator - Check if used
4. Any unused admin controllers

## Implementation Phases

### Phase 1: Kernel Structure Foundation
1. Create kernel package structure
2. Create ReasoningPlan DTO
3. Create kernel interfaces

### Phase 2: Reasoning Planner (Refactor FallbackLayerOrchestrator)
1. Query Normalization
2. Concept Extraction (use EntityExtractionService)
3. Intent Hypothesis (RAG-backed)
4. Capability Decision
5. Execution Plan Generation
6. Admin panel management

### Phase 3: RAG Engine
1. Create domain document entity + retriever (if needed)
2. Intent Definition Retriever (enhance scenario retrieval)
3. Tool Definition Retriever (from scenarios)
4. Response Pattern Retriever (from ResponseTemplate)
5. Admin panel management for all RAG sources

### Phase 4: Prompt Compiler
1. Enhance DynamicPromptBuilder → PromptCompiler
2. Policy injection
3. RAG context injection
4. Format enforcement
5. Admin panel management

### Phase 5: Policy & Compliance Guard
1. Pre-LLM validation
2. Post-LLM validation
3. Compliance language enforcement
4. Admin panel management

### Phase 6: 10-Stage Pipeline
1. Create pipeline orchestrator
2. Connect all stages
3. Integration testing

### Phase 7: Admin Panel Integration
1. Ensure all kernel components are manageable
2. Create admin UI if needed
3. Test admin workflows

### Phase 8: Cleanup
1. Remove unused components
2. Connect disconnected components
3. Documentation

## Admin Panel Management Requirements

All kernel components MUST be manageable via admin panel:

1. **Reasoning Planner**:
   - Configuration (thresholds, timeouts)
   - Enable/disable components

2. **RAG Engine**:
   - Domain Documents CRUD
   - Intent Definitions (via scenarios)
   - Tool Definitions (via scenarios)
   - Response Patterns (via ResponseTemplate - needs admin)

3. **Prompt Compiler**:
   - Prompt templates management
   - Policy rules management
   - Format rules management

4. **Policy & Compliance Guard**:
   - Policy rules CRUD
   - Compliance templates
   - Validation rules

5. **Response Shaper**:
   - Response templates (already has ResponseTemplateAdminController)

## Database Schema Additions Needed

1. **Domain Documents Table** (for RAG):
   - id, title, content, category (FAQ/POLICY/PRODUCT/SOP), tags, embedding_vector, active, created_at, updated_at

2. **Enhanced AiScenario** (if needed):
   - Add fields for intent definition metadata
   - Add fields for tool definition metadata

## Key Principles

1. **Incremental**: Don't break existing functionality
2. **Admin-First**: Everything configurable via admin panel
3. **Connect Everything**: No disconnected components
4. **Remove Unused**: Clean up dead code
5. **Test Continuously**: Test after each phase
