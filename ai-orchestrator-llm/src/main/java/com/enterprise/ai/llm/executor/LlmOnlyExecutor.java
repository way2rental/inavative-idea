package com.enterprise.ai.llm.executor;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.core.scenario.DynamicExecutor;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.service.SystemConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GENERIC Dynamic executor for LLM_ONLY execution type.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * FULLY CONFIGURABLE FROM ADMIN PANEL - NO HARDCODED SCENARIO LOGIC
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This executor uses ONLY the configuration from AiScenario entity:
 * 
 * - llmPromptTemplate: The prompt template with {{variable}} placeholders
 * - description: Context for the AI about what this scenario does
 * - triggerPhrases: Help AI understand when to use this scenario
 * - exampleQueries: Example user queries for context
 * - requiredParams: Parameters that must be provided (JSON array)
 * 
 * VARIABLE SUBSTITUTION (automatically available in templates):
 * ─────────────────────────────────────────────────────────────
 * From Request Params:
 *   {{paramName}} - Any parameter from request.params
 *   
 * From SystemConfig (no hardcoding):
 *   {{orgName}} - Organization name from BRANDING.ORG_NAME
 *   {{orgShortName}} - Short name from BRANDING.ORG_SHORT_NAME
 *   {{assistantName}} - AI assistant name from BRANDING.AI_ASSISTANT_NAME
 *   {{assistantFullName}} - Full name from BRANDING.AI_ASSISTANT_FULL_NAME
 *   {{supportEmail}} - From BRANDING.SUPPORT_EMAIL (if configured)
 *   {{currencySymbol}} - From BRANDING.CURRENCY_SYMBOL (if configured)
 *   
 * Auto-generated:
 *   {{date}} - Current date (format from BRANDING.DATE_FORMAT or default)
 *   {{datetime}} - Current datetime
 *   {{timestamp}} - Unix timestamp
 *   
 * From Scenario Entity:
 *   {{scenarioCode}} - The scenario code
 *   {{scenarioName}} - Human-readable scenario name
 *   {{description}} - Scenario description
 *   {{category}} - Scenario category
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * TO ADD A NEW LLM_ONLY SCENARIO (NO CODE CHANGES REQUIRED):
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Go to Admin Panel → Scenarios
 * 2. Create new scenario with:
 *    - execution_type = LLM_ONLY
 *    - llm_prompt_template = Your prompt with {{variables}}
 *    - required_params = ["param1", "param2"] (JSON array)
 *    - trigger_phrases = ["phrase1", "phrase2"] for AI detection
 *    - example_queries = ["Example query 1", "Example query 2"]
 * 3. Add RBAC mapping for roles that can access this scenario
 * 4. Done! The scenario is immediately available.
 * 
 * EXAMPLE EMAIL DRAFT SCENARIO TEMPLATE:
 * ───────────────────────────────────────
 * You are {{assistantName}}, helping a customer of {{orgName}} draft an email.
 * 
 * Customer Name: {{customerName}}
 * Account: {{accountNumber}}
 * Email Type: {{emailType}}
 * Issue: {{issueDescription}}
 * Chat History: {{chatHistory}}
 * 
 * Generate a professional banking email in JSON format:
 * {
 *   "type": "EMAIL",
 *   "payload": {
 *     "subject": "...",
 *     "body": "...",
 *     "to": "support@{{orgShortName}}.com"
 *   }
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmOnlyExecutor implements DynamicExecutor {

    private static final long DEFAULT_TIMEOUT_MS = 30000;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final ChatClient chatClient;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String executionType) {
        return ExecutionType.LLM_ONLY.name().equals(executionType);
    }

    @Override
    public ScenarioResult execute(ScenarioRequest request, AiScenario scenario) {
        long startTime = System.currentTimeMillis();
        String scenarioCode = scenario.getScenarioCode();

        try {
            log.info("Executing LLM_ONLY scenario: {} with params: {}", scenarioCode, 
                    request.getParams() != null ? request.getParams().keySet() : "none");

            // Check if llmPromptTemplate is configured
            String template = scenario.getLlmPromptTemplate();
            if (template == null || template.isBlank()) {
                log.error("No llm_prompt_template configured for LLM_ONLY scenario: {}", scenarioCode);
                return ScenarioResult.builder()
                        .scenario(scenarioCode)
                        .success(false)
                        .errorMessage("This scenario is not properly configured. Please configure llm_prompt_template in Admin Panel.")
                        .data(Map.of(
                                "error", "MISSING_TEMPLATE",
                                "hint", "Set llm_prompt_template in Admin Panel for scenario: " + scenarioCode
                        ))
                        .build();
            }

            // Validate required params
            List<String> missingParams = validateRequiredParams(request, scenario);
            if (!missingParams.isEmpty()) {
                log.warn("Missing required params for {}: {}", scenarioCode, missingParams);
                return ScenarioResult.builder()
                        .scenario(scenarioCode)
                        .success(false)
                        .errorMessage("Missing required information: " + String.join(", ", missingParams))
                        .data(Map.of("missingParams", missingParams))
                        .build();
            }

            // Build the prompt by substituting all variables
            String prompt = substituteAllVariables(template, request, scenario);
            
            log.debug("LLM prompt for {} (length={})", scenarioCode, prompt.length());
            if (log.isTraceEnabled()) {
                log.trace("Full prompt: {}", prompt);
            }

            // Call LLM
            String llmResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("LLM response for {} (length={})", scenarioCode, 
                    llmResponse != null ? llmResponse.length() : 0);

            // Parse and structure the response
            Map<String, Object> responseData = parseResponse(llmResponse, scenario);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("LLM_ONLY scenario {} completed in {}ms", scenarioCode, executionTime);

            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(true)
                    .data(responseData)
                    .build();

        } catch (Exception e) {
            log.error("LLM_ONLY execution failed for {}: {}", scenarioCode, e.getMessage(), e);
            return ScenarioResult.builder()
                    .scenario(scenarioCode)
                    .success(false)
                    .errorMessage("Failed to generate content. Please try again.")
                    .data(Map.of("error", "Content generation error"))
                    .build();
        }
    }

    @Override
    public Mono<ScenarioResult> executeReactive(ScenarioRequest request, AiScenario scenario) {
        long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;

        return Mono.fromCallable(() -> execute(request, scenario))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(e -> {
                    log.error("Reactive LLM_ONLY failed for {}: {}", scenario.getScenarioCode(), e.getMessage());
                    return Mono.just(ScenarioResult.builder()
                            .scenario(scenario.getScenarioCode())
                            .success(false)
                            .errorMessage("Content generation timeout or error. Please try again.")
                            .build());
                });
    }

    @Override
    public ScenarioResult executeDryRun(ScenarioRequest request, AiScenario scenario) {
        try {
            String template = scenario.getLlmPromptTemplate();
            String prompt = template != null ? substituteAllVariables(template, request, scenario) : "NO_TEMPLATE";
            List<String> missingParams = validateRequiredParams(request, scenario);

            Map<String, Object> dryRunData = new LinkedHashMap<>();
            dryRunData.put("dryRun", true);
            dryRunData.put("executionType", ExecutionType.LLM_ONLY.name());
            dryRunData.put("scenarioCode", scenario.getScenarioCode());
            dryRunData.put("hasTemplate", template != null && !template.isBlank());
            dryRunData.put("templateLength", template != null ? template.length() : 0);
            dryRunData.put("promptPreview", prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
            dryRunData.put("providedParams", request.getParams() != null ? request.getParams().keySet() : List.of());
            dryRunData.put("missingParams", missingParams);
            dryRunData.put("timeoutMs", scenario.getTimeoutMs());

            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(missingParams.isEmpty() && template != null)
                    .data(dryRunData)
                    .build();
        } catch (Exception e) {
            return ScenarioResult.builder()
                    .scenario(scenario.getScenarioCode())
                    .success(false)
                    .errorMessage("Dry-run validation failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Validate that all required parameters are provided.
     * Required params are defined in scenario.requiredParams as JSON array.
     */
    private List<String> validateRequiredParams(ScenarioRequest request, AiScenario scenario) {
        List<String> missing = new ArrayList<>();
        
        String requiredParamsJson = scenario.getRequiredParams();
        if (requiredParamsJson == null || requiredParamsJson.isBlank()) {
            return missing; // No required params
        }

        try {
            List<String> requiredParams = objectMapper.readValue(requiredParamsJson, 
                    new TypeReference<List<String>>() {});
            
            Map<String, Object> providedParams = request.getParams();
            if (providedParams == null) {
                providedParams = Map.of();
            }

            for (String param : requiredParams) {
                if (!providedParams.containsKey(param) || 
                    providedParams.get(param) == null ||
                    providedParams.get(param).toString().isBlank()) {
                    missing.add(param);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse requiredParams for {}: {}", scenario.getScenarioCode(), e.getMessage());
        }

        return missing;
    }

    /**
     * Substitute ALL variables in the template.
     * Variables come from: request params, SystemConfig, scenario entity, and auto-generated values.
     */
    private String substituteAllVariables(String template, ScenarioRequest request, AiScenario scenario) {
        // Build the complete variable map
        Map<String, String> variables = buildVariableMap(request, scenario);
        
        // Substitute all {{variable}} patterns
        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "{{" + varName + "}}"); // Keep original if not found
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Build the complete map of all available variables for substitution.
     */
    private Map<String, String> buildVariableMap(ScenarioRequest request, AiScenario scenario) {
        Map<String, String> variables = new HashMap<>();
        
        // 1. From SystemConfig (no hardcoding - all from DB)
        variables.put("orgName", systemConfigService.getOrgName());
        variables.put("orgShortName", getConfigValue("ORG_SHORT_NAME", systemConfigService.getOrgName()));
        variables.put("assistantName", systemConfigService.getAssistantName());
        variables.put("assistantFullName", systemConfigService.getAssistantFullName());
        variables.put("supportEmail", getConfigValue("SUPPORT_EMAIL", "support@bank.com"));
        variables.put("escalationEmail", getConfigValue("ESCALATION_EMAIL", "escalations@bank.com"));
        variables.put("currencySymbol", getConfigValue("CURRENCY_SYMBOL", "₹"));
        variables.put("dateFormat", getConfigValue("DATE_FORMAT", "dd MMMM yyyy"));
        
        // 2. Auto-generated date/time values
        String dateFormat = variables.get("dateFormat");
        try {
            variables.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormat)));
        } catch (Exception e) {
            variables.put("date", LocalDate.now().toString());
        }
        variables.put("datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        variables.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        // 3. From Scenario entity
        variables.put("scenarioCode", scenario.getScenarioCode());
        variables.put("scenarioName", scenario.getScenarioName() != null ? scenario.getScenarioName() : scenario.getScenarioCode());
        variables.put("description", scenario.getDescription() != null ? scenario.getDescription() : "");
        variables.put("category", scenario.getCategory() != null ? scenario.getCategory() : "");
        
        // 4. From Request params (these override if same key exists)
        if (request.getParams() != null) {
            for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                variables.put(entry.getKey(), value);
            }
        }
        
        // 5. Special request fields
        if (request.getUserId() != null) {
            variables.put("userId", request.getUserId());
        }
        if (request.getSessionId() != null) {
            variables.put("sessionId", request.getSessionId());
        }
        
        return variables;
    }

    /**
     * Get config value with fallback default.
     */
    private String getConfigValue(String key, String defaultValue) {
        try {
            String value = systemConfigService.getString(key, defaultValue);
            return value != null && !value.isBlank() ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parse LLM response into structured data.
     * Tries to extract JSON, falls back to text response.
     */
    private Map<String, Object> parseResponse(String llmResponse, AiScenario scenario) {
        if (llmResponse == null || llmResponse.isBlank()) {
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("type", "ERROR");
            errorResponse.put("payload", Map.of("message", "No response generated"));
            return errorResponse;
        }

        try {
            // Try to extract JSON from response
            String jsonPart = extractJson(llmResponse);
            if (jsonPart != null) {
                Map<String, Object> parsed = objectMapper.readValue(jsonPart, 
                        new TypeReference<Map<String, Object>>() {});
                
                // Ensure required fields exist
                if (!parsed.containsKey("type")) {
                    parsed.put("type", "TEXT");
                }
                if (!parsed.containsKey("payload")) {
                    parsed.put("payload", Map.of("content", llmResponse));
                }
                
                return parsed;
            }
        } catch (Exception e) {
            log.debug("Could not parse LLM response as JSON for {}: {}", 
                    scenario.getScenarioCode(), e.getMessage());
        }

        // Fallback: wrap as text response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "TEXT");
        response.put("title", scenario.getScenarioName() != null ? scenario.getScenarioName() : "Response");
        response.put("payload", Map.of("content", llmResponse));
        response.put("suggestedFollowUps", List.of("Continue", "Start over", "Help"));
        return response;
    }

    /**
     * Extract JSON object from text (finds first { to last }).
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = text.substring(start, end + 1);
            // Basic validation - check if it's valid JSON structure
            try {
                objectMapper.readTree(json);
                return json;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
