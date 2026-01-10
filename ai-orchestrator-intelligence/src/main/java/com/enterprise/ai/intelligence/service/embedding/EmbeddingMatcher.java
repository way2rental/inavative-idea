package com.enterprise.ai.intelligence.service.embedding;

import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.entity.ScenarioEmbedding;
import com.enterprise.ai.data.repository.ScenarioEmbeddingRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Embedding-based intent detection service.
 * Uses pre-computed scenario embeddings for fast similarity search.
 * 
 * NO HARDCODING - All configuration from database.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingMatcher implements FallbackLayerService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingMatcher.class);

    private final ScenarioEmbeddingRepository embeddingRepository;
    private final ConfigCacheService configCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private EmbeddingModel embeddingModel;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private Map<String, String> scenarioCodeMap = new HashMap<>(); // embedding ID -> scenario code
    
    @PostConstruct
    public void init() {
        try {
            // Initialize embedding model
            embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            embeddingStore = new InMemoryEmbeddingStore<>();
            
            // Load embeddings from database
            loadEmbeddings();
            
            log.info("EmbeddingMatcher initialized with {} scenario embeddings", scenarioCodeMap.size());
            
            // If no embeddings found, log warning
            if (scenarioCodeMap.isEmpty()) {
                log.warn("No embeddings found in database. Use EmbeddingGenerationService to generate embeddings for scenarios.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize EmbeddingMatcher: {}", e.getMessage(), e);
        }
    }
    
    private void loadEmbeddings() {
        List<ScenarioEmbedding> embeddings = embeddingRepository.findByActiveTrue();
        
        for (ScenarioEmbedding embedding : embeddings) {
            try {
                // Parse embedding vector
                List<Double> vector = objectMapper.readValue(
                    embedding.getEmbeddingVector(),
                    new TypeReference<List<Double>>() {}
                );
                
                // Convert to float array
                float[] floatVector = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    floatVector[i] = vector.get(i).floatValue();
                }
                
                // Create text segment (scenario code as content)
                TextSegment segment = TextSegment.from(embedding.getScenarioCode());
                
                // Store embedding (correct order: Embedding first, then TextSegment)
                dev.langchain4j.data.embedding.Embedding embeddingVector = dev.langchain4j.data.embedding.Embedding.from(floatVector);
                String id = embeddingStore.add(embeddingVector, segment);
                scenarioCodeMap.put(id, embedding.getScenarioCode());
                
            } catch (Exception e) {
                log.warn("Failed to load embedding for scenario {}: {}", embedding.getScenarioCode(), e.getMessage());
            }
        }
    }

    @Override
    public String getLayerCode() {
        return "EMBEDDING_SIMILARITY";
    }

    @Override
    public boolean isEnabled() {
        return embeddingModel != null && embeddingStore != null && !scenarioCodeMap.isEmpty();
    }

    @Override
    public Mono<Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig) {
        
        if (!isEnabled()) {
            return Mono.just(Optional.empty());
        }
        
        return Mono.<Optional<IntentResult>>fromCallable(() -> {
            try {
                // Generate embedding for user query
                dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(userInput).content();
                
                // Search for similar scenarios
                int topK = 5; // Get top 5 matches
                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .minScore(layerConfig.getConfidenceThreshold())
                        .build()
                );
                
                if (searchResult.matches().isEmpty()) {
                    log.debug("No embedding matches found for query: {}", userInput);
                    return Optional.empty();
                }
                
                // Get best match
                EmbeddingMatch<TextSegment> bestMatch = searchResult.matches().get(0);
                String scenarioCode = scenarioCodeMap.get(bestMatch.embeddingId());
                double similarity = bestMatch.score();
                
                // Check if scenario is allowed (RBAC)
                if (allowedScenarios != null && !allowedScenarios.isEmpty()) {
                    if (!allowedScenarios.contains(scenarioCode)) {
                        log.debug("Scenario {} not allowed for user, trying next match", scenarioCode);
                        // Try next match
                        if (searchResult.matches().size() > 1) {
                            bestMatch = searchResult.matches().get(1);
                            scenarioCode = scenarioCodeMap.get(bestMatch.embeddingId());
                            similarity = bestMatch.score();
                            if (!allowedScenarios.contains(scenarioCode)) {
                                return Optional.empty();
                            }
                        } else {
                            return Optional.empty();
                        }
                    }
                }
                
                // Check confidence threshold
                if (similarity < layerConfig.getConfidenceThreshold()) {
                    log.debug("Similarity {} below threshold {}", similarity, layerConfig.getConfidenceThreshold());
                    return Optional.empty();
                }
                
                // Check if multiple high-similarity matches (ambiguous)
                if (searchResult.matches().size() > 1) {
                    EmbeddingMatch<TextSegment> secondMatch = searchResult.matches().get(1);
                    double secondSimilarity = secondMatch.score();
                    
                    // If second match is close to first, consider ambiguous
                    if (secondSimilarity > similarity * 0.9) { // Within 10% of best match
                        List<String> possibleScenarios = searchResult.matches().stream()
                            .limit(3)
                            .map(m -> scenarioCodeMap.get(m.embeddingId()))
                            .filter(s -> allowedScenarios == null || allowedScenarios.contains(s))
                            .collect(Collectors.toList());
                        
                        return Optional.of(IntentResult.builder()
                            .scenario("AMBIGUOUS")
                            .confidence(similarity * 0.9) // Slightly lower for ambiguity
                            .possibleScenarios(possibleScenarios)
                            .reasoning("Multiple similar scenarios found via embedding similarity")
                            .build());
                    }
                }
                
                // Single clear match
                return Optional.of(IntentResult.builder()
                    .scenario(scenarioCode)
                    .confidence(similarity)
                    .params(extractParams(userInput, scenarioCode))
                    .reasoning("Matched via embedding similarity (score: " + String.format("%.2f", similarity) + ")")
                    .build());
                
            } catch (Exception e) {
                log.error("Embedding matching failed: {}", e.getMessage(), e);
                return Optional.empty();
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .timeout(java.time.Duration.ofMillis(layerConfig.getTimeoutMs()))
        .onErrorReturn(Optional.empty());
    }

    /**
     * Extract parameters from user input using Entity Extraction Service.
     */
    private Map<String, Object> extractParams(String userInput, String scenarioCode) {
        Map<String, Object> params = new HashMap<>();
        
        // Note: Entity extraction is async, so for embedding layer we keep it simple
        // Full parameter extraction happens in ParameterExtractionService
        // This is just a placeholder for basic extraction
        
        return params;
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.just(isEnabled());
    }
    
    /**
     * Refresh embeddings from database
     */
    public void refreshEmbeddings() {
        log.info("Refreshing embeddings from database...");
        embeddingStore = new InMemoryEmbeddingStore<>();
        scenarioCodeMap.clear();
        loadEmbeddings();
        log.info("Embeddings refreshed");
    }
}
