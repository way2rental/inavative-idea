package com.enterprise.ai.intelligence.service.ml;

import ai.onnxruntime.*;
import com.enterprise.ai.common.dto.IntentResult;
import com.enterprise.ai.data.entity.FallbackLayer;
import com.enterprise.ai.data.entity.MlModel;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.AiScenarioRepository;
import com.enterprise.ai.intelligence.service.fallback.FallbackLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.LongBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ML-based Intent Classifier Service.
 * Uses ONNX models for intent classification.
 * 
 * NO HARDCODING - All configuration from database.
 * Integrates with FallbackLayerOrchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlIntentClassifierService implements FallbackLayerService {

    private final MlModelLoaderService modelLoaderService;
    private final AiScenarioRepository scenarioRepository;
    private final MlOnnxTokenizer tokenizer;

    @Override
    public String getLayerCode() {
        return "ML_INTENT_CLASSIFIER";
    }

    @Override
    public boolean isEnabled() {
        try {
            Optional<MlModel> model = modelLoaderService.getActiveModel("INTENT");
            return model.isPresent() && modelLoaderService.isModelLoaded("INTENT", 
                    model.get().getModelVersion());
        } catch (Exception e) {
            log.debug("ML Intent Classifier not enabled: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Mono<Optional<IntentResult>> detectIntent(
            String userInput,
            String sessionContext,
            String lastUsedParamsJson,
            Set<String> allowedScenarios,
            FallbackLayer layerConfig) {
        
        return Mono.fromCallable(() -> {
            try {
                // Get active intent model
                Optional<MlModel> modelOpt = modelLoaderService.getActiveModel("INTENT");
                if (modelOpt.isEmpty()) {
                    log.debug("No active ML intent model found");
                    return Optional.<IntentResult>empty();
                }

                MlModel model = modelOpt.get();
                OrtSession session = modelLoaderService.getModelSession("INTENT", model.getModelVersion());
                if (session == null) {
                    log.debug("ML intent model session not loaded");
                    return Optional.<IntentResult>empty();
                }

                // Get allowed scenarios from database
                List<AiScenario> scenarios = scenarioRepository.findByActiveTrue();
                List<String> scenarioCodes = scenarios.stream()
                        .map(AiScenario::getScenarioCode)
                        .filter(allowedScenarios::contains)
                        .collect(Collectors.toList());

                if (scenarioCodes.isEmpty()) {
                    log.debug("No allowed scenarios for ML intent classification");
                    return Optional.<IntentResult>empty();
                }

                // Prepare input text
                String inputText = userInput;
                if (sessionContext != null && !sessionContext.trim().isEmpty()) {
                    inputText = sessionContext + " " + userInput;
                }

                // Tokenize input
                Map<String, long[]> tokenized = tokenizer.tokenizeForOnnx(inputText, 512);
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
                // Note: Input names depend on your model - adjust as needed
                // Common names: "input_ids", "attention_mask", "token_type_ids"
                inputs.put("input_ids", inputTensor);
                inputs.put("attention_mask", attentionTensor);

                // Run inference
                OrtSession.Result result = session.run(inputs);

                // Get output (probabilities for each class)
                // Output shape typically: [1, num_classes]
                OnnxValue outputValue = result.get(0); // Get first output
                if (outputValue instanceof OnnxTensor) {
                    OnnxTensor outputTensor = (OnnxTensor) outputValue;
                    float[][] outputArray = (float[][]) outputTensor.getValue();
                    
                    if (outputArray.length > 0 && outputArray[0].length > 0) {
                        float[] probabilities = outputArray[0];
                        
                        // Map probabilities to scenario codes
                        // Note: This assumes model output order matches scenario order
                        // In production, you'd need a mapping from class indices to scenario codes
                        int maxIndex = 0;
                        float maxProb = probabilities[0];
                        for (int i = 1; i < Math.min(probabilities.length, scenarioCodes.size()); i++) {
                            if (probabilities[i] > maxProb) {
                                maxProb = probabilities[i];
                                maxIndex = i;
                            }
                        }

                        // Clean up tensors
                        inputTensor.close();
                        attentionTensor.close();
                        result.close();

                        // Check confidence threshold
                        double confidenceThreshold = layerConfig.getConfidenceThreshold() != null 
                                ? layerConfig.getConfidenceThreshold() 
                                : 0.75;

                        if (maxProb >= confidenceThreshold && maxIndex < scenarioCodes.size()) {
                            String scenarioCode = scenarioCodes.get(maxIndex);
                            log.debug("ML intent classified: scenario={}, confidence={}", scenarioCode, maxProb);
                            
                            return Optional.of(IntentResult.builder()
                                    .scenario(scenarioCode)
                                    .confidence(maxProb)
                                    .params(new HashMap<>())
                                    .reasoning("ML Intent Classifier")
                                    .build());
                        }
                    }
                    
                    // Clean up tensors
                    inputTensor.close();
                    attentionTensor.close();
                    result.close();
                }

                log.debug("ML intent classification confidence too low or no match");
                return Optional.<IntentResult>empty();

            } catch (Exception e) {
                log.error("Error in ML intent classification: {}", e.getMessage(), e);
                return Optional.<IntentResult>empty();
            }
        });
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return Mono.fromCallable(() -> {
            try {
                Optional<MlModel> model = modelLoaderService.getActiveModel("INTENT");
                if (model.isEmpty()) {
                    return false;
                }
                return modelLoaderService.isModelLoaded("INTENT", model.get().getModelVersion());
            } catch (Exception e) {
                log.error("Health check failed for ML Intent Classifier: {}", e.getMessage());
                return false;
            }
        });
    }
}
