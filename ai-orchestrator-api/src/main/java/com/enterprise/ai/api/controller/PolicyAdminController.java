package com.enterprise.ai.api.controller;

import com.enterprise.ai.data.entity.PolicyRule;
import com.enterprise.ai.data.repository.PolicyRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Admin controller for policy rule management.
 * Supports configurable business rules for AI behavior control.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
@Tag(name = "Policy Admin", description = "Policy rule management")
@PreAuthorize("hasRole('ADMIN')")
public class PolicyAdminController {

    private final PolicyRuleRepository policyRepository;
    private final ObjectMapper objectMapper;

    // ===================== LIST =====================

    @GetMapping
    @Operation(summary = "Get all policy rules")
    public ResponseEntity<List<PolicyDTO>> getAllPolicies() {
        log.info("Fetching all policy rules");
        List<PolicyRule> policies = policyRepository.findAllByOrderByPriorityDesc();
        List<PolicyDTO> dtos = policies.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active policies")
    public ResponseEntity<List<PolicyDTO>> getActivePolicies() {
        log.info("Fetching active policies");
        List<PolicyRule> policies = policyRepository.findByActiveTrue();
        List<PolicyDTO> dtos = policies.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/ordered")
    @Operation(summary = "Get active policies ordered by priority")
    public ResponseEntity<List<PolicyDTO>> getOrderedPolicies() {
        log.info("Fetching ordered active policies");
        List<PolicyRule> policies = policyRepository.findByActiveTrueOrderByPriorityDesc();
        List<PolicyDTO> dtos = policies.stream()
                .map(this::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // ===================== CRUD =====================

    @GetMapping("/{id}")
    @Operation(summary = "Get policy by ID")
    public ResponseEntity<PolicyDTO> getPolicyById(@PathVariable Long id) {
        log.info("Fetching policy with id: {}", id);
        return policyRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/key/{policyKey}")
    @Operation(summary = "Get policy by key")
    public ResponseEntity<PolicyDTO> getPolicyByKey(@PathVariable String policyKey) {
        log.info("Fetching policy with key: {}", policyKey);
        return policyRepository.findByPolicyKey(policyKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new policy rule")
    public ResponseEntity<PolicyDTO> createPolicy(@RequestBody PolicyFormDTO form) {
        log.info("Creating new policy: {}", form.policyKey);
        
        if (policyRepository.existsByPolicyKey(form.policyKey)) {
            return ResponseEntity.badRequest().build();
        }

        PolicyRule policy = PolicyRule.builder()
                .policyKey(form.policyKey)
                .policyName(form.policyName)
                .description(form.description)
                .ruleExpression(form.ruleExpression)
                .onFail(form.onFail != null ? form.onFail : "BLOCK")
                .failureMessage(form.failureMessage)
                .applicableScenarios(toJsonArray(form.applicableScenarios))
                .applicableRoles(toJsonArray(form.applicableRoles))
                .priority(form.priority != null ? form.priority : 0)
                .active(form.active != null ? form.active : true)
                .createdBy(form.createdBy)
                .build();

        PolicyRule saved = policyRepository.save(policy);
        log.info("Policy created: {}", saved.getPolicyKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update policy rule")
    public ResponseEntity<PolicyDTO> updatePolicy(@PathVariable Long id, @RequestBody PolicyFormDTO form) {
        log.info("Updating policy with id: {}", id);
        
        return policyRepository.findById(id)
                .map(policy -> {
                    policy.setPolicyName(form.policyName);
                    policy.setDescription(form.description);
                    policy.setRuleExpression(form.ruleExpression);
                    policy.setOnFail(form.onFail != null ? form.onFail : "BLOCK");
                    policy.setFailureMessage(form.failureMessage);
                    policy.setApplicableScenarios(toJsonArray(form.applicableScenarios));
                    policy.setApplicableRoles(toJsonArray(form.applicableRoles));
                    policy.setPriority(form.priority != null ? form.priority : 0);
                    policy.setActive(form.active != null ? form.active : true);
                    policy.setUpdatedAt(Instant.now());
                    
                    PolicyRule saved = policyRepository.save(policy);
                    log.info("Policy updated: {}", saved.getPolicyKey());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete policy rule")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        log.info("Deleting policy with id: {}", id);
        
        return policyRepository.findById(id)
                .map(policy -> {
                    policyRepository.delete(policy);
                    log.info("Policy deleted: {}", policy.getPolicyKey());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== STATUS =====================

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle policy active status")
    public ResponseEntity<PolicyDTO> togglePolicy(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        log.info("Toggling policy status for id: {}", id);
        
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return policyRepository.findById(id)
                .map(policy -> {
                    policy.setActive(active);
                    policy.setUpdatedAt(Instant.now());
                    PolicyRule saved = policyRepository.save(policy);
                    log.info("Policy {} status set to: {}", saved.getPolicyKey(), active);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== TESTING =====================

    @PostMapping("/{id}/test")
    @Operation(summary = "Test policy rule with sample context")
    public ResponseEntity<Map<String, Object>> testPolicy(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> testContext) {
        log.info("Testing policy with id: {}", id);
        
        return policyRepository.findById(id)
                .map(policy -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    // Simple expression evaluation (for demo purposes)
                    // In production, use SpEL or a proper expression engine
                    boolean passed = evaluateExpression(policy.getRuleExpression(), testContext);
                    
                    result.put("policyKey", policy.getPolicyKey());
                    result.put("passed", passed);
                    result.put("onFail", policy.getOnFail());
                    result.put("failureMessage", passed ? null : policy.getFailureMessage());
                    result.put("testContext", testContext);
                    
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== HELPERS =====================

    private PolicyDTO toDTO(PolicyRule policy) {
        PolicyDTO dto = new PolicyDTO();
        dto.id = policy.getId();
        dto.policyKey = policy.getPolicyKey();
        dto.policyName = policy.getPolicyName();
        dto.description = policy.getDescription();
        dto.ruleExpression = policy.getRuleExpression();
        dto.onFail = policy.getOnFail();
        dto.failureMessage = policy.getFailureMessage();
        dto.applicableScenarios = parseJsonArray(policy.getApplicableScenarios());
        dto.applicableRoles = parseJsonArray(policy.getApplicableRoles());
        dto.priority = policy.getPriority();
        dto.active = policy.getActive();
        dto.createdAt = policy.getCreatedAt() != null ? policy.getCreatedAt().toString() : null;
        dto.updatedAt = policy.getUpdatedAt() != null ? policy.getUpdatedAt().toString() : null;
        dto.createdBy = policy.getCreatedBy();
        return dto;
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON array", e);
            return new ArrayList<>();
        }
    }

    private boolean evaluateExpression(String expression, Map<String, Object> context) {
        // Simple expression evaluation for demo
        // In production, integrate with SpEL or similar
        if (expression == null || expression.isEmpty()) {
            return true;
        }
        
        // Check for simple length expressions
        if (expression.contains("length()")) {
            // Extract the expected comparison
            // Format: #{query.length() < 1000}
            try {
                if (context.containsKey("query")) {
                    String query = String.valueOf(context.get("query"));
                    if (expression.contains("< ")) {
                        int limit = Integer.parseInt(expression.split("< ")[1].replace("}", "").trim());
                        return query.length() < limit;
                    }
                }
            } catch (Exception e) {
                log.warn("Expression evaluation failed: {}", e.getMessage());
            }
        }
        
        // Default to passing
        return true;
    }

    // ===================== DTOs =====================

    @Data
    public static class PolicyDTO {
        public Long id;
        public String policyKey;
        public String policyName;
        public String description;
        public String ruleExpression;
        public String onFail;
        public String failureMessage;
        public List<String> applicableScenarios;
        public List<String> applicableRoles;
        public Integer priority;
        public Boolean active;
        public String createdAt;
        public String updatedAt;
        public String createdBy;
    }

    @Data
    public static class PolicyFormDTO {
        public String policyKey;
        public String policyName;
        public String description;
        public String ruleExpression;
        public String onFail;
        public String failureMessage;
        public List<String> applicableScenarios;
        public List<String> applicableRoles;
        public Integer priority;
        public Boolean active;
        public String createdBy;
    }
}
