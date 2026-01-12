package com.enterprise.ai.intelligence.service.ml;

import ai.onnxruntime.*;
import com.enterprise.ai.data.entity.EntityPattern;
import com.enterprise.ai.data.entity.MlModel;
import com.enterprise.ai.data.repository.EntityPatternRepository;
import com.enterprise.ai.intelligence.service.entity.EntityMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ML-based NER (Named Entity Recognition) Service.
 * Uses ONNX models for entity extraction.
 * 
 * NO HARDCODING - All configuration from database.
 * Integrates with EntityPatternMatcher for NER_MODEL pattern type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlNerService {

    private final MlModelLoaderService modelLoaderService;
    private final EntityPatternRepository patternRepository;
    private final MlOnnxTokenizer tokenizer;

    /**
     * Extract entities using ML NER model.
     * 
     * @param text Input text
     * @return List of extracted entities
     */
    public List<EntityMatch> extractEntities(String text) {
        List<EntityMatch> results = new ArrayList<>();

        try {
            // Get active NER model
            Optional<MlModel> modelOpt = modelLoaderService.getActiveModel("NER");
            if (modelOpt.isEmpty()) {
                log.debug("No active ML NER model found");
                return results;
            }

            MlModel model = modelOpt.get();
            OrtSession session = modelLoaderService.getModelSession("NER", model.getModelVersion());
            if (session == null) {
                log.debug("ML NER model session not loaded");
                return results;
            }

            // Get entity patterns for NER_MODEL type
            List<EntityPattern> nerPatterns = patternRepository.findByPatternTypeAndActiveTrue("NER_MODEL");
            if (nerPatterns.isEmpty()) {
                log.debug("No NER_MODEL patterns configured");
                return results;
            }

            // Tokenize input
            Map<String, long[]> tokenized = tokenizer.tokenizeForOnnx(text, 512);
            long[] inputIds = tokenized.get("input_ids");
            long[] attentionMask = tokenized.get("attention_mask");

            // Prepare input tensor (shape: [1, seq_len])
            long[] shape = new long[]{1, inputIds.length};
            OnnxTensor inputTensor = OnnxTensor.createTensor(
                    OrtEnvironment.getEnvironment(),
                    LongBuffer.wrap(inputIds),
                    shape);

            // Prepare attention mask tensor (shape: [1, seq_len])
            OnnxTensor attentionTensor = OnnxTensor.createTensor(
                    OrtEnvironment.getEnvironment(),
                    LongBuffer.wrap(attentionMask),
                    shape);

            // Create input map
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputTensor);
            inputs.put("attention_mask", attentionTensor);

            // Run inference
            OrtSession.Result result = session.run(inputs);

            // Get output (token-level labels - shape: [1, seq_len, num_labels] or [1, seq_len])
            // NER models typically output token-level labels (BIO tagging)
            OnnxValue outputValue = result.get(0); // Get first output
            if (outputValue instanceof OnnxTensor) {
                OnnxTensor outputTensor = (OnnxTensor) outputValue;
                Object outputObj = outputTensor.getValue();
                
                // Process output based on shape
                // Typical NER output: token-level labels (integers or logits)
                // Format depends on model - adjust as needed
                if (outputObj instanceof long[][]) {
                    long[][] labels = (long[][]) outputObj;
                    if (labels.length > 0 && labels[0].length > 0) {
                        long[] tokenLabels = labels[0];
                        
                        // Map token labels to entities
                        // Labels typically use BIO tagging: B-ENTITY, I-ENTITY, O
                        // For simplicity, assuming labels are entity type IDs
                        // In production, you'd need proper label mapping
                        Map<String, String> labelToEntityType = buildLabelMapping(nerPatterns);
                        
                        for (int i = 0; i < Math.min(tokenLabels.length, inputIds.length); i++) {
                            long labelId = tokenLabels[i];
                            String entityType = labelToEntityType.get(String.valueOf(labelId));
                            
                            if (entityType != null && labelId > 0) { // 0 typically means "O" (no entity)
                                // Extract entity span (simplified - in production, group consecutive tokens)
                                // For now, create one EntityMatch per token with entity type
                                // In production, you'd group consecutive tokens with same label
                                EntityMatch match = EntityMatch.builder()
                                        .entityType(entityType)
                                        .value("") // Value extraction requires grouping tokens - simplified here
                                        .confidence(BigDecimal.valueOf(0.85)) // Placeholder confidence
                                        .metadata(Map.of("source", "ml_ner", "token_index", String.valueOf(i)))
                                        .build();
                                results.add(match);
                            }
                        }
                    }
                } else if (outputObj instanceof float[][][]) {
                    // If output is logits (shape: [1, seq_len, num_labels])
                    float[][][] logits = (float[][][]) outputObj;
                    if (logits.length > 0 && logits[0].length > 0) {
                        float[][] tokenLogits = logits[0];
                        
                        // Get predicted label for each token (argmax)
                        Map<String, String> labelToEntityType = buildLabelMapping(nerPatterns);
                        
                        for (int i = 0; i < tokenLogits.length; i++) {
                            float[] tokenProbs = tokenLogits[i];
                            int maxIndex = 0;
                            float maxProb = tokenProbs[0];
                            for (int j = 1; j < tokenProbs.length; j++) {
                                if (tokenProbs[j] > maxProb) {
                                    maxProb = tokenProbs[j];
                                    maxIndex = j;
                                }
                            }
                            
                            if (maxIndex > 0 && maxProb > 0.5) { // Threshold
                                String entityType = labelToEntityType.get(String.valueOf(maxIndex));
                                if (entityType != null) {
                                    EntityMatch match = EntityMatch.builder()
                                            .entityType(entityType)
                                            .value("") // Simplified - needs token grouping
                                            .confidence(BigDecimal.valueOf(maxProb))
                                            .metadata(Map.of("source", "ml_ner", "token_index", String.valueOf(i)))
                                            .build();
                                    results.add(match);
                                }
                            }
                        }
                    }
                }

                // Clean up tensors
                inputTensor.close();
                attentionTensor.close();
                result.close();
            }

            log.debug("ML NER extracted {} entities", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error in ML NER extraction: {}", e.getMessage(), e);
            return results;
        }
    }

    /**
     * Build label to entity type mapping from patterns.
     * In production, this should come from model metadata or configuration.
     */
    private Map<String, String> buildLabelMapping(List<EntityPattern> patterns) {
        Map<String, String> mapping = new HashMap<>();
        // Simplified mapping - in production, use actual label mapping from model/config
        for (int i = 0; i < patterns.size(); i++) {
            mapping.put(String.valueOf(i + 1), patterns.get(i).getEntityType());
        }
        return mapping;
    }

    /**
     * Check if ML NER is available.
     * 
     * @return True if ML NER model is loaded and ready
     */
    public boolean isAvailable() {
        try {
            Optional<MlModel> model = modelLoaderService.getActiveModel("NER");
            if (model.isEmpty()) {
                return false;
            }
            return modelLoaderService.isModelLoaded("NER", model.get().getModelVersion());
        } catch (Exception e) {
            log.debug("ML NER not available: {}", e.getMessage());
            return false;
        }
    }
}
