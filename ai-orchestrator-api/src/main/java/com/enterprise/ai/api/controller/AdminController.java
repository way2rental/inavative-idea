package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.AiAuditLog;
import com.enterprise.ai.data.entity.AiScenario;
import com.enterprise.ai.data.entity.ChatSession;
import com.enterprise.ai.data.entity.HttpUrlWhitelist;
import com.enterprise.ai.data.repository.AiAuditLogRepository;
import com.enterprise.ai.data.repository.AiScenarioRepository;
import com.enterprise.ai.data.repository.ChatSessionRepository;
import com.enterprise.ai.data.repository.ChatMessageRepository;
import com.enterprise.ai.data.repository.HttpUrlWhitelistRepository;
import com.enterprise.ai.data.service.ConfigCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * REST controller for admin panel operations.
 * Provides CRUD operations for scenarios, audit logs, sessions, and URL whitelist.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin Panel API for configuration management")
public class AdminController {

    private final AiScenarioRepository scenarioRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final HttpUrlWhitelistRepository urlWhitelistRepository;
    private final ConfigCacheService configCacheService;

    // ===================== DASHBOARD STATS =====================

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get dashboard statistics", description = "Returns aggregated statistics for the admin dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("Fetching dashboard statistics");
        
        long totalScenarios = scenarioRepository.count();
        long activeScenarios = scenarioRepository.findByActiveTrue().size();
        long totalSessions = sessionRepository.count();
        
        // Calculate today's requests
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<AiAuditLog> todayLogs = auditLogRepository.findByRequestTimeBetween(startOfDay, Instant.now());
        long todayRequests = todayLogs.size();
        
        // Calculate average response time
        double avgResponseTime = todayLogs.stream()
                .filter(log -> log.getResponseTime() != null && log.getRequestTime() != null)
                .mapToLong(log -> log.getResponseTime().toEpochMilli() - log.getRequestTime().toEpochMilli())
                .average()
                .orElse(0);
        
