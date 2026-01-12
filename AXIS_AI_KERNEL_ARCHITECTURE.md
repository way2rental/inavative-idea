# AXIS AI KERNEL ARCHITECTURE – REAL AI SYSTEM (FINAL)

## 1. OVERVIEW

Axis AI is a **bank-grade, reasoning-centric AI SYSTEM** built in Java/Spring.

This is NOT a chatbot.
This is NOT prompt engineering.
This is NOT an LLM wrapper.

Axis AI is an **intelligent execution system** where:
- intelligence comes from architecture
- reasoning is explicit and auditable
- LLMs are replaceable reasoning engines only

------------------------------------------------------------
CORE PRINCIPLE (NON-NEGOTIABLE)
------------------------------------------------------------

LLMs are treated as:
→ Stateless reasoning CPUs

LLMs are NOT:
→ Decision makers
→ Controllers
→ Sources of truth
→ Business logic

All decisions are owned by the AXIS AI KERNEL.

------------------------------------------------------------
2. ARCHITECTURE PHILOSOPHY
------------------------------------------------------------

Axis AI = CENTRAL AI KERNEL + Surrounding Services

The AI Kernel is **infrastructure**, not a feature.

AI Kernel OWNS:
- Intent understanding
- Reasoning & planning
- Context assembly
- Prompt compilation
- Tool orchestration
- Response validation
- Policy & compliance enforcement

Surrounding services ONLY:
- Provide data
- Execute actions
- Render responses

------------------------------------------------------------
3. AXIS AI KERNEL – MANDATORY COMPONENTS
------------------------------------------------------------

### 3.1 Reasoning Planner (THE BRAIN)

Purpose:
Decide WHAT must be done BEFORE any LLM call.

Responsibilities:
- Query normalization
- Concept extraction (semantic, embedding-based)
- Intent hypothesis (RAG-backed)
- Capability decision:
  - Direct answer
  - Data retrieval
  - Tool / DB / API execution
- Execution plan generation

OUTPUT:
A **ReasoningPlan** (structured, auditable, deterministic)

Example (conceptual):
```json
{
  "intent": "FETCH_CREDIT_CARD_STATEMENT",
  "capability": "TOOL_EXECUTION",
  "tools": ["CARD_STATEMENT_SERVICE"],
  "required_context": ["ACCOUNT_ID", "DATE_RANGE"],
  "response_pattern": "STATEMENT_SUMMARY"
}
```

IMPORTANT:
LLM is NEVER called inside Reasoning Planner.

------------------------------------------------------------
3.2 RAG ENGINE (SYSTEM KNOWLEDGE BASE)
------------------------------------------------------------

RAG is NOT document search.

RAG = RETRIEVABLE SYSTEM KNOWLEDGE

RAG contains FOUR knowledge classes:

1) DOMAIN DOCUMENTS
   - FAQs
   - Policies
   - Product definitions
   - SOPs

2) INTENT DEFINITIONS
   - Allowed user intents
   - Required entities
   - Constraints
   - Risk classification

3) TOOL DEFINITIONS
   - What the system CAN DO
   - DB queries
   - API calls
   - Business actions

4) RESPONSE PATTERNS
   - Output structure
   - Compliance language
   - UX consistency rules

All of the above are:
- Stored
- Versioned
- Retrievable
- Configurable via DB

------------------------------------------------------------
3.3 PROMPT COMPILER (NOT STRING BUILDER)
------------------------------------------------------------

Prompts are COMPILED ARTIFACTS.

Prompt Compiler converts:
Intent + Context + Rules → CompiledPrompt

Responsibilities:
- Inject policy constraints
- Inject RAG context
- Enforce strict format
- Limit scope of reasoning
- Prevent hallucination
- Control tone and output style

Think of this as:
Source Code → Bytecode
Intent → Compiled Prompt

------------------------------------------------------------
3.4 TOOL DISPATCHER (DETERMINISTIC EXECUTION)
------------------------------------------------------------

Purpose:
Execute real-world actions decided by the planner.

Responsibilities:
- Route to correct executor
- Execute DB queries
- Call internal services
- Call external APIs
- Return structured results

STRICT RULE:
❗ NO LLM is allowed inside tool execution.

