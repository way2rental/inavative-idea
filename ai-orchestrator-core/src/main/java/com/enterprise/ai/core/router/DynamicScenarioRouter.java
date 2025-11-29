package com.enterprise.ai.core.router;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.enums.ExecutionType;
import com.enterprise.ai.common.exception.ScenarioNotFoundException;
import com.enterprise.ai.core.scenario.DynamicExecutor;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.repository.AiScenarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dynamic scenario router that routes by execution_type, not scenario_code.
 * 
 * Flow:
 * 1. Load AiScenario from DB by scenario_code
 * 2. Get execution_type from scenario
 * 3. Find executor that supports(execution_type)
 * 4. Execute using scenario configuration
 * 
 * This enables:
 * - 150+ scenarios with only 2-4 executor implementations
 * - No code changes when adding new scenarios
 * - DB-driven configuration
 * - Dry-run mode support
 */
@Slf4j
@Service
public class DynamicScenarioRouter {

    private static final long DEFAULT_TIMEOUT_MS = 30000;

    private final Map<String, DynamicExecutor> executorsByType;
    private final AiScenarioRepository scenarioRepository;
    
    // Cache of active scenarios
    private final ConcurrentHashMap<String, AiScenario> scenarioCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> activeScenarios = new ConcurrentHashMap<>();

    public DynamicScenarioRouter(List<DynamicExecutor> executors, AiScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
        
        // Map executors by supported execution type
        this.executorsByType = new ConcurrentHashMap<>();
        for (DynamicExecutor executor : executors) {
            for (ExecutionType type : ExecutionType.values()) {
                if (executor.supports(type.name())) {
                    executorsByType.put(type.name(), executor);
                    log.info("Registered {} for execution type {}", executor.getClass().getSimpleName(), type);
                }
            }
        }
        
        // Initial cache load
        refreshScenarioCache();
        log.info("DynamicScenarioRouter initialized with {} executor types", executorsByType.size());
    }

    /**
     * Route a scenario request to the appropriate dynamic executor.
     * NEVER routes by scenario code directly - always by execution_type.
     */
    public ScenarioResult route(ScenarioRequest request) {
        return route(request, false);
    }

    /**
     * Route with optional dry-run mode.
     */
    public ScenarioResult route(ScenarioRequest request, boolean dryRun) {
        String scenarioCode = request.getScenario();
        
        // Load scenario from DB (or cache)
        AiScenario scenario = getScenario(scenarioCode);
        
        // Check if scenario is active
        if (!isScenarioActive(scenarioCode)) {
            log.warn("Scenario {} is disabled in database", scenarioCode);
            throw new ScenarioNotFoundException("Scenario is disabled: " + scenarioCode);
        }
        
        // Get executor by execution type
        String executionType = scenario.getExecutionType();
        if (executionType == null) {
            executionType = ExecutionType.DB_QUERY.name(); // Default
        }
        
        DynamicExecutor executor = executorsByType.get(executionType);
        if (executor == null) {
            throw new ScenarioNotFoundException(
                    "No executor found for execution type: " + executionType);
        }
        
        log.info("Routing scenario {} to {} executor (execution_type={})", 
                scenarioCode, executor.getClass().getSimpleName(), executionType);
        
        // Execute (or dry-run)
        if (dryRun) {
            return executor.executeDryRun(request, scenario);
        }
        return executor.execute(request, scenario);
    }

    /**
     * Route reactively (non-blocking).
     */
    public Mono<ScenarioResult> routeReactive(ScenarioRequest request) {
        return routeReactive(request, false);
    }

