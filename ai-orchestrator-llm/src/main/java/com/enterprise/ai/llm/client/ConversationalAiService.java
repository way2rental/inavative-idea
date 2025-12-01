package com.enterprise.ai.llm.client;

import com.enterprise.ai.llm.prompt.DynamicPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Conversational AI Service for handling unknown/unclear user requests.
 * Uses GPT to engage in natural conversation and understand user needs.
 */
@Slf4j
@Service
public class ConversationalAiService {

    private final ChatClient chatClient;
    private final DynamicPromptBuilder promptBuilder;

    public ConversationalAiService(ChatClient chatClient, DynamicPromptBuilder promptBuilder) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Generate a friendly, conversational response for unknown intent.
     * The AI will ask clarifying questions and suggest relevant banking actions.
     */
    public Mono<String> generateConversationalResponse(String userQuery, String userId) {
        return Mono.fromCallable(() -> {
            log.debug("Generating conversational response for query: {}", userQuery);

            String prompt = buildConversationalPrompt(userQuery, userId);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.trim().isEmpty()) {
                log.warn("LLM returned empty conversational response, using fallback");
                return getDefaultConversationalResponse(userQuery);
            }

            log.debug("Generated conversational response (length: {})", response.length());
            return response.trim();
        }).onErrorResume(e -> {
            log.error("Error generating conversational response: {}", e.getMessage(), e);
            return Mono.just(getDefaultConversationalResponse(userQuery));
        });
    }

    /**
     * Build a conversational prompt that guides the AI to be helpful and friendly.
     */
    private String buildConversationalPrompt(String userQuery, String userId) {
        // Build a minimal intent detection prompt to get scenario list, then extract scenarios
        String fullPrompt = promptBuilder.buildIntentDetectionPrompt("", "");

        // Extract the scenario list section from the prompt
        String scenarioContext = "";
        if (fullPrompt.contains("Available scenarios and their descriptions:")) {
            int start = fullPrompt.indexOf("Available scenarios and their descriptions:") + 44;
            int end = fullPrompt.indexOf("\nSession context:");
            if (end > start) {
                scenarioContext = fullPrompt.substring(start, end).trim();
            }
        }

        return String.format("""
                You are Aha - an intelligent, friendly AI assistant for Axis Bank Corporate Banking.
                
                A user said: "%s"
                
                You couldn't understand exactly what they want. Your job is to:
                1. Acknowledge their message politely
                2. Ask a clarifying question to understand their need
                3. Suggest 3-4 relevant banking actions they might be looking for
                
                Available Banking Services:
                %s
                
                Guidelines:
                - Be warm, professional, and conversational
                - Use emojis appropriately (banking context)
                - Keep response concise (2-3 sentences max)
                - Suggest actions that match their probable intent
                - Use natural language, avoid technical jargon
                
                Example response format:
                "I'd be happy to help! 👋 It looks like you're interested in [probable intent].
                Are you looking to:
                • Check your account balance
                • View recent transactions
                • Review loan status
                • Something else?
                
                Just let me know what you'd like to do!"
                
                Now generate a friendly, conversational response:
                """, userQuery, scenarioContext);
    }

    /**
     * Fallback response if AI fails to generate conversational response.
     */
    private String getDefaultConversationalResponse(String userQuery) {
        return """
                I'd be happy to help! 👋
                
                I couldn't quite understand your request. Could you tell me what you're looking for?
                
                Here are some common actions:
                • 💰 Check account balance
                • 📜 View transaction history
                • 📊 Get account summary
                • 💳 View card details
                • 🏦 Check loan status
                • 📈 Analyze spending
                
                What would you like to do?
                """;
    }
}