------------------------------------------------------------
3.5 MEMORY MANAGER (STATEFUL INTELLIGENCE)
------------------------------------------------------------

Purpose:
Maintain conversational and contextual memory.

Responsibilities:
- Short-term context (current session)
- Long-term references (summaries, preferences)
- Reference resolution:
  - "same account"
  - "that transaction"
  - "previous bill"

Memory is:
- Structured
- Queryable
- Auditable

------------------------------------------------------------
3.6 POLICY & COMPLIANCE GUARD (BANK-GRADE SAFETY)
------------------------------------------------------------

Policy Guard runs BOTH:

PRE-LLM:
- Intent allow-listing
- Role & authorization checks
- Data access constraints

POST-LLM:
- Hallucination detection
- Compliance phrasing enforcement
- Sensitive data masking
- Risk classification

NO RESPONSE leaves the kernel without policy approval.

------------------------------------------------------------
3.7 RESPONSE SHAPER (FINAL OUTPUT AUTHORITY)
------------------------------------------------------------

LLM output is RAW reasoning text.

Response Shaper:
- Converts raw output into final API response
- Applies structure (JSON / sections)
- Applies UX rules
- Ensures frontend compatibility

LLM NEVER formats final responses.

------------------------------------------------------------
4. REASONING-FIRST PIPELINE (ABSOLUTE RULE)
------------------------------------------------------------

❌ FORBIDDEN FLOW:
```
User → Prompt → LLM → Answer
```

✅ REQUIRED FLOW:

1. Query Normalization
2. Concept Extraction
3. Intent Hypothesis (RAG-backed)
4. Capability Decision
5. Execution Plan Creation
6. Context Assembly
7. Prompt Compilation
8. LLM Reasoning
9. Post-Response Validation
10. Final Response Shaping

Any deviation = architectural violation.

------------------------------------------------------------
5. CONCEPT-BASED UNDERSTANDING (REAL NLP)
------------------------------------------------------------

DO NOT:
- Replace strings
- Rewrite user queries
- Depend on keywords

DO:
- Use embeddings
- Map to canonical concepts

Example internal representation:

```json
{
  "raw_query": "bhai CC ka bill dikha",
  "concepts": {
    "CREDIT_CARD": 0.94,
    "BILL_STATEMENT": 0.90
  }
}
```

Original query is preserved.
Concepts guide reasoning.

------------------------------------------------------------
6. LLM POSITIONING (STRICT)
------------------------------------------------------------

LLMs are:
- Stateless
- Replaceable
- Provider-agnostic
- Used ONLY for reasoning + language generation

Axis AI logic MUST NOT:
- Depend on model quirks
- Depend on prompt hacks
- Depend on temperature tricks

------------------------------------------------------------
7. MODULE STRUCTURE (KERNEL-FIRST)
------------------------------------------------------------

```
ai-orchestrator-intelligence
├── kernel/
│   ├── planner/
│   │   └── ReasoningPlanner
│   ├── rag/
│   │   ├── RagEngine
│   │   ├── DomainRetriever
│   │   ├── IntentRetriever
│   │   ├── ToolRetriever
│   │   └── ResponsePatternRetriever
│   ├── prompt/
│   │   └── PromptCompiler
│   ├── tool/
│   │   └── ToolDispatcher
│   ├── memory/
│   │   └── MemoryManager
│   ├── policy/
│   │   └── ComplianceGuard
│   └── response/
│       └── ResponseShaper
└── service/ (business executors only)
```

------------------------------------------------------------
8. NON-NEGOTIABLE SYSTEM RULES
------------------------------------------------------------

1. NO service may call an LLM directly
2. ALL LLM calls go through Kernel
3. ALL decisions must be logged
4. ALL actions must be auditable
5. EVERYTHING must be configurable
6. NO hardcoding of intelligence
7. Determinism > Creativity

------------------------------------------------------------
9. FINAL DEFINITION (ONE LINE)
------------------------------------------------------------

"Axis AI is a reasoning-centric, bank-grade AI SYSTEM in Java,
where architecture creates intelligence,
and LLMs are just interchangeable reasoning engines."

------------------------------------------------------------
END OF DOCUMENT
------------------------------------------------------------
