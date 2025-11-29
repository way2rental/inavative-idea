package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.LlmException;
import com.enterprise.ai.llm.config.OllamaProperties;
import com.enterprise.ai.llm.prompt.PromptTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama LLM client implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaLlmClient implements LlmClient {

    private final WebClient ollamaWebClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "detectIntentFallback")
    public IntentResult detectIntent(String userInput, String sessionContext) {
        String prompt = PromptTemplates.buildIntentDetectionPrompt(userInput, sessionContext);
        String response = callOllama(prompt);
        return parseIntentResult(response);
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "generateFollowUpFallback")
    public String generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
        String prompt = PromptTemplates.buildFollowUpPrompt(scenarioCode, missingParams);
        return callOllama(prompt);
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "formatResponseFallback")
    public String formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            String prompt = PromptTemplates.buildResponseFormattingPrompt(scenarioCode, dataJson, userQuery);
            return callOllama(prompt);
        } catch (Exception e) {
            log.error("Error formatting response", e);
            return "Your request has been processed successfully.";
        }
    }

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        try {
            String response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return node.has("response") ? node.get("response").asText() : response;
        } catch (Exception e) {
            log.error("Error calling Ollama API", e);
            throw new LlmException("Failed to communicate with Ollama", e);
        }
    }

    private IntentResult parseIntentResult(String response) {
        try {
            // Extract JSON from response (LLM might add extra text)
            String jsonPart = extractJson(response);
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

            return IntentResult.builder()
                    .scenario(node.has("scenario") ? node.get("scenario").asText() : "UNKNOWN")
                    .confidence(node.has("confidence") ? node.get("confidence").asDouble() : 0.0)
                    .params(params)
                    .missingParams(missingParams)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing intent result: {}", response, e);
            return IntentResult.builder()
                    .scenario("UNKNOWN")
                    .confidence(0.0)
                    .params(new HashMap<>())
                    .missingParams(new ArrayList<>())
                    .build();
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // Fallback methods for circuit breaker
    public IntentResult detectIntentFallback(String userInput, String sessionContext, Throwable t) {
        log.warn("Fallback for detectIntent due to: {}", t.getMessage());
        return IntentResult.builder()
                .scenario("UNKNOWN")
                .confidence(0.0)
                .params(new HashMap<>())
                .missingParams(new ArrayList<>())
                .build();
    }

    public String generateFollowUpFallback(String scenarioCode, List<String> missingParams, Throwable t) {
        log.warn("Fallback for generateFollowUp due to: {}", t.getMessage());
        return "Could you please provide more details?";
    }

    public String formatResponseFallback(String scenarioCode, ScenarioResult result, String userQuery, Throwable t) {
        log.warn("Fallback for formatResponse due to: {}", t.getMessage());
        return "Your request has been processed. Here is the result: " + result.getData();
    }
}
