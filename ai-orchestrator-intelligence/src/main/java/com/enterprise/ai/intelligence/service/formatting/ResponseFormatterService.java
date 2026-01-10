package com.enterprise.ai.intelligence.service.formatting;

import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.dto.StructuredChatResponse;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.ResponseTemplate;
import com.enterprise.ai.data.repository.AiResponseMappingRepository;
import com.enterprise.ai.data.repository.ResponseTemplateRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.data.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for formatting responses using DB-driven templates.
 * Uses Freemarker templates from database.
 * 
 * NO HARDCODING - All templates from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseFormatterService {

    private final ConfigCacheService configCacheService;
    private final SystemConfigService systemConfigService;
    private final AiResponseMappingRepository responseMappingRepository;
    private final ResponseTemplateRepository responseTemplateRepository;
    private final Configuration freemarkerConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Format response as JSON by default, or Freemarker template if explicitly configured.
     * 
     * Strategy:
     * - Default: Convert ScenarioResult to StructuredChatResponse JSON (for UI rendering)
     * - Special cases: Use Freemarker template only if explicitly configured in DB
     *   (e.g., for email drafting scenarios where formatted text is needed)
     */
    public Mono<String> formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        return Mono.fromCallable(() -> {
            try {
                // Get scenario configuration
                AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioCode));
                
                // Check if Freemarker template is explicitly configured (for special cases like email drafting)
                // Only use Freemarker if a template is configured with useFreemarker flag or in specific scenarios
                boolean useFreemarker = shouldUseFreemarker(scenarioCode);
                
                if (useFreemarker) {
                    // Use Freemarker template (for special cases like email drafting)
                    String templateContent = getTemplateContent(scenarioCode, result);
                    if (templateContent != null && !templateContent.isEmpty()) {
                        log.debug("Using Freemarker template for scenario {}", scenarioCode);
                        return processTemplate(templateContent, scenarioCode, result, userQuery);
                    }
                }
                
                // Default: Convert to StructuredChatResponse JSON format (for UI rendering)
                log.debug("Using JSON formatting for scenario {}", scenarioCode);
                return formatAsStructuredJson(scenarioCode, result, userQuery);
                
            } catch (Exception e) {
                log.error("Response formatting failed for scenario {}: {}", scenarioCode, e.getMessage(), e);
                return formatAsStructuredJson(scenarioCode, result, userQuery);
            }
        });
    }
    
    /**
     * Determine if Freemarker should be used for this scenario.
     * Only use Freemarker for special cases like email drafting.
     */
    private boolean shouldUseFreemarker(String scenarioCode) {
        // Check if there's an active Freemarker template configured
        List<ResponseTemplate> templates = responseTemplateRepository
            .findByScenarioCodeAndActiveTrueOrderByPriorityDesc(scenarioCode);
        
        // Only use Freemarker if templates are explicitly configured
        // Common scenarios (like transaction history) should use JSON for UI rendering
        return !templates.isEmpty();
    }
    
    /**
     * Get template content from database or scenario fallback.
     */
    private String getTemplateContent(String scenarioCode, ScenarioResult result) {
        String responseType = determineResponseType(result);
        
        // Try to get template from database by scenario code and response type
        ResponseTemplate dbTemplate = responseTemplateRepository
            .findFirstByScenarioCodeAndResponseTypeAndActiveTrueOrderByPriorityDesc(scenarioCode, responseType)
            .orElse(null);
        
        if (dbTemplate != null) {
            return dbTemplate.getTemplateContent();
        }
        
        // Fallback: try any active template for this scenario
        List<ResponseTemplate> scenarioTemplates = responseTemplateRepository
            .findByScenarioCodeAndActiveTrueOrderByPriorityDesc(scenarioCode);
        if (!scenarioTemplates.isEmpty()) {
            return scenarioTemplates.get(0).getTemplateContent();
        }
        
        // Final fallback: use scenario's llmPromptTemplate
        AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode).orElse(null);
        if (scenario != null && scenario.getLlmPromptTemplate() != null) {
            return scenario.getLlmPromptTemplate();
        }
        
        return null;
    }
    
    /**
     * Process Freemarker template
     */
    private String processTemplate(String templateString, String scenarioCode, ScenarioResult result, String userQuery) {
        try {
            Template template = new Template("response", templateString, freemarkerConfig);
            Map<String, Object> dataModel = new HashMap<>();
            
            // Add SystemConfig variables
            dataModel.put("assistantName", systemConfigService.getAssistantName());
            dataModel.put("orgName", systemConfigService.getOrgName());
            dataModel.put("currencySymbol", "$");
            
            // Add scenario data
            dataModel.put("scenarioCode", scenarioCode);
            dataModel.put("scenarioName", configCacheService.getScenarioByCode(scenarioCode)
                .map(AiScenario::getScenarioName).orElse(scenarioCode));
            dataModel.put("result", result);
            
            // Extract data from result - handle nested structure from ResponseMappingService
            // ResponseMappingService returns: {"scenario": "...", "type": "multiple", "data": [...], "count": 5}
            // For templates, we want to expose the actual list/object directly as "data"
            Map<String, Object> resultData = result.getData();
            if (resultData != null) {
                // Check if data is wrapped in a map structure from ResponseMappingService
                if (resultData.containsKey("data") && resultData.get("data") != null) {
                    // Extract the actual data array/object
                    Object actualData = resultData.get("data");
                    dataModel.put("data", actualData);
                    
                    // Also expose the wrapper structure for advanced templates
                    dataModel.put("resultData", resultData);
                    dataModel.put("dataType", resultData.get("type"));
                    dataModel.put("dataCount", resultData.get("count"));
                } else {
                    // Direct data (no wrapper)
                    dataModel.put("data", resultData);
                }
            } else {
                dataModel.put("data", null);
            }
            
            dataModel.put("userQuery", userQuery);
            
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
            
        } catch (Exception e) {
            log.warn("Template processing failed, using default formatting: {}", e.getMessage());
            return formatAsStructuredJson(scenarioCode, result, userQuery);
        }
    }
    
    /**
     * Format as StructuredChatResponse JSON (default format for UI rendering).
     * Converts ScenarioResult to the JSON structure the UI expects.
     */
    private String formatAsStructuredJson(String scenarioCode, ScenarioResult result, String userQuery) {
        try {
            if (!result.isSuccess()) {
                // Error response
                String errorMessage = result.getErrorMessage() != null ? result.getErrorMessage() : "Request failed";
                StructuredChatResponse errorResponse = StructuredChatResponse.error(errorMessage, null);
                errorResponse.setScenario(scenarioCode);
                return objectMapper.writeValueAsString(errorResponse);
            }
            
            Map<String, Object> resultData = result.getData();
            if (resultData == null || resultData.isEmpty()) {
                // Empty result
                StructuredChatResponse textResponse = StructuredChatResponse.text(
                    getScenarioTitle(scenarioCode),
                    "No data found for your request.",
                    null
                );
                textResponse.setScenario(scenarioCode);
                return objectMapper.writeValueAsString(textResponse);
            }
            
            // Extract data from nested structure (ResponseMappingService format)
            // ResponseMappingService returns: {"scenario": "...", "type": "multiple", "data": [...], "count": 5}
            Object actualData = null;
            String dataType = null;
            Integer dataCount = null;
            
            if (resultData.containsKey("data")) {
                actualData = resultData.get("data");
                Object typeObj = resultData.get("type");
                dataType = typeObj != null ? typeObj.toString() : null;
                Object countObj = resultData.get("count");
                dataCount = countObj instanceof Integer ? (Integer) countObj : 
                           (countObj instanceof Number ? ((Number) countObj).intValue() : null);
            } else {
                actualData = resultData;
            }
            
            // Convert to StructuredChatResponse based on data structure
            if (actualData instanceof List) {
                // List of items - convert to TABLE format
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) actualData;
                
                if (items.isEmpty()) {
                    StructuredChatResponse textResponse = StructuredChatResponse.text(
                        getScenarioTitle(scenarioCode),
                        "No data found.",
                        null
                    );
                    textResponse.setScenario(scenarioCode);
                    return objectMapper.writeValueAsString(textResponse);
                }
                
                // Extract columns from first item
                Map<String, Object> firstItem = items.get(0);
                List<String> columns = new ArrayList<>(firstItem.keySet());
                
                // Convert items to rows
                List<List<String>> rows = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    List<String> row = new ArrayList<>();
                    for (String col : columns) {
                        Object value = item.get(col);
                        row.add(value != null ? value.toString() : "");
                    }
                    rows.add(row);
                }
                
                StructuredChatResponse tableResponse = StructuredChatResponse.table(
                    getScenarioTitle(scenarioCode),
                    columns,
                    rows,
                    null
                );
                tableResponse.setScenario(scenarioCode);
                if (dataCount != null) {
                    tableResponse.setFooter("Total: " + dataCount + " records");
                }
                return objectMapper.writeValueAsString(tableResponse);
                
            } else if (actualData instanceof Map) {
                // Single object - convert to KV format
                @SuppressWarnings("unchecked")
                Map<String, Object> kvData = (Map<String, Object>) actualData;
                
                StructuredChatResponse kvResponse = StructuredChatResponse.kv(
                    getScenarioTitle(scenarioCode),
                    kvData,
                    null
                );
                kvResponse.setScenario(scenarioCode);
                return objectMapper.writeValueAsString(kvResponse);
                
            } else {
                // Simple value - convert to TEXT
                StructuredChatResponse textResponse = StructuredChatResponse.text(
                    getScenarioTitle(scenarioCode),
                    actualData != null ? actualData.toString() : "Response processed",
                    null
                );
                textResponse.setScenario(scenarioCode);
                return objectMapper.writeValueAsString(textResponse);
            }
            
        } catch (Exception e) {
            log.error("JSON formatting failed: {}", e.getMessage(), e);
            // Fallback to simple JSON
            try {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("type", "TEXT");
                fallback.put("title", getScenarioTitle(scenarioCode));
                fallback.put("payload", Map.of("message", "Response processed"));
                fallback.put("scenario", scenarioCode);
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception ex) {
                return "{\"type\":\"TEXT\",\"payload\":{\"message\":\"Response processed\"}}";
            }
        }
    }
    
    /**
     * Get scenario title for response.
     */
    private String getScenarioTitle(String scenarioCode) {
        return configCacheService.getScenarioByCode(scenarioCode)
            .map(AiScenario::getScenarioName)
            .orElse(scenarioCode.replace("_", " "));
    }
    
    /**
     * Determine response type based on result data.
     * Types: TEXT, TABLE, KV, MIXED, FOLLOW_UP, ERROR
     */
    private String determineResponseType(ScenarioResult result) {
        if (!result.isSuccess()) {
            return "ERROR";
        }
        
        if (result.getData() == null || result.getData().isEmpty()) {
            return "TEXT";
        }
        
        Object firstValue = result.getData().values().iterator().next();
        if (firstValue instanceof List) {
            return "TABLE";
        } else if (firstValue instanceof Map) {
            return "MIXED";
        } else {
            return "KV";
        }
    }
}
