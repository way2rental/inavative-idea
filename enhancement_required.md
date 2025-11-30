Good work on the foundation. Now I need you to UPGRADE this from POC to a REAL enterprise runtime platform.

Implement the following STRICTLY:

1. Create TWO Ollama Modelfiles:
   - enterprise-intent
   - enterprise-formatter
   And remove intent rules from PromptTemplates.java.

2. Replace ALL mock data in:
   - TxnStatusExecutor
   - FileStatusExecutor
   - AccountSummaryExecutor
   With REAL Http Call with WebClient, and call timeouts.
   // API call is Configurable based on intent will think how to send request body dynamically. for now send whatever we can send max 
   there will be another service(for now module to get the Data) who will serve BusinessData back to our Executor
   
3. Convert ALL Ollama calls from BLOCKING to NON-BLOCKING:
   - No .block()
   - Use Mono / WebFlux end-to-end
   - Add SSE streaming for chat responses.

4. Implement PROMPT SCALING:
   - Two-stage intent detection:
     Stage 1: Category (PAYMENT / FILE / ACCOUNT)
     Stage 2: Exact scenario
   OR
   - Vector embedding scenario filtering.

5. Make scenario routing FULLY DB-DRIVEN:
   - Scenario executors still Spring beans
   - Routing decision must come from ai_scenarios table
   - Enable/disable scenario without redeploy.

6. Add STRICT runtime protections:
   - Max execution time per scenario
   - Hard DB query timeout
   - Hard Ollama timeout
   - Per-user rate limit

7. Add structured performance logging:
   - Intent detection time
   - DB execution time
   - Formatting time
   - End-to-end response time

Return ONLY the updated design + code changes for these upgrades.
