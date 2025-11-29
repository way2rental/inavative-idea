package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.LlmException;
import com.enterprise.ai.llm.config.OllamaProperties;
import com.enterprise.ai.llm.prompt.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama LLM client implementation with proper error handling and retry logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaLlmClient implements LlmClient {

    private final WebClient ollamaWebClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        if (properties.isEnabled()) {
            log.info("Ollama LLM Client initialized with base URL: {} and model: {}", 
                    properties.getBaseUrl(), properties.getModel());
        } else {
            log.warn("Ollama LLM Client is DISABLED. All calls will use fallback responses.");
        }
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "detectIntentFallback")
    @Retry(name = "ollama")
    public IntentResult detectIntent(String userInput, String sessionContext) {
        if (!properties.isEnabled()) {
            log.debug("Ollama disabled, using fallback for intent detection");
            return detectIntentFallback(userInput, sessionContext, new LlmException("Ollama disabled"));
        }
        
        String prompt = PromptTemplates.buildIntentDetectionPrompt(userInput, sessionContext);
        String response = callOllama(prompt);
        return parseIntentResult(response);
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "generateFollowUpFallback")
    @Retry(name = "ollama")
    public String generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
        if (!properties.isEnabled()) {
            return generateFollowUpFallback(scenarioCode, missingParams, new LlmException("Ollama disabled"));
        }
        
        String prompt = PromptTemplates.buildFollowUpPrompt(scenarioCode, missingParams);
        return callOllama(prompt).trim();
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "formatResponseFallback")
    @Retry(name = "ollama")
    public String formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        if (!properties.isEnabled()) {
            return formatResponseFallback(scenarioCode, result, userQuery, new LlmException("Ollama disabled"));
        }
        
        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            String prompt = PromptTemplates.buildResponseFormattingPrompt(scenarioCode, dataJson, userQuery);
            return callOllama(prompt).trim();
        } catch (Exception e) {
            log.error("Error formatting response", e);
            return formatResponseFallback(scenarioCode, result, userQuery, e);
        }
    }

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        requestBody.put("options", Map.of(
                "temperature", 0.3,  // Lower temperature for more consistent outputs
                "num_predict", 1024  // Limit response length
        ));

        try {
            log.debug("Calling Ollama with model: {}", properties.getModel());
            
            String response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            if (response == null || response.isEmpty()) {
                throw new LlmException("Empty response from Ollama");
            }

            JsonNode node = objectMapper.readTree(response);
            
            // Check for error in response
            if (node.has("error")) {
                String error = node.get("error").asText();
                log.error("Ollama returned error: {}", error);
                throw new LlmException("Ollama error: " + error);
            }
            
            return node.has("response") ? node.get("response").asText() : response;
            
        } catch (WebClientResponseException e) {
            log.error("Ollama API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new LlmException("Ollama API error: " + e.getMessage(), e);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage());
            throw new LlmException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }

    private IntentResult parseIntentResult(String response) {
        try {
            // Extract JSON from response (LLM might add extra text)
            String jsonPart = extractJson(response);
            if (jsonPart == null || jsonPart.isEmpty()) {
                log.warn("No JSON found in Ollama response, returning UNKNOWN intent");
                return createUnknownIntent();
            }
            
            JsonNode node = objectMapper.readTree(jsonPart);

            List<String> missingParams = new ArrayList<>();
            if (node.has("missingParams") && node.get("missingParams").isArray()) {
                for (JsonNode param : node.get("missingParams")) {
                    missingParams.add(param.asText());
                }
            }

            Map<String, Object> params = new HashMap<>();
            if (node.has("params") && node.get("params").isObject()) {
                node.get("params").fields().forEachRemaining(
                        entry -> params.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText())
                );
            }

            String scenario = node.has("scenario") ? node.get("scenario").asText() : "UNKNOWN";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;

            log.debug("Parsed intent - scenario: {}, confidence: {}, params: {}, missing: {}", 
                    scenario, confidence, params, missingParams);

            return IntentResult.builder()
                    .scenario(scenario)
                    .confidence(confidence)
                    .params(params)
                    .missingParams(missingParams)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing intent result from response: {}", response, e);
            return createUnknownIntent();
        }
    }

    private String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private IntentResult createUnknownIntent() {
        return IntentResult.builder()
                .scenario("UNKNOWN")
                .confidence(0.0)
                .params(new HashMap<>())
                .missingParams(new ArrayList<>())
                .build();
    }

    // Fallback methods for circuit breaker
    public IntentResult detectIntentFallback(String userInput, String sessionContext, Throwable t) {
        log.warn("Fallback for detectIntent due to: {}", t.getMessage());
        return createUnknownIntent();
    }

    public String generateFollowUpFallback(String scenarioCode, List<String> missingParams, Throwable t) {
        log.warn("Fallback for generateFollowUp due to: {}", t.getMessage());
        if (missingParams != null && !missingParams.isEmpty()) {
            return "Please provide the following information: " + String.join(", ", missingParams);
        }
        return "Could you please provide more details about your request?";
    }

    public String formatResponseFallback(String scenarioCode, ScenarioResult result, String userQuery, Throwable t) {
        log.warn("Fallback for formatResponse due to: {}", t.getMessage());
        if (result != null && result.getData() != null) {
            StringBuilder sb = new StringBuilder("Here is the result for your request:\n");
            result.getData().forEach((key, value) -> 
                    sb.append("- ").append(key).append(": ").append(value).append("\n"));
            return sb.toString();
        }
        return "Your request has been processed successfully.";
    }
}
