package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.entity.HttpUrlWhitelist;
import com.enterprise.ai.data.service.ConfigCacheService;
import com.enterprise.ai.data.service.AuditLogService;
import com.enterprise.ai.data.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for admin panel operations.
 * All operations go through centralized services with proper caching.
 * No direct repository access - follows SOLID principles.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin Panel API for configuration management")
public class AdminController {

    // Default confidence when not stored in audit log
    private static final double DEFAULT_CONFIDENCE = 0.85;
    // Session active timeout in seconds (5 minutes)
    private static final int SESSION_ACTIVE_TIMEOUT_SECONDS = 300;

    private final ConfigCacheService configCacheService;
    private final AuditLogService auditLogService;
    private final SessionService sessionService;

    // ===================== DASHBOARD STATS =====================

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get dashboard statistics", description = "Returns aggregated statistics for the admin dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("Fetching dashboard statistics");
        
        // Use cached scenario data
        List<AiScenario> allScenarios = configCacheService.getAllScenarios();
        long totalScenarios = allScenarios.size();
        long activeScenarios = configCacheService.getActiveScenarios().size();
        
        // Use session service for session data
        long totalSessions = sessionService.getTotalSessionCount();
        
        // Use audit log service for today's stats
        Map<String, Object> todayStats = auditLogService.getTodayStats();
        long todayRequests = (Long) todayStats.getOrDefault("todayRequests", 0L);
        double avgResponseTime = (Double) todayStats.getOrDefault("avgResponseTime", 0.0);
        double successRate = (Double) todayStats.getOrDefault("successRate", 100.0);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScenarios", totalScenarios);
        stats.put("activeScenarios", activeScenarios);
        stats.put("totalSessions", totalSessions);
        stats.put("todayRequests", todayRequests);
        stats.put("avgResponseTime", Math.round(avgResponseTime));
        stats.put("successRate", Math.round(successRate * 10.0) / 10.0);
        