        // Calculate success rate
        long successfulRequests = todayLogs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getSuccess()))
                .count();
        double successRate = todayRequests > 0 ? (successfulRequests * 100.0 / todayRequests) : 100;
        
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
    @Operation(summary = "Get all scenarios", description = "Returns list of all AI scenarios")
    public ResponseEntity<List<ScenarioDTO>> getAllScenarios() {
        log.info("Fetching all scenarios");
        List<AiScenario> scenarios = scenarioRepository.findAll();
        List<ScenarioDTO> dtos = scenarios.stream()
                .map(this::toScenarioDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Get scenario by ID", description = "Returns a single scenario by its ID")
    public ResponseEntity<ScenarioDTO> getScenarioById(@PathVariable Long id) {
        log.info("Fetching scenario with id: {}", id);
        return scenarioRepository.findById(id)
                .map(this::toScenarioDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/scenarios")
    @Operation(summary = "Create new scenario", description = "Creates a new AI scenario")
    public ResponseEntity<ScenarioDTO> createScenario(@RequestBody ScenarioFormDTO form) {
        log.info("Creating new scenario: {}", form.scenarioCode);
        
        if (scenarioRepository.existsByScenarioCode(form.scenarioCode)) {
            return ResponseEntity.badRequest().build();
        }
        
        AiScenario scenario = AiScenario.builder()
                .scenarioCode(form.scenarioCode)
                .description(form.description)
                .executionType(form.executionType)
                .httpMethod(form.httpMethod)
                .httpUrl(form.httpUrl)
                .sqlQuery(form.sqlQuery)
                .requestMapping(form.requestMapping)
                .responseMapping(form.responseMapping)
                .securityLevel(form.securityLevel)
                .requiredParams(form.requiredParams)
                .llmPromptTemplate(form.intentPrompt)
                .active(form.active != null ? form.active : true)
                .promptVersion(1)
                .build();
        
        AiScenario saved = configCacheService.saveScenario(scenario);
        return ResponseEntity.ok(toScenarioDTO(saved));
    }

    @PutMapping("/scenarios/{id}")
    @Operation(summary = "Update scenario", description = "Updates an existing AI scenario")
    public ResponseEntity<ScenarioDTO> updateScenario(@PathVariable Long id, @RequestBody ScenarioFormDTO form) {
        log.info("Updating scenario with id: {}", id);
        
        return scenarioRepository.findById(id)
                .map(scenario -> {
                    scenario.setDescription(form.description);
                    scenario.setExecutionType(form.executionType);
                    scenario.setHttpMethod(form.httpMethod);
                    scenario.setHttpUrl(form.httpUrl);
                    scenario.setSqlQuery(form.sqlQuery);
                    scenario.setRequestMapping(form.requestMapping);
                    scenario.setResponseMapping(form.responseMapping);
                    scenario.setSecurityLevel(form.securityLevel);
                    scenario.setRequiredParams(form.requiredParams);
                    scenario.setLlmPromptTemplate(form.intentPrompt);
                    scenario.setActive(form.active != null ? form.active : true);
                    scenario.setPromptVersion(scenario.getPromptVersion() + 1);
                    
                    AiScenario saved = configCacheService.saveScenario(scenario);
                    return ResponseEntity.ok(toScenarioDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/scenarios/{id}")
    @Operation(summary = "Delete scenario", description = "Deletes an AI scenario by ID")
    public ResponseEntity<Void> deleteScenario(@PathVariable Long id) {
        log.info("Deleting scenario with id: {}", id);
        
        return scenarioRepository.findById(id)
                .map(scenario -> {
                    configCacheService.deleteScenario(scenario.getScenarioCode());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/scenarios/{id}/status")
    @Operation(summary = "Toggle scenario status", description = "Enables or disables a scenario")
    public ResponseEntity<ScenarioDTO> toggleScenarioStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling scenario status for id: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return scenarioRepository.findById(id)
                .map(scenario -> {
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
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestTime"));
        Page<AiAuditLog> logsPage = auditLogRepository.findAll(pageRequest);
        
        // Filter results if filters provided
        List<AuditLogDTO> filteredLogs = logsPage.getContent().stream()
                .filter(log -> userId == null || (log.getUserId() != null && log.getUserId().contains(userId)))
                .filter(log -> scenarioCode == null || (log.getScenarioCode() != null && log.getScenarioCode().contains(scenarioCode)))
                .map(this::toAuditLogDTO)
                .toList();
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", filteredLogs);
        response.put("totalElements", logsPage.getTotalElements());
        response.put("totalPages", logsPage.getTotalPages());
        response.put("currentPage", page);
        
        return ResponseEntity.ok(response);
    }

    // ===================== CHAT SESSIONS =====================

    @GetMapping("/sessions")
    @Operation(summary = "Get chat sessions", description = "Returns paginated chat sessions")
    public ResponseEntity<Map<String, Object>> getChatSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Fetching chat sessions - page: {}, size: {}", page, size);
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastActivityAt"));
        Page<ChatSession> sessionsPage = sessionRepository.findAll(pageRequest);
        
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
    @Operation(summary = "Get URL whitelist", description = "Returns all URL whitelist entries")
    public ResponseEntity<List<UrlWhitelistDTO>> getUrlWhitelist() {
        log.info("Fetching URL whitelist");
        List<HttpUrlWhitelist> whitelist = urlWhitelistRepository.findAll();
        List<UrlWhitelistDTO> dtos = whitelist.stream()
                .map(this::toUrlWhitelistDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/url-whitelist")
    @Operation(summary = "Add URL to whitelist", description = "Adds a new URL pattern to the whitelist")
    public ResponseEntity<UrlWhitelistDTO> addUrlToWhitelist(@RequestBody UrlWhitelistFormDTO form) {
        log.info("Adding URL to whitelist: {}", form.urlPattern);
        
        if (urlWhitelistRepository.existsByUrlPattern(form.urlPattern)) {
            return ResponseEntity.badRequest().build();
        }
        
        HttpUrlWhitelist saved = configCacheService.addWhitelistedUrl(
                form.urlPattern, 
                form.description, 
                form.allowedMethods,
                "admin"
        );
        return ResponseEntity.ok(toUrlWhitelistDTO(saved));
    }

    @DeleteMapping("/url-whitelist/{id}")
    @Operation(summary = "Remove URL from whitelist", description = "Removes a URL pattern from the whitelist")
    public ResponseEntity<Void> removeUrlFromWhitelist(@PathVariable Long id) {
        log.info("Removing URL from whitelist with id: {}", id);
        
        return urlWhitelistRepository.findById(id)
                .map(whitelist -> {
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
        dto.securityLevel = scenario.getSecurityLevel();
        dto.requiredParams = parseJsonArray(scenario.getRequiredParams());
        dto.intentPrompt = scenario.getLlmPromptTemplate();
        dto.promptVersion = scenario.getPromptVersion();
        dto.active = scenario.getActive();
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
        dto.confidence = 0.85; // Default confidence since not stored in current schema
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
        dto.messageCount = messageRepository.findBySessionIdOrderByTimestampAsc(session.getSessionId()).size();
        dto.active = session.getLastActivityAt() != null && 
                session.getLastActivityAt().isAfter(Instant.now().minusSeconds(300)); // Active if activity within last 5 minutes
        return dto;
    }

    private UrlWhitelistDTO toUrlWhitelistDTO(HttpUrlWhitelist whitelist) {
        UrlWhitelistDTO dto = new UrlWhitelistDTO();
        dto.id = whitelist.getId();
        dto.urlPattern = whitelist.getUrlPattern();
        dto.description = whitelist.getDescription();
        dto.allowedMethods = whitelist.getAllowedMethods() != null ? whitelist.getAllowedMethods() : "GET";
        dto.active = whitelist.getActive();
        dto.createdAt = whitelist.getCreatedAt() != null ? whitelist.getCreatedAt().toString() : null;
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
        public String securityLevel;
        public List<String> requiredParams;
        public String intentPrompt;
        public String responseTemplate;
        public Integer promptVersion;
        public Boolean active;
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
        public String securityLevel;
        public String requiredParams;
        public String intentPrompt;
        public String responseTemplate;
        public Boolean active;
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
        public String createdAt;
    }

    public static class UrlWhitelistFormDTO {
        public String urlPattern;
        public String description;
        public String allowedMethods;
    }
}
