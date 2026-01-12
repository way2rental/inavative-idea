package com.enterprise.ai.intelligence.kernel.planner;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.intelligence.service.FallbackLayerOrchestrator;
import com.enterprise.ai.intelligence.service.concept.BankingConceptService;
import com.enterprise.ai.intelligence.service.entity.EntityExtractionService;
import com.enterprise.ai.intelligence.service.entity.EntityMatch;
import com.enterprise.ai.intelligence.service.parameter.ParameterExtractionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reasoning Planner Implementation.
 * 
 * REUSES existing components:
 * - FallbackLayerOrchestrator for intent detection
 * - EntityExtractionService for concept extraction
 * - ParameterExtractionService for parameter extraction
 * 
 * Enhances with:
 * - Query normalization
 * - Concept extraction (embedding-based)
 * - Capability decision
 * - Execution plan generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasoningPlannerImpl implements ReasoningPlanner {

    private final FallbackLayerOrchestrator fallbackLayerOrchestrator;
    private final BankingConceptService bankingConceptService;
    private final EntityExtractionService entityExtractionService;
    private final ParameterExtractionService parameterExtractionService;
    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<ReasoningPlan> createPlan(
            String rawQuery,
            String sessionContext,
            Set<String> allowedScenarios,
            String sessionId,
            String userId) {
        
        return Mono.fromCallable(() -> {
            // Step 1: Query Normalization
            String normalizedQuery = normalizeQuery(rawQuery);
            log.debug("Query normalized: {} -> {}", rawQuery, normalizedQuery);
            
            // Step 2: Concept Extraction (Banking Domain Concepts)
            // Uses BankingConceptService to extract banking concepts from query
            Map<String, Double> concepts = bankingConceptService.extractConcepts(rawQuery);
            log.debug("Extracted {} banking concepts: {}", concepts.size(), concepts.keySet());
            
            // Step 3: Intent Hypothesis (RAG-backed) - REUSE FallbackLayerOrchestrator
            // This is a blocking call but we're already in Mono.fromCallable
            IntentResult intentResult = fallbackLayerOrchestrator.detectIntent(
                    normalizedQuery,
                    sessionContext,
                    null, // lastUsedParamsJson - will be resolved in parameter extraction
                    allowedScenarios,
                    sessionId,
                    userId
            ).block();
            
            if (intentResult == null) {
                return buildUnknownPlan(rawQuery, normalizedQuery, concepts, sessionId, userId, allowedScenarios);
            }
            
            log.debug("Intent detected: scenario={}, confidence={}", intentResult.getScenario(), intentResult.getConfidence());
            
            // Step 4: Parameter Extraction - REUSE ParameterExtractionService
            Map<String, Object> parameters = new HashMap<>();
            List<String> missingParameters = new ArrayList<>();
            
            if (intentResult.getScenario() != null && 
                !intentResult.getScenario().equals("UNKNOWN") && 
                !intentResult.getScenario().equals("AMBIGUOUS")) {
                
                try {
                    // Extract parameters
                    parameters = parameterExtractionService.extractParameters(
                            normalizedQuery, intentResult.getScenario(), sessionId, userId
                    ).block();
                    
                    if (parameters == null) {
                        parameters = new HashMap<>();
                    }
                    
                    // Merge with params from intent result
                    if (intentResult.getParams() != null) {
                        parameters.putAll(intentResult.getParams());
                    }
                    
                    // Get missing parameters
                    missingParameters = parameterExtractionService.getMissingParameters(
                            normalizedQuery, intentResult.getScenario(), sessionId, userId
                    ).block();
                    
                    if (missingParameters == null) {
                        missingParameters = new ArrayList<>();
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract parameters: {}", e.getMessage());
                }
            }
            
            // Step 5: Capability Decision & Execution Plan
            String scenarioCode = intentResult.getScenario();
            String capability = determineCapability(scenarioCode);
            List<String> tools = determineTools(scenarioCode);
            List<String> requiredContext = determineRequiredContext(scenarioCode);
            String responsePattern = determineResponsePattern(scenarioCode);
            String executionPlan = buildExecutionPlan(scenarioCode, capability, tools, parameters);
            
            // Build ReasoningPlan
            return ReasoningPlan.builder()
                    .intent(scenarioCode)
                    .capability(capability)
                    .tools(tools)
                    .requiredContext(requiredContext)
                    .concepts(concepts)
                    .parameters(parameters)
                    .missingParameters(missingParameters)
                    .responsePattern(responsePattern)
                    .confidence(intentResult.getConfidence())
                    .reasoning(intentResult.getReasoning())
                    .normalizedQuery(normalizedQuery)
                    .rawQuery(rawQuery)
                    .executionPlan(executionPlan)
                    .allowedScenarios(allowedScenarios)
                    .sessionId(sessionId)
                    .userId(userId)
                    .build();
        });
    }
    
    /**
     * Normalize query (trim, lowercase, remove extra spaces).
     * Preserves original query separately.
     */
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .replaceAll("\\s+", " ") // Replace multiple spaces with single space
                .toLowerCase();
    }
    
    /**
     * Extract concepts from query using BankingConceptService.
     * 
     * This method is now replaced by BankingConceptService.extractConcepts().
     * Kept for backward compatibility but now delegates to BankingConceptService.
     * 
     * @deprecated Use BankingConceptService directly
     */
    @Deprecated
    private Map<String, Double> extractConceptsLegacy(String query) {
        // Legacy method - now using BankingConceptService
        // This is kept for reference but not used
        Map<String, Double> concepts = new HashMap<>();
        
        try {
            // Also extract entities and add them as concepts (for backward compatibility)
            String scenarioCode = null;
            Map<String, EntityMatch> entities = entityExtractionService.extractEntities(query, scenarioCode).block();
            
            if (entities != null) {
                for (Map.Entry<String, EntityMatch> entry : entities.entrySet()) {
                    String entityType = entry.getKey();
                    EntityMatch match = entry.getValue();
                    
                    // Map entity type to canonical concept
                    String concept = mapEntityToConcept(entityType);
                    double confidence = match.getConfidence() != null 
                            ? match.getConfidence().doubleValue() 
                            : 0.8;
                    
                    concepts.put(concept, confidence);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract concepts: {}", e.getMessage());
        }
        
        return concepts;
    }
    
    /**
     * Map entity type to canonical concept.
     * This is now handled by BankingConceptService, but kept for entity-based concepts.
     */
    private String mapEntityToConcept(String entityType) {
        // Try to find matching banking concept first
        Optional<com.enterprise.ai.data.entity.BankingConcept> concept = 
                bankingConceptService.findMatchingConcept(entityType);
        if (concept.isPresent()) {
            return concept.get().getConceptCode();
        }
        // Fallback: use entity type as concept
        return entityType;
    }
    
    /**
     * Determine capability from scenario execution type.
     */
    private String determineCapability(String scenarioCode) {
        if (scenarioCode == null || scenarioCode.equals("UNKNOWN") || scenarioCode.equals("AMBIGUOUS")) {
            return "DIRECT_ANSWER";
        }
        
        Optional<AiScenario> scenarioOpt = configCacheService.getScenarioByCode(scenarioCode);
        if (scenarioOpt.isEmpty()) {
            return "DIRECT_ANSWER";
        }
        
        AiScenario scenario = scenarioOpt.get();
        String executionType = scenario.getExecutionType();
        
        if (executionType == null) {
            return "DIRECT_ANSWER";
        }
        
        switch (executionType.toUpperCase()) {
            case "DB_QUERY":
            case "HTTP_CALL":
            case "FILE_READ":
            case "KAFKA_CONSUME":
                return "TOOL_EXECUTION";
            case "LLM_ONLY":
                return "DIRECT_ANSWER";
            case "COMPOSITE":
                return "COMPOSITE";
            default:
                return "DIRECT_ANSWER";
        }
    }
    
    /**
     * Determine tools to execute based on scenario.
     */
    private List<String> determineTools(String scenarioCode) {
        if (scenarioCode == null || scenarioCode.equals("UNKNOWN") || scenarioCode.equals("AMBIGUOUS")) {
            return Collections.emptyList();
        }
        
        Optional<AiScenario> scenarioOpt = configCacheService.getScenarioByCode(scenarioCode);
        if (scenarioOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        AiScenario scenario = scenarioOpt.get();
        String executionType = scenario.getExecutionType();
        
        List<String> tools = new ArrayList<>();
        if (executionType != null) {
            tools.add(executionType);
        }
        
        return tools;
    }
    
    /**
     * Determine required context entities from scenario.
     */
    private List<String> determineRequiredContext(String scenarioCode) {
        if (scenarioCode == null || scenarioCode.equals("UNKNOWN") || scenarioCode.equals("AMBIGUOUS")) {
            return Collections.emptyList();
        }
        
        Optional<AiScenario> scenarioOpt = configCacheService.getScenarioByCode(scenarioCode);
        if (scenarioOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        AiScenario scenario = scenarioOpt.get();
        String requiredParams = scenario.getRequiredParams();
        
        if (requiredParams == null || requiredParams.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // Parse required params (JSON array)
            com.fasterxml.jackson.core.type.TypeReference<List<String>> typeRef = 
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {};
            List<String> params = objectMapper.readValue(requiredParams, typeRef);
            return params != null ? params : Collections.emptyList();
        } catch (Exception e) {
            // Try comma-separated
            return Arrays.stream(requiredParams.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Determine response pattern from scenario.
     */
    private String determineResponsePattern(String scenarioCode) {
        // For now, default to "TEXT" - can be enhanced with scenario-specific patterns
        return "TEXT";
    }
    
    /**
     * Build execution plan JSON.
     */
    private String buildExecutionPlan(String scenarioCode, String capability, List<String> tools, Map<String, Object> parameters) {
        try {
            Map<String, Object> plan = new HashMap<>();
            plan.put("scenarioCode", scenarioCode);
            plan.put("capability", capability);
            plan.put("tools", tools);
            plan.put("parameters", parameters);
            plan.put("steps", buildExecutionSteps(capability, tools));
            
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            log.warn("Failed to build execution plan: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Build execution steps.
     */
    private List<String> buildExecutionSteps(String capability, List<String> tools) {
        List<String> steps = new ArrayList<>();
        
        switch (capability) {
            case "TOOL_EXECUTION":
                steps.add("1. Execute tool: " + (tools.isEmpty() ? "UNKNOWN" : tools.get(0)));
                steps.add("2. Retrieve data");
                steps.add("3. Format response");
                break;
            case "DIRECT_ANSWER":
                steps.add("1. Generate direct answer");
                break;
            case "COMPOSITE":
                steps.add("1. Execute composite workflow");
                break;
            default:
                steps.add("1. Process request");
        }
        
        return steps;
    }
    
    /**
     * Build unknown plan.
     */
    private ReasoningPlan buildUnknownPlan(String rawQuery, String normalizedQuery, 
                                           Map<String, Double> concepts, String sessionId, 
                                           String userId, Set<String> allowedScenarios) {
        return ReasoningPlan.builder()
                .intent("UNKNOWN")
                .capability("DIRECT_ANSWER")
                .tools(Collections.emptyList())
                .requiredContext(Collections.emptyList())
                .concepts(concepts)
                .parameters(Collections.emptyMap())
                .missingParameters(Collections.emptyList())
                .responsePattern("TEXT")
                .confidence(0.1)
                .reasoning("No matching intent found")
                .normalizedQuery(normalizedQuery)
                .rawQuery(rawQuery)
                .executionPlan("{}")
                .allowedScenarios(allowedScenarios)
                .sessionId(sessionId)
                .userId(userId)
                .build();
    }
}
