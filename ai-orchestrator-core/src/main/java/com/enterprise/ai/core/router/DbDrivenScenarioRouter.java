package com.enterprise.ai.core.router;

import com.enterprise.ai.common.dto.ScenarioRequest;
import com.enterprise.ai.common.dto.ScenarioResult;
import com.enterprise.ai.common.exception.ScenarioNotFoundException;
import com.enterprise.ai.core.scenario.ReactiveScenarioExecutor;
import com.enterprise.ai.core.scenario.ScenarioExecutor;
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
 * DB-driven scenario router that enables/disables scenarios without redeploy.
 * Routing decision comes from ai_scenarios table while executors remain Spring beans.
 */
@Slf4j
@Service
public class DbDrivenScenarioRouter {

    private final Map<String, ScenarioExecutor> executors;
    private final AiScenarioRepository scenarioRepository;
    
    // Cache of active scenarios (refreshed periodically)
    private final ConcurrentHashMap<String, Boolean> activeScenarios = new ConcurrentHashMap<>();

    public DbDrivenScenarioRouter(List<ScenarioExecutor> executorList, AiScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        ScenarioExecutor::getScenarioCode,
                        e -> e
                ));
        
        // Initial load of active scenarios
        refreshActiveScenarios();
        log.info("DB-driven ScenarioRouter initialized with {} executors", executors.size());
    }

    /**
     * Route a scenario request to the appropriate executor.
     * Checks DB for scenario enablement status.
     *
     * @param request scenario request
     * @return scenario result
     * @throws ScenarioNotFoundException if no executor is found or scenario is disabled
     */
    public ScenarioResult route(ScenarioRequest request) {
        String scenarioCode = request.getScenario();
        
        // Check if scenario is enabled in DB
        if (!isScenarioActive(scenarioCode)) {
            log.warn("Scenario {} is disabled in database", scenarioCode);
            throw new ScenarioNotFoundException("Scenario is disabled: " + scenarioCode);
        }
        
        ScenarioExecutor executor = executors.get(scenarioCode);
        if (executor == null) {
            throw new ScenarioNotFoundException("No executor found for: " + scenarioCode);
        }
        
        return executor.execute(request);
    }

    /**
     * Route a scenario request reactively (non-blocking).
     *
     * @param request scenario request
     * @return Mono of scenario result
     */
    public Mono<ScenarioResult> routeReactive(ScenarioRequest request) {
        String scenarioCode = request.getScenario();
        
        // Check if scenario is enabled
        if (!isScenarioActive(scenarioCode)) {
            log.warn("Scenario {} is disabled in database", scenarioCode);
            return Mono.error(new ScenarioNotFoundException("Scenario is disabled: " + scenarioCode));
        }
        
        ScenarioExecutor executor = executors.get(scenarioCode);
        if (executor == null) {
            return Mono.error(new ScenarioNotFoundException("No executor found for: " + scenarioCode));
        }
        
        // If executor supports reactive, use it
        if (executor instanceof ReactiveScenarioExecutor reactiveExecutor) {
            long maxTime = reactiveExecutor.getMaxExecutionTimeMs();
            return reactiveExecutor.executeReactive(request)
                    .timeout(Duration.ofMillis(maxTime))
                    .doOnError(e -> log.error("Reactive execution failed for {}: {}", scenarioCode, e.getMessage()));
        }
        
        // Fallback to blocking execution wrapped in Mono
        return Mono.fromCallable(() -> executor.execute(request))
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * Check if a scenario is active in the database.
     * Uses cached value for performance.
     */
    public boolean isScenarioActive(String scenarioCode) {
        return activeScenarios.computeIfAbsent(scenarioCode, this::checkScenarioActiveInDb);
    }

    /**
     * Refresh the active scenarios cache from database.
     * Can be called periodically or on demand.
     */
    public void refreshActiveScenarios() {
        try {
            List<AiScenario> activeList = scenarioRepository.findByActiveTrue();
            activeScenarios.clear();
            activeList.forEach(s -> activeScenarios.put(s.getScenarioCode(), true));
            
            // Add all executors with default active status if not in DB
            executors.keySet().forEach(code -> 
                    activeScenarios.computeIfAbsent(code, k -> true));
            
            log.info("Refreshed active scenarios cache: {} scenarios active", activeScenarios.size());
        } catch (Exception e) {
            log.error("Failed to refresh active scenarios from DB, using defaults", e);
            // Default all executors to active if DB is unavailable
            executors.keySet().forEach(code -> activeScenarios.put(code, true));
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

    private boolean checkScenarioActiveInDb(String scenarioCode) {
        try {
            return scenarioRepository.findByScenarioCode(scenarioCode)
                    .map(AiScenario::getActive)
                    .orElse(true); // Default to active if not in DB
        } catch (Exception e) {
            log.warn("Failed to check scenario status in DB for {}, defaulting to active", scenarioCode);
            return true;
        }
    }

    public boolean hasExecutor(String scenarioCode) {
        return executors.containsKey(scenarioCode);
    }

    public Set<String> getRegisteredScenarios() {
        return executors.keySet();
    }

    public Set<String> getActiveScenarios() {
        return activeScenarios.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
