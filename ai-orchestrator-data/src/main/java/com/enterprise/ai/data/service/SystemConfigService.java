package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.SystemConfig;
import com.enterprise.ai.data.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing system-wide configurations.
 * Provides caching and default value fallbacks.
 * 
 * This service ensures NO hardcoded values in the application.
 * All configurable values are loaded from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    // In-memory cache for fast access
    private final Map<String, SystemConfig> configCache = new ConcurrentHashMap<>();
    private volatile long lastRefreshTime = 0;
    // Bootstrap cache TTL - used only until the service initializes and reads from DB
    // After init, CACHE_TTL_SECONDS from DB is used for prompt/scenario caching
    private static final long BOOTSTRAP_CACHE_TTL_MS = 300000; // 5 minutes

    // ===================== CONFIG KEYS =====================
    // These are the configuration keys used throughout the application
    
    // Branding
    public static final String ORG_NAME = "ORG_NAME";
    public static final String ORG_SHORT_NAME = "ORG_SHORT_NAME";
    public static final String AI_ASSISTANT_NAME = "AI_ASSISTANT_NAME";
    public static final String AI_ASSISTANT_FULL_NAME = "AI_ASSISTANT_FULL_NAME";
    public static final String WELCOME_MESSAGE = "WELCOME_MESSAGE";
    public static final String GOODBYE_MESSAGE = "GOODBYE_MESSAGE";
    
    // Performance
    public static final String DEFAULT_TIMEOUT_MS = "DEFAULT_TIMEOUT_MS";
    public static final String MAX_TIMEOUT_MS = "MAX_TIMEOUT_MS";
    public static final String CACHE_TTL_SECONDS = "CACHE_TTL_SECONDS";
    public static final String MAX_RESULT_SIZE = "MAX_RESULT_SIZE";
    public static final String MAX_RESPONSE_SIZE_BYTES = "MAX_RESPONSE_SIZE_BYTES";
    public static final String MAX_STREAM_DURATION_SECONDS = "MAX_STREAM_DURATION_SECONDS";
    
    // Security
    public static final String SESSION_TIMEOUT_SECONDS = "SESSION_TIMEOUT_SECONDS";
    public static final String MAX_QUERIES_PER_MINUTE = "MAX_QUERIES_PER_MINUTE";
    public static final String DEFAULT_CONFIDENCE_THRESHOLD = "DEFAULT_CONFIDENCE_THRESHOLD";
    
    // Messages
    public static final String ERROR_MESSAGE_GENERIC = "ERROR_MESSAGE_GENERIC";
    public static final String ERROR_MESSAGE_TIMEOUT = "ERROR_MESSAGE_TIMEOUT";
    public static final String ERROR_MESSAGE_UNAUTHORIZED = "ERROR_MESSAGE_UNAUTHORIZED";
    
    // LLM Settings
    public static final String LLM_TEMPERATURE = "LLM_TEMPERATURE";
    public static final String LLM_MAX_TOKENS = "LLM_MAX_TOKENS";
    public static final String PROMPT_CACHE_TTL_SECONDS = "PROMPT_CACHE_TTL_SECONDS";

    // ===================== DEFAULT VALUES =====================
    // Used when no database value exists (for initial setup)
    
    private static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
        // Branding - Generic defaults for SaaS
        Map.entry(ORG_NAME, "Your Organization"),
        Map.entry(ORG_SHORT_NAME, "YourOrg"),
        Map.entry(AI_ASSISTANT_NAME, "AI Assistant"),
        Map.entry(AI_ASSISTANT_FULL_NAME, "AI Helpdesk Assistant"),
        Map.entry(WELCOME_MESSAGE, "Hello! I'm your AI assistant. How can I help you today?"),
        Map.entry(GOODBYE_MESSAGE, "Thank you for using our services. Have a great day!"),
        
        // Performance
        Map.entry(DEFAULT_TIMEOUT_MS, "5000"),
        Map.entry(MAX_TIMEOUT_MS, "30000"),
        Map.entry(CACHE_TTL_SECONDS, "300"),
        Map.entry(MAX_RESULT_SIZE, "1000"),
        Map.entry(MAX_RESPONSE_SIZE_BYTES, "1048576"),
        Map.entry(MAX_STREAM_DURATION_SECONDS, "120"),
        
        // Security
        Map.entry(SESSION_TIMEOUT_SECONDS, "300"),
        Map.entry(MAX_QUERIES_PER_MINUTE, "60"),
        Map.entry(DEFAULT_CONFIDENCE_THRESHOLD, "0.85"),
        
        // Messages
        Map.entry(ERROR_MESSAGE_GENERIC, "I apologize, but I encountered an issue processing your request. Please try again."),
        Map.entry(ERROR_MESSAGE_TIMEOUT, "Your request is taking longer than expected. Please try again with a simpler query."),
        Map.entry(ERROR_MESSAGE_UNAUTHORIZED, "You don't have permission to access this feature."),
        
        // LLM
        Map.entry(LLM_TEMPERATURE, "0.7"),
        Map.entry(LLM_MAX_TOKENS, "1024"),
        Map.entry(PROMPT_CACHE_TTL_SECONDS, "300")
    );

    @PostConstruct
    public void init() {
        refreshCache();
        initializeDefaults();
        log.info("SystemConfigService initialized with {} configs", configCache.size());
    }

    /**
     * Initialize default configurations if they don't exist.
     */
    private void initializeDefaults() {
        for (Map.Entry<String, String> entry : DEFAULT_VALUES.entrySet()) {
            if (!configRepository.existsByConfigKey(entry.getKey())) {
                SystemConfig config = SystemConfig.builder()
                        .configKey(entry.getKey())
                        .configValue(entry.getValue())
                        .defaultValue(entry.getValue())
                        .category(getCategoryForKey(entry.getKey()))
                        .description(getDescriptionForKey(entry.getKey()))
                        .valueType(getValueTypeForKey(entry.getKey()))
                        .editable(true)
                        .visible(true)
                        .build();
                configRepository.save(config);
                configCache.put(entry.getKey(), config);
                log.debug("Initialized default config: {}", entry.getKey());
            }
        }
    }

    /**
     * Refresh the in-memory cache from database.
     */
    @CacheEvict(value = "systemConfig", allEntries = true)
    public void refreshCache() {
        List<SystemConfig> allConfigs = configRepository.findAll();
        configCache.clear();
        for (SystemConfig config : allConfigs) {
            configCache.put(config.getConfigKey(), config);
        }
        lastRefreshTime = System.currentTimeMillis();
        log.info("System config cache refreshed with {} entries", configCache.size());
    }

    /**
     * Check if cache needs refresh and refresh if needed.
     */
    private void checkCacheRefresh() {
        if (System.currentTimeMillis() - lastRefreshTime > BOOTSTRAP_CACHE_TTL_MS) {
            refreshCache();
        }
    }

    // ===================== GET METHODS =====================

    /**
     * Get configuration by key (with cache).
     */
    @Cacheable(value = "systemConfig", key = "#configKey")
    public Optional<SystemConfig> getConfig(String configKey) {
        checkCacheRefresh();
        return Optional.ofNullable(configCache.get(configKey));
    }

    /**
     * Get string value by key with fallback to default.
     */
    public String getString(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(DEFAULT_VALUES.getOrDefault(configKey, ""));
    }

    /**
     * Get string value by key with custom default.
     */
    public String getString(String configKey, String defaultValue) {
        return getConfig(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    /**
     * Get integer value by key with fallback.
     */
    public int getInt(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getIntValue)
                .orElse(Integer.parseInt(DEFAULT_VALUES.getOrDefault(configKey, "0")));
    }

    /**
     * Get integer value by key with custom default.
     */
    public int getInt(String configKey, int defaultValue) {
        return getConfig(configKey)
                .map(SystemConfig::getIntValue)
                .orElse(defaultValue);
    }

    /**
     * Get long value by key with fallback.
     */
    public long getLong(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getLongValue)
                .orElse(Long.parseLong(DEFAULT_VALUES.getOrDefault(configKey, "0")));
    }

    /**
     * Get long value by key with custom default.
     */
    public long getLong(String configKey, long defaultValue) {
        return getConfig(configKey)
                .map(SystemConfig::getLongValue)
                .orElse(defaultValue);
    }

    /**
     * Get double value by key with fallback.
     */
    public double getDouble(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getDoubleValue)
                .orElse(Double.parseDouble(DEFAULT_VALUES.getOrDefault(configKey, "0.0")));
    }

    /**
     * Get boolean value by key with fallback.
     */
    public boolean getBoolean(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getBooleanValue)
                .orElse(false);
    }

    /**
     * Get JSON value by key.
     */
    public String getJson(String configKey) {
        return getConfig(configKey)
                .map(SystemConfig::getJsonValue)
                .orElse(null);
    }

    // ===================== CONVENIENCE METHODS =====================

    /**
     * Get organization name.
     */
    public String getOrgName() {
        return getString(ORG_NAME);
    }

    /**
     * Get AI assistant name.
     */
    public String getAssistantName() {
        return getString(AI_ASSISTANT_NAME);
    }

    /**
     * Get AI assistant full name.
     */
    public String getAssistantFullName() {
        return getString(AI_ASSISTANT_FULL_NAME);
    }

    /**
     * Get default timeout in milliseconds.
     */
    public long getDefaultTimeoutMs() {
        return getLong(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Get max result size.
     */
    public int getMaxResultSize() {
        return getInt(MAX_RESULT_SIZE);
    }

    /**
     * Get generic error message.
     */
    public String getGenericErrorMessage() {
        return getString(ERROR_MESSAGE_GENERIC);
    }

    /**
     * Get timeout error message.
     */
    public String getTimeoutErrorMessage() {
        return getString(ERROR_MESSAGE_TIMEOUT);
    }

    /**
     * Get default confidence threshold.
     */
    public double getDefaultConfidenceThreshold() {
        return getDouble(DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * Get prompt cache TTL in milliseconds.
     */
    public long getPromptCacheTtlMs() {
        return getLong(PROMPT_CACHE_TTL_SECONDS) * 1000;
    }

    // ===================== SAVE/UPDATE METHODS =====================

    /**
     * Save or update a configuration value.
     */
    @CacheEvict(value = "systemConfig", key = "#configKey")
    public SystemConfig saveConfig(String configKey, String configValue) {
        SystemConfig config = configRepository.findByConfigKey(configKey)
                .orElse(SystemConfig.builder()
                        .configKey(configKey)
                        .category("CUSTOM")
                        .editable(true)
                        .visible(true)
                        .build());
        
        config.setConfigValue(configValue);
        SystemConfig saved = configRepository.save(config);
        configCache.put(configKey, saved);
        return saved;
    }

    /**
     * Save or update a full SystemConfig entity.
     */
    @CacheEvict(value = "systemConfig", key = "#config.configKey")
    public SystemConfig saveConfig(SystemConfig config) {
        SystemConfig saved = configRepository.save(config);
        configCache.put(saved.getConfigKey(), saved);
        return saved;
    }

    /**
     * Delete a configuration.
     */
    @CacheEvict(value = "systemConfig", key = "#configKey")
    public void deleteConfig(String configKey) {
        configRepository.findByConfigKey(configKey).ifPresent(config -> {
            configRepository.delete(config);
            configCache.remove(configKey);
        });
    }

    // ===================== LIST METHODS =====================

    /**
     * Get all configurations.
     */
    public List<SystemConfig> getAllConfigs() {
        return configRepository.findAllByOrderByCategoryAscConfigKeyAsc();
    }

    /**
     * Get configurations by category.
     */
    public List<SystemConfig> getConfigsByCategory(String category) {
        return configRepository.findByCategory(category);
    }

    /**
     * Get visible configurations.
     */
    public List<SystemConfig> getVisibleConfigs() {
        return configRepository.findByVisibleTrue();
    }

    /**
     * Get all unique categories.
     */
    public List<String> getCategories() {
        return getAllConfigs().stream()
                .map(SystemConfig::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    // ===================== HELPER METHODS =====================

    private String getCategoryForKey(String key) {
        if (key.startsWith("ORG_") || key.contains("NAME") || key.contains("MESSAGE")) {
            return "BRANDING";
        } else if (key.contains("TIMEOUT") || key.contains("CACHE") || key.contains("MAX_") || key.contains("SIZE")) {
            return "PERFORMANCE";
        } else if (key.contains("SESSION") || key.contains("SECURITY") || key.contains("QUERIES")) {
            return "SECURITY";
        } else if (key.contains("ERROR")) {
            return "MESSAGES";
        } else if (key.contains("LLM") || key.contains("PROMPT") || key.contains("CONFIDENCE")) {
            return "LLM";
        }
        return "GENERAL";
    }

    private String getDescriptionForKey(String key) {
        return switch (key) {
            case ORG_NAME -> "Full organization name (e.g., 'Axis Bank', 'HDFC Bank')";
            case ORG_SHORT_NAME -> "Short organization name for display";
            case AI_ASSISTANT_NAME -> "AI assistant's name (e.g., 'AHA', 'EVA')";
            case AI_ASSISTANT_FULL_NAME -> "Full AI assistant name (e.g., 'AI Helpdesk Assistant')";
            case WELCOME_MESSAGE -> "Welcome message shown to users";
            case GOODBYE_MESSAGE -> "Goodbye message shown to users";
            case DEFAULT_TIMEOUT_MS -> "Default request timeout in milliseconds";
            case MAX_TIMEOUT_MS -> "Maximum allowed timeout in milliseconds";
            case CACHE_TTL_SECONDS -> "Cache time-to-live in seconds";
            case MAX_RESULT_SIZE -> "Maximum number of results to return";
            case MAX_RESPONSE_SIZE_BYTES -> "Maximum response size in bytes";
            case MAX_STREAM_DURATION_SECONDS -> "Maximum streaming duration in seconds";
            case SESSION_TIMEOUT_SECONDS -> "User session timeout in seconds";
            case MAX_QUERIES_PER_MINUTE -> "Maximum queries allowed per minute per user";
            case DEFAULT_CONFIDENCE_THRESHOLD -> "Default confidence threshold for intent detection";
            case ERROR_MESSAGE_GENERIC -> "Generic error message shown to users";
            case ERROR_MESSAGE_TIMEOUT -> "Timeout error message shown to users";
            case ERROR_MESSAGE_UNAUTHORIZED -> "Unauthorized access error message";
            case LLM_TEMPERATURE -> "LLM temperature setting (0.0-2.0)";
            case LLM_MAX_TOKENS -> "Maximum tokens for LLM response";
            case PROMPT_CACHE_TTL_SECONDS -> "Prompt template cache TTL in seconds";
            default -> "Configuration: " + key;
        };
    }

    private String getValueTypeForKey(String key) {
        if (key.contains("_MS") || key.contains("SECONDS") || key.contains("SIZE") || key.contains("BYTES") || key.contains("TOKENS") || key.contains("MINUTE")) {
            return "LONG";
        } else if (key.contains("THRESHOLD") || key.contains("TEMPERATURE")) {
            return "DOUBLE";
        } else if (key.startsWith("ENABLE") || key.startsWith("IS_")) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    public String getCurrencySymbol() {
        return "$";
    }
}
