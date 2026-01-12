package com.enterprise.ai.llm.client;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.LlmException;
import com.enterprise.ai.llm.prompt.DynamicPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Spring AI-based LLM Client.
 * Provider-agnostic implementation - works with OpenAI, Azure OpenAI, etc. (API-based only)
 * 
 * Axis AI uses LLMs as interchangeable reasoning engines - no local model runtimes.
 * Uses DynamicPromptBuilder to load prompts from database - NO HARDCODED prompts.
 * To switch providers, just change spring.ai.active-provider in application.yml
 */
@Slf4j
@Service("springAiLlmClient")
public class SpringAiLlmClient implements ReactiveLlmClient {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final DynamicPromptBuilder promptBuilder;

    public SpringAiLlmClient(ChatClient chatClient, ObjectMapper objectMapper, DynamicPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext) {
        return detectIntent(userInput, sessionContext, null, null);
    }

    @Override
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson) {
        return detectIntent(userInput, sessionContext, lastUsedParamsJson, null);
    }

    @Override
    public Mono<IntentResult> detectIntent(String userInput, String sessionContext, String lastUsedParamsJson, Set<String> allowedScenarios) {
        return Mono.fromCallable(() -> detectIntentBlocking(userInput, sessionContext, lastUsedParamsJson, allowedScenarios));
    }

    @Override
    public Mono<String> generateFollowUpQuestion(String scenarioCode, List<String> missingParams) {
        return Mono.fromCallable(() -> generateFollowUpBlocking(scenarioCode, missingParams));
    }

    @Override
    public Mono<String> formatResponse(String scenarioCode, ScenarioResult result, String userQuery) {
        return Mono.fromCallable(() -> formatResponseBlocking(scenarioCode, result, userQuery));
    }

    @Override
    public Mono<IntentResult> detectIntentTwoStage(String userInput, String sessionContext) {
        return detectIntent(userInput, sessionContext);
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(true);
    }

    @Override
    public Flux<String> formatResponseStreaming(String scenarioCode, ScenarioResult result, String userQuery) {
        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            log.debug("Formatting response for scenario: {}, data length: {}", scenarioCode, dataJson.length());

            String prompt = promptBuilder.buildResponseFormattingPrompt(scenarioCode, dataJson, userQuery);
            log.debug("Prompt length: {}, prompt preview: {}", prompt.length(),
                    prompt.length() > 200 ? prompt.substring(0, 200) : prompt);

            return chatClient.prompt()
                    .user(prompt)
                    .stream()
                    .content()
                    .doOnNext(token -> log.trace("LLM token received: {}", token))
                    .collectList()
                    .doOnNext(tokens -> log.debug("Collected {} tokens from LLM", tokens.size()))
                    .map(tokens -> {
                        String joined = String.join("", tokens);
                        log.debug("Joined tokens, total length: {}", joined.length());
                        return joined;
                    })
                    .flatMapMany(completeResponse -> {
                        log.debug("Complete formatted response (length: {}): {}",
                                completeResponse.length(),
                                completeResponse.length() > 200 ? completeResponse.substring(0, 200) + "..." : completeResponse);

                        if (completeResponse.isEmpty() || completeResponse.trim().isEmpty()) {
                            log.error("LLM returned empty response! Using fallback.");
                            return Flux.just(formatResponseFallback(scenarioCode, result, userQuery,
                                    new RuntimeException("Empty LLM response")));
                        }

                        return Flux.just(completeResponse);
                    })
                    .onErrorResume(e -> {
                        log.error("Error in streaming format: {}", e.getMessage(), e);
                        return Flux.just(formatResponseFallback(scenarioCode, result, userQuery, e));
                    });

        } catch (Exception e) {
            log.error("Error in streaming format: {}", e.getMessage(), e);
            return Flux.just(formatResponseFallback(scenarioCode, result, userQuery, e));
        }
    }

    @CircuitBreaker(name = "ollama", fallbackMethod = "detectIntentFallback")
    @Retry(name = "ollama")
    private IntentResult detectIntentBlocking(String userInput, String sessionContext, String lastUsedParamsJson, Set<String> allowedScenarios) {
        try {
            String prompt = promptBuilder.buildIntentDetectionPrompt(userInput, sessionContext, lastUsedParamsJson, allowedScenarios);

            log.debug("Intent detection prompt:\n{}", prompt);

            // Call LLM using Spring AI (provider-agnostic)
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("LLM raw response:\n{}", response);

            return parseIntentResult(response);
        } catch (Exception e) {
            log.error("Error detecting intent: {}", e.getMessage(), e);
            throw new LlmException("Failed to detect intent", e);
        }
    }

    @CircuitBreaker(name = "ollama", fallbackMethod = "generateFollowUpFallback")
    @Retry(name = "ollama")
    private String generateFollowUpBlocking(String scenarioCode, List<String> missingParams) {
        try {
            String prompt = promptBuilder.buildFollowUpPrompt(scenarioCode, missingParams);

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("Error generating follow-up: {}", e.getMessage(), e);
            return generateFollowUpFallback(scenarioCode, missingParams, e);
        }
    }

    @CircuitBreaker(name = "ollama", fallbackMethod = "formatResponseFallback")
    @Retry(name = "ollama")
    private String formatResponseBlocking(String scenarioCode, ScenarioResult result, String userQuery) {
        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());
            String prompt = promptBuilder.buildResponseFormattingPrompt(scenarioCode, dataJson, userQuery);

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("Error formatting response: {}", e.getMessage(), e);
            return formatResponseFallback(scenarioCode, result, userQuery, e);
        }
    }

    // ===== PARSING METHODS =====

    private IntentResult parseIntentResult(String response) {
        try {
            log.debug("Parsing intent result from response (length: {})", response != null ? response.length() : 0);

            String jsonPart = extractJson(response);
            if (jsonPart == null || jsonPart.isEmpty()) {
                log.warn("No JSON found in LLM response, returning UNKNOWN intent. Response was: {}", response);
                return createUnknownIntent();
            }

            log.debug("Extracted JSON: {}", jsonPart);

            // Fix: Remove invalid escape sequences (like \_ )
            jsonPart = jsonPart.replaceAll("\\\\_", "_");

            JsonNode node = objectMapper.readTree(jsonPart);
            log.debug("Parsed JSON node: {}", node);

            List<String> missingParams = new ArrayList<>();
            if (node.has("missingParams") && node.get("missingParams").isArray()) {
                for (JsonNode param : node.get("missingParams")) {
                    missingParams.add(param.asText());
                }
            }

            Map<String, Object> params = new HashMap<>();
            if (node.has("params") && node.get("params").isObject()) {
                node.get("params").fields().forEachRemaining(
                        entry -> params.put(entry.getKey(),
                                entry.getValue().isNull() ? null : entry.getValue().asText())
                );
            }

            List<String> possibleScenarios = new ArrayList<>();
            if (node.has("possibleScenarios") && node.get("possibleScenarios").isArray()) {
                for (JsonNode scenario : node.get("possibleScenarios")) {
                    possibleScenarios.add(scenario.asText());
                }
            }

            String scenario = node.has("scenario") ? node.get("scenario").asText() : "UNKNOWN";
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : null;

            log.debug("Parsed intent - scenario: {}, confidence: {}, params: {}",
                    scenario, confidence, params);

            return IntentResult.builder()
                    .scenario(scenario)
                    .confidence(confidence)
                    .params(params)
                    .missingParams(missingParams)
                    .possibleScenarios(possibleScenarios.isEmpty() ? null : possibleScenarios)
                    .reasoning(reasoning)
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

    // ===== FALLBACK METHODS ====

    public IntentResult detectIntentFallback(String userInput, String sessionContext, Throwable t) {
        log.warn("Fallback for detectIntent due to: {}", t.getMessage());
        return createUnknownIntent();
    }

    /**
     * Fallback for follow-up question generation.
     * Returns a properly structured JSON response that the frontend can parse.
     */
    public String generateFollowUpFallback(String scenarioCode, List<String> missingParams, Throwable t) {
        log.warn("Fallback for generateFollowUp due to: {}", t.getMessage());
        
        String question;
        if (missingParams != null && !missingParams.isEmpty()) {
            String firstParam = missingParams.get(0);
            // Generate user-friendly question based on parameter name
            question = switch (firstParam.toLowerCase()) {
                case "accountid", "account_id" -> "Which account would you like me to check? (e.g., ACC001)";
                case "datefrom", "date_from", "startdate" -> "From which date would you like to see the data?";
                case "dateto", "date_to", "enddate" -> "Until which date would you like to see the data?";
                case "amount", "minamount", "maxamount" -> "What amount range are you looking for?";
                case "transactiontype", "type" -> "What type of transactions - credits, debits, or all?";
                default -> "Could you please provide the " + firstParam.replace("_", " ").replace("Id", " ID") + "?";
            };
        } else {
            question = "Could you please provide more details about your request?";
        }
        
        // Return structured JSON response
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "FOLLOW_UP");
            response.put("title", "");
            response.put("confidence", 1.0);
            
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("question", question);
            payload.put("missingParams", missingParams != null ? missingParams : List.of());
            response.put("payload", payload);
            response.put("scenario", scenarioCode);
            response.put("suggestedFollowUps", List.of());
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            // Last resort fallback - simple JSON
            return "{\"type\":\"FOLLOW_UP\",\"payload\":{\"question\":\"" + question + "\"},\"scenario\":\"" + scenarioCode + "\"}";
        }
    }

    /**
     * Fallback for response formatting.
     * Returns a properly structured JSON response that the frontend can render.
     */
    public String formatResponseFallback(String scenarioCode, ScenarioResult result, String userQuery, Throwable t) {
        log.warn("Fallback for formatResponse due to: {}", t.getMessage());
        
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            
            if (result != null && result.getData() != null && !result.getData().isEmpty()) {
                Map<String, Object> data = result.getData();
                
                // Determine response type based on data structure
                if (data.containsKey("data") && data.get("data") instanceof List) {
                    // Multiple rows - use TABLE
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("data");
                    
                    response.put("type", "TABLE");
                    response.put("title", "Results 📊");
                    
                    if (!rows.isEmpty()) {
                        List<String> columns = new ArrayList<>(rows.get(0).keySet());
                        List<List<String>> tableRows = rows.stream()
                                .map(row -> columns.stream()
                                        .map(col -> String.valueOf(row.getOrDefault(col, "")))
                                        .toList())
                                .toList();
                        
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("columns", columns);
                        payload.put("rows", tableRows);
                        response.put("payload", payload);
                    } else {
                        response.put("type", "TEXT");
                        response.put("payload", Map.of("message", "No data found for your query."));
                    }
                } else {
                    // Key-value pairs - use KV
                    response.put("type", "KV");
                    response.put("title", getScenarioTitle(scenarioCode));
                    response.put("payload", data);
                }
                
                response.put("footer", "Data retrieved successfully");
                response.put("suggestedFollowUps", getDefaultFollowUps(scenarioCode));
            } else if (result != null && result.getErrorMessage() != null) {
                // Error response
                response.put("type", "ERROR");
                response.put("payload", Map.of(
                        "message", result.getErrorMessage(),
                        "suggestions", List.of("Try again", "Check your input", "Contact support")
                ));
            } else {
                // Success with no data
                response.put("type", "TEXT");
                response.put("payload", Map.of("message", "Your request has been processed successfully."));
                response.put("suggestedFollowUps", getDefaultFollowUps(scenarioCode));
            }
            
            response.put("confidence", 1.0);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error creating fallback response: {}", e.getMessage());
            // Ultimate fallback - minimal TEXT response
            return "{\"type\":\"TEXT\",\"payload\":{\"message\":\"Your request has been processed.\"},\"suggestedFollowUps\":[\"Check account balance\",\"View transactions\"]}";
        }
    }
    
    /**
     * Get a user-friendly title for a scenario.
     */
    private String getScenarioTitle(String scenarioCode) {
        return switch (scenarioCode.toUpperCase()) {
            case "ACCOUNT_BALANCE" -> "Account Balance 💰";
            case "TRANSACTION_HISTORY" -> "Transaction History 📊";
            case "ACCOUNT_SUMMARY" -> "Account Summary 📋";
            case "FUND_TRANSFER" -> "Transfer Details 💸";
            case "CARD_DETAILS" -> "Card Information 💳";
            case "LOAN_STATUS" -> "Loan Status 🏦";
            case "SPENDING_ANALYSIS" -> "Spending Analysis 📈";
            case "INVESTMENT_PORTFOLIO" -> "Investment Portfolio 📂";
            default -> scenarioCode.replace("_", " ");
        };
    }
    
    /**
     * Get default follow-up suggestions for a scenario.
     */
    private List<String> getDefaultFollowUps(String scenarioCode) {
        return switch (scenarioCode.toUpperCase()) {
            case "ACCOUNT_BALANCE" -> List.of("Show recent transactions", "Transfer funds", "View spending breakdown");
            case "TRANSACTION_HISTORY" -> List.of("Filter by date", "Check balance", "Download statement");
            case "ACCOUNT_SUMMARY" -> List.of("View transactions", "Check card details", "Transfer funds");
            case "FUND_TRANSFER" -> List.of("Check transfer status", "View account balance", "Another transfer");
            default -> List.of("Check account balance", "View transactions", "Help");
        };
    }
}

