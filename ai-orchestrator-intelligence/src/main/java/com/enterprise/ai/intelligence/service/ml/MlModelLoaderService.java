package com.enterprise.ai.intelligence.service.ml;

import com.enterprise.ai.data.entity.MlModel;
import com.enterprise.ai.data.repository.MlModelRepository;
import ai.onnxruntime.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading and managing ML models (ONNX Runtime).
 * Loads models from database configuration and caches them in memory.
 * 
 * NO HARDCODING - All model configuration from database.
 * Supports ONNX models for intent classification and NER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlModelLoaderService {

    private final MlModelRepository modelRepository;
    private static OrtEnvironment ortEnvironment;

    /**
     * Cache of loaded ONNX sessions (model type + version -> OrtSession).
     */
    private final Map<String, OrtSession> modelCache = new ConcurrentHashMap<>();

    static {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment();
            log.info("ONNX Runtime environment initialized");
        } catch (Exception e) {
            log.error("Failed to initialize ONNX Runtime environment: {}", e.getMessage(), e);
            ortEnvironment = null;
        }
    }

    @PostConstruct
    public void init() {
        log.info("MlModelLoaderService initialized");
        // Load active models on startup
        if (ortEnvironment != null) {
            loadActiveModels();
        } else {
            log.warn("ONNX Runtime environment not available - model loading disabled");
        }
    }

    @PreDestroy
    public void cleanup() {
        // Close all model sessions
        log.info("Closing {} ML model sessions", modelCache.size());
        for (Map.Entry<String, OrtSession> entry : modelCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.error("Error closing model session {}: {}", entry.getKey(), e.getMessage());
            }
        }
        modelCache.clear();
    }

    /**
     * Load all active ML models.
     */
    private void loadActiveModels() {
        try {
            List<MlModel> activeModels = modelRepository.findByActiveTrueAndEnabledTrue();
            log.info("Found {} active ML models to load", activeModels.size());

            for (MlModel model : activeModels) {
                try {
                    loadModel(model);
                    log.info("Loaded ML model: type={}, name={}, version={}", 
                            model.getModelType(), model.getModelName(), model.getModelVersion());
                } catch (Exception e) {
                    log.error("Failed to load ML model: type={}, name={}, version={}, error={}", 
                            model.getModelType(), model.getModelName(), model.getModelVersion(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load active ML models: {}", e.getMessage(), e);
        }
    }

    /**
     * Load an ML model.
     * 
     * @param model Model configuration from database
     * @return Loaded model session (OrtSession)
     */
    public OrtSession loadModel(MlModel model) {
        String cacheKey = model.getModelType() + ":" + model.getModelVersion();
        
        // Check cache first
        if (modelCache.containsKey(cacheKey)) {
            log.debug("Model already loaded: {}", cacheKey);
            return modelCache.get(cacheKey);
        }

        if (ortEnvironment == null) {
            throw new IllegalStateException("ONNX Runtime environment not initialized");
        }

        try {
            // Validate model path
            String modelPath = model.getModelPath();
            if (modelPath == null || modelPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Model path is required");
            }

            // Check if model file exists
            Path path = Paths.get(modelPath);
            if (!Files.exists(path)) {
                throw new IOException("Model file not found: " + modelPath);
            }

            // Load ONNX model using ONNX Runtime
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            
            // Configure session options based on model config
            // Set optimization level
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            // Set number of threads (use available processors)
            int numThreads = Runtime.getRuntime().availableProcessors();
            sessionOptions.setIntraOpNumThreads(numThreads);
            sessionOptions.setInterOpNumThreads(numThreads);

            // Create session
            OrtSession session = ortEnvironment.createSession(modelPath, sessionOptions);
            modelCache.put(cacheKey, session);
            
            log.info("Loaded ONNX model: {} (inputs: {}, outputs: {})", 
                    cacheKey, session.getInputNames().size(), session.getOutputNames().size());
            return session;

        } catch (Exception e) {
            log.error("Failed to load ONNX model {}: {}", cacheKey, e.getMessage(), e);
            throw new RuntimeException("Failed to load ONNX model: " + cacheKey, e);
        }
    }

    /**
     * Get loaded model session.
     * 
     * @param modelType Model type (INTENT, NER, etc.)
     * @param modelVersion Model version
     * @return Model session or null if not loaded
     */
    public OrtSession getModelSession(String modelType, String modelVersion) {
        String cacheKey = modelType + ":" + modelVersion;
        return modelCache.get(cacheKey);
    }

    /**
     * Check if a model is loaded.
     * 
     * @param modelType Model type
     * @param modelVersion Model version
     * @return True if model is loaded
     */
    public boolean isModelLoaded(String modelType, String modelVersion) {
        String cacheKey = modelType + ":" + modelVersion;
        return modelCache.containsKey(cacheKey);
    }

    /**
     * Get default active model for a type.
     * 
     * @param modelType Model type (INTENT, NER, etc.)
     * @return Model configuration or empty
     */
    public java.util.Optional<MlModel> getActiveModel(String modelType) {
        List<MlModel> models = modelRepository.findByModelTypeAndActiveTrueAndEnabledTrue(modelType);
        return models.stream().findFirst();
    }

    /**
     * Reload a model (for model updates).
     * 
     * @param modelType Model type
     * @param modelVersion Model version
     */
    public void reloadModel(String modelType, String modelVersion) {
        String cacheKey = modelType + ":" + modelVersion;
        OrtSession oldSession = modelCache.remove(cacheKey);
        if (oldSession != null) {
            try {
                oldSession.close();
            } catch (Exception e) {
                log.error("Error closing old model session: {}", e.getMessage());
            }
        }
        
        modelRepository.findByModelTypeAndModelVersion(modelType, modelVersion)
                .ifPresent(this::loadModel);
    }
}