        return ResponseEntity.ok(stats);
    }

    // ===================== SCENARIOS CRUD =====================

    @GetMapping("/scenarios")
    @Operation(summary = "Get all scenarios", description = "Returns list of all AI scenarios from cache")
    public ResponseEntity<List<ScenarioDTO>> getAllScenarios() {
        log.info("Fetching all scenarios from cache");
        List<AiScenario> scenarios = configCacheService.getAllScenarios();
        List<ScenarioDTO> dtos = scenarios.stream()
                .map(this::toScenarioDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Get scenario by ID", description = "Returns a single scenario by its ID from cache")
    public ResponseEntity<ScenarioDTO> getScenarioById(@PathVariable Long id) {
        log.info("Fetching scenario with id: {}", id);
        return configCacheService.getScenarioById(id)
                .map(this::toScenarioDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/scenarios")
    @Operation(summary = "Create new scenario", description = "Creates a new AI scenario and updates cache")
    public ResponseEntity<ScenarioDTO> createScenario(@RequestBody ScenarioFormDTO form) {
        log.info("Creating new scenario: {}", form.scenarioCode);
        
        // Check if exists via cache
        if (configCacheService.getScenarioByCode(form.scenarioCode).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Handle legacy field - intentPrompt maps to llmPromptTemplate
        String promptTemplate = form.llmPromptTemplate != null ? form.llmPromptTemplate : form.intentPrompt;
        
        AiScenario scenario = AiScenario.builder()
                .scenarioCode(form.scenarioCode)
                .description(form.description)
                .executionType(form.executionType)
                .httpMethod(form.httpMethod)
                .httpUrl(form.httpUrl)
                .sqlQuery(form.sqlQuery)
                .requestMapping(form.requestMapping)
                .responseMapping(form.responseMapping)
                .timeoutMs(form.timeoutMs != null ? form.timeoutMs : 5000)
                .requiredParams(form.requiredParams)
                .llmPromptTemplate(promptTemplate)
                .active(form.active != null ? form.active : true)
                // Multi-filter engine fields
                .filterDefinitions(form.filterDefinitions)
                .securityFilters(form.securityFilters)
                .maxResults(form.maxResults != null ? form.maxResults : 100)
                .defaultSort(form.defaultSort)
                .build();
        
        // Save via ConfigCacheService - automatically updates cache
        AiScenario saved = configCacheService.saveScenario(scenario);
        return ResponseEntity.ok(toScenarioDTO(saved));
    }

    @PutMapping("/scenarios/{id}")
    @Operation(summary = "Update scenario", description = "Updates an existing AI scenario and refreshes cache")
    public ResponseEntity<ScenarioDTO> updateScenario(@PathVariable Long id, @RequestBody ScenarioFormDTO form) {
        log.info("Updating scenario with id: {}", id);
        
        return configCacheService.getScenarioById(id)
                .map(scenario -> {
                    // Handle legacy field - intentPrompt maps to llmPromptTemplate
                    String promptTemplate = form.llmPromptTemplate != null ? form.llmPromptTemplate : form.intentPrompt;
                    
                    scenario.setDescription(form.description);
                    scenario.setExecutionType(form.executionType);
                    scenario.setHttpMethod(form.httpMethod);
                    scenario.setHttpUrl(form.httpUrl);
                    scenario.setSqlQuery(form.sqlQuery);
                    scenario.setRequestMapping(form.requestMapping);
                    scenario.setResponseMapping(form.responseMapping);
                    scenario.setTimeoutMs(form.timeoutMs != null ? form.timeoutMs : scenario.getTimeoutMs());
                    scenario.setRequiredParams(form.requiredParams);
                    scenario.setLlmPromptTemplate(promptTemplate);
                    scenario.setActive(form.active != null ? form.active : true);
                    // Multi-filter engine fields
                    scenario.setFilterDefinitions(form.filterDefinitions);
                    scenario.setSecurityFilters(form.securityFilters);
                    scenario.setMaxResults(form.maxResults != null ? form.maxResults : scenario.getMaxResults());
                    scenario.setDefaultSort(form.defaultSort);
                    
                    // Save via ConfigCacheService - automatically updates cache
                    AiScenario saved = configCacheService.saveScenario(scenario);
                    return ResponseEntity.ok(toScenarioDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/scenarios/{id}")
    @Operation(summary = "Delete scenario", description = "Deletes an AI scenario and removes from cache")
    public ResponseEntity<Void> deleteScenario(@PathVariable Long id) {
        log.info("Deleting scenario with id: {}", id);
        
        return configCacheService.getScenarioById(id)
                .map(scenario -> {
                    // Delete via ConfigCacheService - automatically removes from cache
                    configCacheService.deleteScenario(scenario.getScenarioCode());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/scenarios/{id}/status")
    @Operation(summary = "Toggle scenario status", description = "Enables or disables a scenario and updates cache")
    public ResponseEntity<ScenarioDTO> toggleScenarioStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling scenario status for id: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return configCacheService.getScenarioById(id)
                .map(scenario -> {
                    // Toggle via ConfigCacheService - automatically updates cache
                    configCacheService.toggleScenarioStatus(scenario.getScenarioCode(), active);
                    scenario.setActive(active);
                    return ResponseEntity.ok(toScenarioDTO(scenario));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== AUDIT LOGS =====================

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Returns paginated audit logs with optional filters")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String scenarioCode) {
        
        log.info("Fetching audit logs - page: {}, size: {}, userId: {}, scenarioCode: {}", page, size, userId, scenarioCode);
        
        // Use AuditLogService for paginated results
        Page<AiAuditLog> logsPage = auditLogService.getAuditLogs(page, size, userId, scenarioCode);
        
        List<AuditLogDTO> logDTOs = logsPage.getContent().stream()
                .map(this::toAuditLogDTO)
                .toList();
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", logDTOs);
        response.put("totalElements", logsPage.getTotalElements());
        response.put("totalPages", logsPage.getTotalPages());
        response.put("currentPage", page);
        
        return ResponseEntity.ok(response);
    }

    // ===================== CHAT SESSIONS =====================

    @GetMapping("/sessions")
    @Operation(summary = "Get chat sessions", description = "Returns paginated chat sessions with optional filters")
    public ResponseEntity<Map<String, Object>> getChatSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sessionId) {

        log.info("Fetching chat sessions - page: {}, size: {}, userId filter: {}, sessionId filter: {}",
                page, size, userId, sessionId);

        // Use SessionService for paginated results with filters
        Page<ChatSession> sessionsPage = sessionService.getSessionsWithFilters(userId, sessionId, page, size);

        List<SessionDTO> sessionDTOs = sessionsPage.getContent().stream()
                .map(this::toSessionDTO)
                .toList();
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", sessionDTOs);
        response.put("totalElements", sessionsPage.getTotalElements());
        response.put("totalPages", sessionsPage.getTotalPages());
        response.put("currentPage", page);
        
        return ResponseEntity.ok(response);
    }

    // ===================== URL WHITELIST =====================

    @GetMapping("/url-whitelist")
    @Operation(summary = "Get URL whitelist", description = "Returns all URL whitelist entries from cache")
    public ResponseEntity<List<UrlWhitelistDTO>> getUrlWhitelist() {
        log.info("Fetching URL whitelist from cache");
        List<HttpUrlWhitelist> whitelist = configCacheService.getAllWhitelistedUrls();
        List<UrlWhitelistDTO> dtos = whitelist.stream()
                .map(this::toUrlWhitelistDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/url-whitelist")
    @Operation(summary = "Add URL to whitelist", description = "Adds a new URL pattern to the whitelist and updates cache")
    public ResponseEntity<UrlWhitelistDTO> addUrlToWhitelist(@RequestBody UrlWhitelistFormDTO form) {
        log.info("Adding URL to whitelist: {}", form.urlPattern);
        
        // Check if exists via cache service
        if (configCacheService.isUrlWhitelisted(form.urlPattern)) {
            return ResponseEntity.badRequest().build();
        }
        
        // Add via ConfigCacheService - automatically updates cache
        HttpUrlWhitelist saved = configCacheService.addWhitelistedUrl(
                form.urlPattern, 
                form.description, 
                form.allowedMethods,
                "admin"
        );
        return ResponseEntity.ok(toUrlWhitelistDTO(saved));
    }

    @DeleteMapping("/url-whitelist/{id}")
    @Operation(summary = "Remove URL from whitelist", description = "Removes a URL pattern from the whitelist and updates cache")
    public ResponseEntity<Void> removeUrlFromWhitelist(@PathVariable Long id) {
        log.info("Removing URL from whitelist with id: {}", id);
        
        return configCacheService.getWhitelistById(id)
                .map(whitelist -> {
                    // Delete via ConfigCacheService - automatically removes from cache
                    configCacheService.removeWhitelistedUrl(whitelist.getUrlPattern());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== CACHE MANAGEMENT =====================

    @PostMapping("/cache/scenarios/refresh")
    @Operation(summary = "Refresh scenario cache", description = "Refreshes the scenario cache from database")
    public ResponseEntity<Map<String, String>> refreshScenarioCache() {
        log.info("Refreshing scenario cache");
        configCacheService.refreshScenarioCache();
        return ResponseEntity.ok(Map.of("message", "Scenario cache refreshed successfully"));
    }

    @PostMapping("/cache/clear")
    @Operation(summary = "Clear all caches", description = "Clears all configuration caches")
    public ResponseEntity<Map<String, String>> clearAllCache() {
        log.info("Clearing all caches");
        configCacheService.refreshAllCaches();
        return ResponseEntity.ok(Map.of("message", "All caches cleared and refreshed successfully"));
    }

    // ===================== DTO CONVERSIONS =====================

    private ScenarioDTO toScenarioDTO(AiScenario scenario) {
        ScenarioDTO dto = new ScenarioDTO();
        dto.id = scenario.getId();
        dto.scenarioCode = scenario.getScenarioCode();
        dto.scenarioName = scenario.getDescription() != null ? scenario.getDescription().split(" - ")[0] : scenario.getScenarioCode();
        dto.description = scenario.getDescription();
        dto.executionType = scenario.getExecutionType();
        dto.httpMethod = scenario.getHttpMethod();
        dto.httpUrl = scenario.getHttpUrl();
        dto.sqlQuery = scenario.getSqlQuery();
        dto.requestMapping = scenario.getRequestMapping();
        dto.responseMapping = scenario.getResponseMapping();
        dto.timeoutMs = scenario.getTimeoutMs();
        dto.requiredParams = parseJsonArray(scenario.getRequiredParams());
        dto.llmPromptTemplate = scenario.getLlmPromptTemplate();
        dto.active = scenario.getActive();
        // Multi-filter engine fields
        dto.filterDefinitions = scenario.getFilterDefinitions();
        dto.securityFilters = scenario.getSecurityFilters();
        dto.maxResults = scenario.getMaxResults();
        dto.defaultSort = scenario.getDefaultSort();
        return dto;
    }

    private AuditLogDTO toAuditLogDTO(AiAuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.id = log.getId();
        dto.sessionId = log.getExecutionId();
        dto.userId = log.getUserId() != null ? log.getUserId() : "unknown";
        dto.scenarioCode = log.getScenarioCode() != null ? log.getScenarioCode() : "N/A";
        dto.userQuery = extractQueryFromJson(log.getRawIntentJson());
        dto.detectedIntent = log.getScenarioCode();
        dto.confidence = DEFAULT_CONFIDENCE;
        dto.paramsExtracted = log.getRawIntentJson();
        dto.responseGenerated = extractResponseFromJson(log.getRawResultJson());
        dto.executionTimeMs = log.getResponseTime() != null && log.getRequestTime() != null
                ? log.getResponseTime().toEpochMilli() - log.getRequestTime().toEpochMilli()
                : 0;
        dto.errorDetails = log.getErrorMessage();
        dto.createdAt = log.getRequestTime() != null ? log.getRequestTime().toString() : null;
        return dto;
    }

    private SessionDTO toSessionDTO(ChatSession session) {
        SessionDTO dto = new SessionDTO();
        dto.id = session.getId();
        dto.sessionId = session.getSessionId();
        dto.userId = session.getUserId();
        dto.startTime = session.getCreatedAt() != null ? session.getCreatedAt().toString() : null;
        dto.lastActivityTime = session.getLastActivityAt() != null ? session.getLastActivityAt().toString() : null;
        dto.messageCount = sessionService.getMessageCountForSession(session.getSessionId());
        dto.active = session.getLastActivityAt() != null && 
                session.getLastActivityAt().isAfter(Instant.now().minusSeconds(SESSION_ACTIVE_TIMEOUT_SECONDS));
        return dto;
    }

    private UrlWhitelistDTO toUrlWhitelistDTO(HttpUrlWhitelist whitelist) {
        UrlWhitelistDTO dto = new UrlWhitelistDTO();
        dto.id = whitelist.getId();
        dto.urlPattern = whitelist.getUrlPattern();
        dto.description = whitelist.getDescription();
        dto.allowedMethods = whitelist.getAllowedMethods() != null ? whitelist.getAllowedMethods() : "GET";
        dto.active = whitelist.getActive();
        dto.addedBy = whitelist.getAddedBy();
        dto.createdAt = whitelist.getCreatedAt() != null ? whitelist.getCreatedAt().toString() : null;
        dto.updatedAt = whitelist.getUpdatedAt() != null ? whitelist.getUpdatedAt().toString() : null;
        return dto;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            // Simple JSON array parsing
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
                return Arrays.stream(json.split(","))
                        .map(s -> s.trim().replace("\"", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
            return List.of(json);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractQueryFromJson(String json) {
        if (json == null) return "N/A";
        // Simple extraction - in production use proper JSON parsing
        try {
            if (json.contains("\"query\"")) {
                int start = json.indexOf("\"query\"") + 9;
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
            return json.length() > 100 ? json.substring(0, 100) + "..." : json;
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String extractResponseFromJson(String json) {
        if (json == null) return "N/A";
        try {
            if (json.contains("\"message\"")) {
                int start = json.indexOf("\"message\"") + 11;
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
            return json.length() > 200 ? json.substring(0, 200) + "..." : json;
        } catch (Exception e) {
            return "N/A";
        }
    }

    // ===================== DTOs =====================

    public static class ScenarioDTO {
        public Long id;
        public String scenarioCode;
        public String scenarioName;
        public String description;
        public String executionType;
        public String httpMethod;
        public String httpUrl;
        public String sqlQuery;
        public String requestMapping;
        public String responseMapping;
        public Integer timeoutMs;
        public List<String> requiredParams;
        public String llmPromptTemplate;
        public Boolean active;
        // Multi-filter engine fields
        public String filterDefinitions;
        public String securityFilters;
        public Integer maxResults;
        public String defaultSort;
    }

    public static class ScenarioFormDTO {
        public String scenarioCode;
        public String scenarioName;
        public String description;
        public String executionType;
        public String httpMethod;
        public String httpUrl;
        public String sqlQuery;
        public String requestMapping;
        public String responseMapping;
        public Integer timeoutMs;
        public String requiredParams;
        public String llmPromptTemplate;
        // Legacy field - maps to llmPromptTemplate for backward compatibility
        public String intentPrompt;
        public Boolean active;
        // Multi-filter engine fields
        public String filterDefinitions;
        public String securityFilters;
        public Integer maxResults;
        public String defaultSort;
    }

    public static class AuditLogDTO {
        public Long id;
        public String sessionId;
        public String userId;
        public String scenarioCode;
        public String userQuery;
        public String detectedIntent;
        public double confidence;
        public String paramsExtracted;
        public String responseGenerated;
        public long executionTimeMs;
        public String errorDetails;
        public String createdAt;
    }

    public static class SessionDTO {
        public Long id;
        public String sessionId;
        public String userId;
        public String startTime;
        public String lastActivityTime;
        public int messageCount;
        public boolean active;
    }

    public static class UrlWhitelistDTO {
        public Long id;
        public String urlPattern;
        public String description;
        public String allowedMethods;
        public Boolean active;
        public String addedBy;
        public String createdAt;
        public String updatedAt;
    }

    public static class UrlWhitelistFormDTO {
        public String urlPattern;
        public String description;
        public String allowedMethods;
    }
}
