package com.enterprise.ai.intelligence.service.embedding;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.ScenarioEmbedding;
import com.enterprise.ai.data.repository.ScenarioEmbeddingRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating embeddings for scenarios.
 * Uses all-MiniLM-L6-v2 model to generate 384-dimensional embeddings.
 * 
 * NO HARDCODING - All scenarios from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingGenerationService {

    private final ConfigCacheService configCacheService;
    private final ScenarioEmbeddingRepository embeddingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private EmbeddingModel embeddingModel;
    
    /**
     * Initialize embedding model on first use.
     */
    private EmbeddingModel getEmbeddingModel() {
        if (embeddingModel == null) {
            try {
                embeddingModel = new AllMiniLmL6V2EmbeddingModel();
                log.info("Embedding model initialized: all-MiniLM-L6-v2");
            } catch (Exception e) {
                log.error("Failed to initialize embedding model: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to initialize embedding model", e);
            }
        }
        return embeddingModel;
    }

    /**
     * Generate embedding for a scenario.
     * 
     * @param scenarioCode Scenario code
     * @return Mono of ScenarioEmbedding
     */
    public Mono<ScenarioEmbedding> generateEmbedding(String scenarioCode) {
        return Mono.fromCallable(() -> {
            try {
                // Get scenario configuration
                AiScenario scenario = configCacheService.getScenarioByCode(scenarioCode)
                    .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenarioCode));
                
                // Build text for embedding: scenario name + description + example queries
                StringBuilder textBuilder = new StringBuilder();
                textBuilder.append(scenario.getScenarioName()).append(" ");
                if (scenario.getDescription() != null) {
                    textBuilder.append(scenario.getDescription()).append(" ");
                }
                
                // Add example queries if available
                if (scenario.getExampleQueries() != null && !scenario.getExampleQueries().isEmpty()) {
                    try {
                        List<String> examples = objectMapper.readValue(
                            scenario.getExampleQueries(),
                            new TypeReference<List<String>>() {}
                        );
                        for (String example : examples) {
                            textBuilder.append(example).append(" ");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse example queries for scenario {}: {}", scenarioCode, e.getMessage());
                    }
                }
                
                String textToEmbed = textBuilder.toString().trim();
                
                if (textToEmbed.isEmpty()) {
                    throw new IllegalArgumentException("No text available for embedding scenario: " + scenarioCode);
                }
                
                log.debug("Generating embedding for scenario {}: text length={}", scenarioCode, textToEmbed.length());
                
                // Generate embedding
                EmbeddingModel model = getEmbeddingModel();
                Embedding embedding = model.embed(textToEmbed).content();
                
                // Convert to JSON array
                float[] embeddingArray = embedding.vector();
                List<Double> embeddingList = new ArrayList<>();
                for (float value : embeddingArray) {
                    embeddingList.add((double) value);
                }
                String embeddingJson = objectMapper.writeValueAsString(embeddingList);
                
                // Check if embedding already exists
                var existingOpt = embeddingRepository.findByScenarioCode(scenarioCode);
                
                ScenarioEmbedding scenarioEmbedding;
                if (existingOpt.isPresent()) {
                    // Update existing
                    scenarioEmbedding = existingOpt.get();
                    scenarioEmbedding.setEmbeddingVector(embeddingJson);
                    scenarioEmbedding.setEmbeddingModel("all-MiniLM-L6-v2");
                    scenarioEmbedding.setActive(true);
                } else {
                    // Create new
                    scenarioEmbedding = ScenarioEmbedding.builder()
                        .scenarioCode(scenarioCode)
                        .embeddingVector(embeddingJson)
                        .embeddingModel("all-MiniLM-L6-v2")
                        .active(true)
                        .build();
                }
                
                ScenarioEmbedding saved = embeddingRepository.save(scenarioEmbedding);
                log.info("Generated embedding for scenario {}: dimension={}", scenarioCode, embeddingArray.length);
                
                return saved;
            } catch (Exception e) {
                log.error("Error generating embedding for scenario {}: {}", scenarioCode, e.getMessage(), e);
                throw new RuntimeException("Failed to generate embedding", e);
            }
        });
    }

    /**
     * Generate embeddings for all active scenarios.
     * 
     * @return Mono of number of embeddings generated
     */
    public Mono<Integer> generateAllEmbeddings() {
        return Mono.fromCallable(() -> {
            List<AiScenario> scenarios = configCacheService.getActiveScenarios();
            int count = 0;
            
            log.info("Generating embeddings for {} scenarios...", scenarios.size());
            
            for (AiScenario scenario : scenarios) {
                try {
                    generateEmbedding(scenario.getScenarioCode()).block();
                    count++;
                    log.debug("Generated embedding {}/{}", count, scenarios.size());
                } catch (Exception e) {
                    log.warn("Failed to generate embedding for scenario {}: {}", 
                        scenario.getScenarioCode(), e.getMessage());
                }
            }
            
            log.info("Generated {} embeddings successfully", count);
            return count;
        });
    }

    /**
     * Refresh embedding for a scenario (regenerate).
     * 
     * @param scenarioCode Scenario code
     * @return Mono of ScenarioEmbedding
     */
    public Mono<ScenarioEmbedding> refreshEmbedding(String scenarioCode) {
        return generateEmbedding(scenarioCode);
    }
}