    /**
     * Route reactively with optional dry-run mode.
     */
    public Mono<ScenarioResult> routeReactive(ScenarioRequest request, boolean dryRun) {
        String scenarioCode = request.getScenario();
        
        return Mono.defer(() -> {
            // Load scenario
            AiScenario scenario = getScenario(scenarioCode);
            
            // Check if active
            if (!isScenarioActive(scenarioCode)) {
                return Mono.error(new ScenarioNotFoundException("Scenario is disabled: " + scenarioCode));
            }
            
            // Get executor
            String executionType = scenario.getExecutionType();
            if (executionType == null) {
                executionType = ExecutionType.DB_QUERY.name();
            }
            
            DynamicExecutor executor = executorsByType.get(executionType);
            if (executor == null) {
                return Mono.error(new ScenarioNotFoundException(
                        "No executor found for execution type: " + executionType));
            }
            
            log.info("Routing scenario {} reactively to {} executor", 
                    scenarioCode, executor.getClass().getSimpleName());
            
            // Execute
            if (dryRun) {
                return Mono.just(executor.executeDryRun(request, scenario));
            }
            
            long timeoutMs = scenario.getTimeoutMs() != null ? scenario.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
            return executor.executeReactive(request, scenario)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .doOnError(e -> log.error("Reactive execution failed for {}: {}", scenarioCode, e.getMessage()));
        });
    }

    /**
     * Get scenario from cache or database.
     */
    public AiScenario getScenario(String scenarioCode) {
        return scenarioCache.computeIfAbsent(scenarioCode, this::loadScenarioFromDb);
    }

    private AiScenario loadScenarioFromDb(String scenarioCode) {
        return scenarioRepository.findByScenarioCode(scenarioCode)
                .orElseThrow(() -> new ScenarioNotFoundException("Scenario not found: " + scenarioCode));
    }

    /**
     * Check if a scenario is active.
     */
    public boolean isScenarioActive(String scenarioCode) {
        return activeScenarios.computeIfAbsent(scenarioCode, this::checkScenarioActiveInDb);
    }

    private boolean checkScenarioActiveInDb(String scenarioCode) {
        try {
            return scenarioRepository.findByScenarioCode(scenarioCode)
                    .map(s -> Boolean.TRUE.equals(s.getActive()))
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Failed to check scenario status for {}, defaulting to inactive", scenarioCode);
            return false;
        }
    }

    /**
     * Refresh scenario cache from database.
     */
    public void refreshScenarioCache() {
        try {
            List<AiScenario> allScenarios = scenarioRepository.findAll();
            scenarioCache.clear();
            activeScenarios.clear();
            
            for (AiScenario scenario : allScenarios) {
                scenarioCache.put(scenario.getScenarioCode(), scenario);
                activeScenarios.put(scenario.getScenarioCode(), Boolean.TRUE.equals(scenario.getActive()));
            }
            
            log.info("Refreshed scenario cache: {} scenarios loaded, {} active", 
                    scenarioCache.size(), 
                    activeScenarios.values().stream().filter(v -> v).count());
        } catch (Exception e) {
            log.error("Failed to refresh scenario cache", e);
        }
    }

    /**
     * Enable a scenario (updates cache, DB update should be done separately).
     */
    public void enableScenario(String scenarioCode) {
        activeScenarios.put(scenarioCode, true);
        log.info("Scenario {} enabled in cache", scenarioCode);
    }

    /**
     * Disable a scenario (updates cache, DB update should be done separately).
     */
    public void disableScenario(String scenarioCode) {
        activeScenarios.put(scenarioCode, false);
        log.info("Scenario {} disabled in cache", scenarioCode);
    }

    /**
     * Invalidate cache for a specific scenario (forces reload from DB).
     */
    public void invalidateScenario(String scenarioCode) {
        scenarioCache.remove(scenarioCode);
        activeScenarios.remove(scenarioCode);
        log.info("Invalidated cache for scenario {}", scenarioCode);
    }

    /**
     * Get all registered scenarios.
     */
    public Set<String> getRegisteredScenarios() {
        return Set.copyOf(scenarioCache.keySet());
    }

    /**
     * Get active scenarios only.
     */
    public Set<String> getActiveScenarios() {
        return activeScenarios.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Get supported execution types.
     */
    public Set<String> getSupportedExecutionTypes() {
        return Set.copyOf(executorsByType.keySet());
    }

    /**
     * Check if an execution type is supported.
     */
    public boolean hasExecutorForType(String executionType) {
        return executorsByType.containsKey(executionType);
    }
}
