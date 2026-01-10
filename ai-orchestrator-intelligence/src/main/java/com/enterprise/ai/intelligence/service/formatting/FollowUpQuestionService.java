package com.enterprise.ai.intelligence.service.formatting;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.FollowUpTemplateRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for generating follow-up questions using DB-driven templates.
 * Uses Freemarker templates from ai_followup_templates.
 * 
 * NO HARDCODING - All templates from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpQuestionService {

    private final ConfigCacheService configCacheService;
    private final SystemConfigService systemConfigService;
    private final FollowUpTemplateRepository followUpTemplateRepository;
    private final Configuration freemarkerConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate follow-up question for missing parameters
     */
    public Mono<String> generateQuestion(String scenarioCode, List<String> missingParams) {
        return Mono.fromCallable(() -> {
            try {
                // Get scenario configuration
                AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioCode));
                
                // Get follow-up template from database
                // For now, use first missing param (can be enhanced to use specific templates)
                String firstMissingParam = missingParams.isEmpty() ? "information" : missingParams.get(0);
                
                var templateOpt = followUpTemplateRepository.findByScenarioCodeAndParamNameAndActiveTrue(
                    scenarioCode, firstMissingParam).stream().findFirst();
                
                if (templateOpt.isPresent()) {
                    var template = templateOpt.get();
                    return processTemplate(template.getTemplateQuestion(), scenarioCode, missingParams);
                }
                
                // Default template if none found
                return generateDefaultQuestion(scenarioCode, missingParams);
                
            } catch (Exception e) {
                log.error("Follow-up question generation failed for scenario {}: {}", scenarioCode, e.getMessage(), e);
                return generateDefaultQuestion(scenarioCode, missingParams);
            }
        });
    }
    
    /**
     * Process Freemarker template
     */
    private String processTemplate(String templateString, String scenarioCode, List<String> missingParams) {
        try {
            Template template = new Template("followup", templateString, freemarkerConfig);
            Map<String, Object> dataModel = new HashMap<>();
            
            // Add SystemConfig variables
            dataModel.put("assistantName", systemConfigService.getAssistantName());
            dataModel.put("orgName", systemConfigService.getOrgName());
            
            // Add scenario data
            dataModel.put("scenarioCode", scenarioCode);
            dataModel.put("missingParams", missingParams);
            dataModel.put("firstMissingParam", missingParams.isEmpty() ? "information" : missingParams.get(0));
            
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
            
        } catch (Exception e) {
            log.warn("Template processing failed, using default question: {}", e.getMessage());
            return generateDefaultQuestion(scenarioCode, missingParams);
        }
    }
    
    /**
     * Generate default question (fallback)
     */
    private String generateDefaultQuestion(String scenarioCode, List<String> missingParams) {
        try {
            String question = "Could you please provide the following information: " + 
                String.join(", ", missingParams) + "?";
            
            // Return as structured JSON
            Map<String, Object> response = new HashMap<>();
            response.put("type", "FOLLOW_UP");
            response.put("payload", Map.of("question", question, "missingParams", missingParams));
            
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            log.error("Default question generation failed: {}", e.getMessage());
            return "Could you please provide the required information?";
        }
    }
}
