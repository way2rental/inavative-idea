package com.enterprise.ai.data.service;

import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.HttpUrlWhitelist;
import com.enterprise.ai.data.entity.RoleScenarioMap;
import com.enterprise.ai.data.repository.AiScenarioRepository;
import com.enterprise.ai.data.repository.HttpUrlWhitelistRepository;
import com.enterprise.ai.data.repository.RoleScenarioMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized configuration cache service.
 * Loads all configuration from DB once and caches it.
 * Provides cache invalidation on updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigCacheService {

    private final AiScenarioRepository scenarioRepository;
    private final HttpUrlWhitelistRepository urlWhitelistRepository;
    private final RoleScenarioMapRepository roleScenarioMapRepository;

    // In-memory cache for faster access
    private final Map<String, AiScenario> scenarioCache = new ConcurrentHashMap<>();
    private final Set<String> whitelistedUrls = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> roleToScenariosMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> scenarioToRolesMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing configuration cache from database...");
        refreshAllCaches();
        log.info("Configuration cache initialized successfully");
    }

    // ===================== SCENARIO OPERATIONS =====================

    @Cacheable(value = "scenarios", key = "'all'")
    public List<AiScenario> getAllScenarios() {
        return new ArrayList<>(scenarioCache.values());
    }

    @Cacheable(value = "scenarios", key = "#scenarioCode")
    public Optional<AiScenario> getScenarioByCode(String scenarioCode) {
        return Optional.ofNullable(scenarioCache.get(scenarioCode));
    }

    @Cacheable(value = "scenarios", key = "'active'")
    public List<AiScenario> getActiveScenarios() {
        return scenarioCache.values().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "scenarios", key = "'byType:' + #executionType")
    public List<AiScenario> getScenariosByExecutionType(String executionType) {
        return scenarioCache.values().stream()
                .filter(s -> executionType.equals(s.getExecutionType()))
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "scenarios", allEntries = true)
    public AiScenario saveScenario(AiScenario scenario) {
        AiScenario saved = scenarioRepository.save(scenario);
        scenarioCache.put(saved.getScenarioCode(), saved);
        log.info("Scenario {} saved and cache updated", saved.getScenarioCode());
        return saved;
    }

    @CacheEvict(value = "scenarios", allEntries = true)
    public void deleteScenario(String scenarioCode) {
        scenarioRepository.findByScenarioCode(scenarioCode).ifPresent(s -> {
            scenarioRepository.delete(s);
            scenarioCache.remove(scenarioCode);
            log.info("Scenario {} deleted and removed from cache", scenarioCode);
        });
    }

    @CacheEvict(value = "scenarios", allEntries = true)
    public void toggleScenarioStatus(String scenarioCode, boolean active) {
        scenarioRepository.findByScenarioCode(scenarioCode).ifPresent(s -> {
            s.setActive(active);
            scenarioRepository.save(s);
            scenarioCache.put(scenarioCode, s);
            log.info("Scenario {} status set to {}", scenarioCode, active);
        });
    }

    // ===================== URL WHITELIST OPERATIONS =====================

    @Cacheable(value = "urlWhitelist", key = "'all'")
    public Set<String> getWhitelistedUrls() {
        return new HashSet<>(whitelistedUrls);
    }

    public boolean isUrlWhitelisted(String url) {
        // Check exact match or pattern match
        if (whitelistedUrls.contains(url)) {
            return true;
        }
        // Check pattern matching (e.g., /api/v1/* matches /api/v1/anything)
        for (String pattern : whitelistedUrls) {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (url.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @CacheEvict(value = "urlWhitelist", allEntries = true)
    public HttpUrlWhitelist addWhitelistedUrl(String urlPattern, String description, String addedBy) {
        HttpUrlWhitelist whitelist = HttpUrlWhitelist.builder()
                .urlPattern(urlPattern)
                .description(description)
                .addedBy(addedBy)
                .active(true)
                .build();
        HttpUrlWhitelist saved = urlWhitelistRepository.save(whitelist);
        whitelistedUrls.add(urlPattern);
        log.info("URL pattern {} added to whitelist", urlPattern);
        return saved;
    }

    @CacheEvict(value = "urlWhitelist", allEntries = true)
    public void removeWhitelistedUrl(String urlPattern) {
        urlWhitelistRepository.findByUrlPattern(urlPattern).ifPresent(w -> {
            urlWhitelistRepository.delete(w);
            whitelistedUrls.remove(urlPattern);
            log.info("URL pattern {} removed from whitelist", urlPattern);
        });
    }

    // ===================== ROLE-SCENARIO MAPPING OPERATIONS =====================

    @Cacheable(value = "roleScenarioMap", key = "#role")
    public Set<String> getScenariosForRole(String role) {
        return roleToScenariosMap.getOrDefault(role, Collections.emptySet());
    }

    public boolean isScenarioAllowedForRole(String scenarioCode, String role) {
        Set<String> allowedScenarios = roleToScenariosMap.get(role);
        return allowedScenarios != null && allowedScenarios.contains(scenarioCode);
    }

    @CacheEvict(value = "roleScenarioMap", allEntries = true)
    public void mapRoleToScenario(String role, String scenarioCode) {
        RoleScenarioMap mapping = RoleScenarioMap.builder()
                .roleName(role)
                .scenarioCode(scenarioCode)
                .build();
        roleScenarioMapRepository.save(mapping);
        
        roleToScenariosMap.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(scenarioCode);
        scenarioToRolesMap.computeIfAbsent(scenarioCode, k -> ConcurrentHashMap.newKeySet()).add(role);
        log.info("Role {} mapped to scenario {}", role, scenarioCode);
    }

    @CacheEvict(value = "roleScenarioMap", allEntries = true)
    public void unmapRoleFromScenario(String role, String scenarioCode) {
        roleScenarioMapRepository.findByRoleNameAndScenarioCode(role, scenarioCode)
                .ifPresent(m -> {
                    roleScenarioMapRepository.delete(m);
                    Set<String> scenarios = roleToScenariosMap.get(role);
                    if (scenarios != null) scenarios.remove(scenarioCode);
                    Set<String> roles = scenarioToRolesMap.get(scenarioCode);
                    if (roles != null) roles.remove(role);
                    log.info("Role {} unmapped from scenario {}", role, scenarioCode);
                });
    }

    // ===================== CACHE REFRESH OPERATIONS =====================

    @CacheEvict(value = {"scenarios", "urlWhitelist", "roleScenarioMap"}, allEntries = true)
    public void refreshAllCaches() {
        refreshScenarioCache();
        refreshUrlWhitelistCache();
        refreshRoleScenarioMapCache();
    }

    @CacheEvict(value = "scenarios", allEntries = true)
    public void refreshScenarioCache() {
        scenarioCache.clear();
        scenarioRepository.findAll().forEach(s -> scenarioCache.put(s.getScenarioCode(), s));
        log.info("Scenario cache refreshed with {} scenarios", scenarioCache.size());
    }

    @CacheEvict(value = "urlWhitelist", allEntries = true)
    public void refreshUrlWhitelistCache() {
        whitelistedUrls.clear();
        urlWhitelistRepository.findByActiveTrue().forEach(w -> whitelistedUrls.add(w.getUrlPattern()));
        log.info("URL whitelist cache refreshed with {} patterns", whitelistedUrls.size());
    }

    @CacheEvict(value = "roleScenarioMap", allEntries = true)
    public void refreshRoleScenarioMapCache() {
        roleToScenariosMap.clear();
        scenarioToRolesMap.clear();
        roleScenarioMapRepository.findAll().forEach(m -> {
            roleToScenariosMap.computeIfAbsent(m.getRoleName(), k -> ConcurrentHashMap.newKeySet())
                    .add(m.getScenarioCode());
            scenarioToRolesMap.computeIfAbsent(m.getScenarioCode(), k -> ConcurrentHashMap.newKeySet())
                    .add(m.getRoleName());
        });
        log.info("Role-scenario mapping cache refreshed with {} roles", roleToScenariosMap.size());
    }

    // ===================== CACHE STATISTICS =====================

    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScenarios", scenarioCache.size());
        stats.put("activeScenarios", getActiveScenarios().size());
        stats.put("whitelistedUrls", whitelistedUrls.size());
        stats.put("totalRoles", roleToScenariosMap.size());
        stats.put("scenarios", scenarioCache.keySet());
        stats.put("roles", roleToScenariosMap.keySet());
        return stats;
    }
}
