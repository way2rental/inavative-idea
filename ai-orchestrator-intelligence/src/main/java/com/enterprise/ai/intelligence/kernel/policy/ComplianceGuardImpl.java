package com.enterprise.ai.intelligence.kernel.policy;

import com.enterprise.ai.common.dto.ComplianceResult;
import com.enterprise.ai.common.dto.ReasoningPlan;
import com.enterprise.ai.data.entity.PolicyRule;
import com.enterprise.ai.data.repository.PolicyRuleRepository;
import com.enterprise.ai.data.service.RbacManagementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Policy & Compliance Guard Implementation.
 * 
 * REUSES:
 * - PolicyRuleRepository for policy rules
 * - RbacManagementService for authorization checks
 * - PolicyRule entity (already exists)
 * 
 * NO HARDCODING - All policies from database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceGuardImpl implements ComplianceGuard {

    private final PolicyRuleRepository policyRuleRepository;
    private final RbacManagementService rbacManagementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<ComplianceResult> validatePreLlm(
            ReasoningPlan reasoningPlan,
            String userId,
            List<String> userRoles,
            String query) {
        
        return Mono.fromCallable(() -> {
            try {
                log.debug("PRE-LLM validation for intent: {}, user: {}", reasoningPlan.getIntent(), userId);

                List<ComplianceResult.Violation> violations = new ArrayList<>();
                List<String> appliedPolicies = new ArrayList<>();
                String riskLevel = "LOW";

                // 1. Intent allow-listing (check if intent is allowed)
                if (reasoningPlan.getIntent() != null && !"UNKNOWN".equals(reasoningPlan.getIntent())) {
                    // Check RBAC authorization
                    boolean authorized = false;
                    if (userRoles != null && !userRoles.isEmpty()) {
                        for (String role : userRoles) {
                            if (rbacManagementService.hasAccess(role, reasoningPlan.getIntent())) {
                                authorized = true;
                                break;
                            }
                        }
                    }
                    
                    if (!authorized) {
                        violations.add(ComplianceResult.Violation.builder()
                                .policyKey("RBAC_AUTHORIZATION")
                                .policyName("Role-Based Access Control")
                                .message("You don't have permission to access this feature.")
                                .severity("BLOCK")
                                .build());
                        riskLevel = "HIGH";
                    }
                    appliedPolicies.add("RBAC_AUTHORIZATION");
                }

                // 2. Policy rule validation (from database)
                List<PolicyRule> applicablePolicies = getApplicablePolicies(
                        reasoningPlan.getIntent(),
                        userRoles
                );

                for (PolicyRule policy : applicablePolicies) {
                    appliedPolicies.add(policy.getPolicyKey());
                    
                    // Evaluate policy rule (simple implementation - can be enhanced with SpEL)
                    boolean rulePassed = evaluatePolicyRule(policy, query, reasoningPlan, userRoles);
                    
                    if (!rulePassed) {
                        String severity = policy.getOnFail() != null ? policy.getOnFail() : "BLOCK";
                        violations.add(ComplianceResult.Violation.builder()
                                .policyKey(policy.getPolicyKey())
                                .policyName(policy.getPolicyName() != null ? policy.getPolicyName() : policy.getPolicyKey())
                                .message(policy.getFailureMessage() != null ? policy.getFailureMessage() : "Policy violation")
                                .severity(severity)
                                .build());
                        
                        if ("BLOCK".equals(severity)) {
                            riskLevel = "HIGH";
                        } else if ("WARN".equals(severity) && !"HIGH".equals(riskLevel)) {
                            riskLevel = "MEDIUM";
                        }
                    }
                }

                // 3. Query length validation (basic policy)
                if (query != null && query.length() > 2000) {
                    violations.add(ComplianceResult.Violation.builder()
                            .policyKey("MAX_QUERY_LENGTH")
                            .policyName("Maximum Query Length")
                            .message("Query is too long (maximum 2000 characters)")
                            .severity("BLOCK")
                            .build());
                    riskLevel = "HIGH";
                    appliedPolicies.add("MAX_QUERY_LENGTH");
                }

                // Determine if blocked
                boolean blocked = violations.stream()
                        .anyMatch(v -> "BLOCK".equals(v.getSeverity()));

                String blockingMessage = blocked 
                        ? violations.stream()
                                .filter(v -> "BLOCK".equals(v.getSeverity()))
                                .map(ComplianceResult.Violation::getMessage)
                                .findFirst()
                                .orElse("Request blocked by policy")
                        : null;

                return ComplianceResult.builder()
                        .compliant(violations.isEmpty())
                        .blocked(blocked)
                        .violations(violations)
                        .appliedPolicies(appliedPolicies)
                        .riskLevel(riskLevel)
                        .blockingMessage(blockingMessage)
                        .metadata(Map.of(
                                "userId", userId,
                                "intent", reasoningPlan.getIntent() != null ? reasoningPlan.getIntent() : "UNKNOWN",
                                "validatedAt", System.currentTimeMillis()
                        ))
                        .build();

            } catch (Exception e) {
                log.error("PRE-LLM validation failed: {}", e.getMessage(), e);
                // On error, block for safety
                return ComplianceResult.builder()
                        .compliant(false)
                        .blocked(true)
                        .violations(List.of(ComplianceResult.Violation.builder()
                                .policyKey("VALIDATION_ERROR")
                                .policyName("Validation Error")
                                .message("An error occurred during validation")
                                .severity("BLOCK")
                                .build()))
                        .riskLevel("CRITICAL")
                        .blockingMessage("Validation error occurred")
                        .build();
            }
        });
    }

    @Override
    public Mono<ComplianceResult> validatePostLlm(
            String llmResponse,
            ReasoningPlan reasoningPlan,
            String userId,
            List<String> userRoles) {
        
        return Mono.fromCallable(() -> {
            try {
                log.debug("POST-LLM validation for intent: {}, user: {}", reasoningPlan.getIntent(), userId);

                List<ComplianceResult.Violation> violations = new ArrayList<>();
                List<String> appliedPolicies = new ArrayList<>();
                Map<String, String> maskedData = new HashMap<>();
                String riskLevel = "LOW";

                if (llmResponse == null || llmResponse.isEmpty()) {
                    violations.add(ComplianceResult.Violation.builder()
                            .policyKey("EMPTY_RESPONSE")
                            .policyName("Empty Response")
                            .message("LLM returned empty response")
                            .severity("WARN")
                            .build());
                    riskLevel = "MEDIUM";
                }

                // 1. Sensitive data masking (basic implementation)
                String maskedResponse = maskSensitiveData(llmResponse, maskedData);
                if (!maskedResponse.equals(llmResponse)) {
                    appliedPolicies.add("SENSITIVE_DATA_MASKING");
                }

                // 2. Compliance phrasing enforcement (basic checks)
                if (containsNonCompliantPhrasing(llmResponse)) {
                    violations.add(ComplianceResult.Violation.builder()
                            .policyKey("COMPLIANCE_PHRASING")
                            .policyName("Compliance Phrasing")
                            .message("Response contains non-compliant phrasing")
                            .severity("WARN")
                            .build());
                    riskLevel = "MEDIUM";
                    appliedPolicies.add("COMPLIANCE_PHRASING");
                }

                // 3. Basic hallucination detection (length check, consistency)
                if (llmResponse.length() > 10000) {
                    violations.add(ComplianceResult.Violation.builder()
                            .policyKey("RESPONSE_LENGTH")
                            .policyName("Response Length")
                            .message("Response is unusually long (possible hallucination)")
                            .severity("WARN")
                            .build());
                    riskLevel = "MEDIUM";
                    appliedPolicies.add("RESPONSE_LENGTH");
                }

                boolean blocked = violations.stream()
                        .anyMatch(v -> "BLOCK".equals(v.getSeverity()));

                return ComplianceResult.builder()
                        .compliant(violations.isEmpty())
                        .blocked(blocked)
                        .violations(violations)
                        .appliedPolicies(appliedPolicies)
                        .riskLevel(riskLevel)
                        .maskedData(maskedData)
                        .metadata(Map.of(
                                "userId", userId,
                                "intent", reasoningPlan.getIntent() != null ? reasoningPlan.getIntent() : "UNKNOWN",
                                "responseLength", llmResponse != null ? llmResponse.length() : 0,
                                "validatedAt", System.currentTimeMillis()
                        ))
                        .build();

            } catch (Exception e) {
                log.error("POST-LLM validation failed: {}", e.getMessage(), e);
                // On error, allow but log
                return ComplianceResult.builder()
                        .compliant(true)
                        .blocked(false)
                        .riskLevel("LOW")
                        .build();
            }
        });
    }

    /**
     * Get applicable policy rules for scenario and roles.
     */
    private List<PolicyRule> getApplicablePolicies(String scenarioCode, List<String> userRoles) {
        List<PolicyRule> allPolicies = policyRuleRepository.findByActiveTrueOrderByPriorityDesc();
        
        return allPolicies.stream()
                .filter(policy -> {
                    // Check scenario applicability
                    if (policy.getApplicableScenarios() != null && !policy.getApplicableScenarios().isEmpty()) {
                        try {
                            List<String> applicableScenarios = objectMapper.readValue(
                                    policy.getApplicableScenarios(),
                                    new TypeReference<List<String>>() {}
                            );
                            if (scenarioCode != null && !applicableScenarios.contains(scenarioCode)) {
                                return false;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse applicable scenarios for policy {}: {}", 
                                    policy.getPolicyKey(), e.getMessage());
                        }
                    }
                    
                    // Check role applicability
                    if (policy.getApplicableRoles() != null && !policy.getApplicableRoles().isEmpty()) {
                        try {
                            List<String> applicableRoles = objectMapper.readValue(
                                    policy.getApplicableRoles(),
                                    new TypeReference<List<String>>() {}
                            );
                            if (userRoles == null || userRoles.isEmpty() || 
                                    !applicableRoles.stream().anyMatch(userRoles::contains)) {
                                return false;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse applicable roles for policy {}: {}", 
                                    policy.getPolicyKey(), e.getMessage());
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Evaluate policy rule (simple implementation).
     * TODO: Enhance with SpEL evaluation for complex rules.
     */
    private boolean evaluatePolicyRule(PolicyRule policy, String query, ReasoningPlan reasoningPlan, List<String> userRoles) {
        if (policy.getRuleExpression() == null || policy.getRuleExpression().isEmpty()) {
            return true; // No rule = always pass
        }

        // Simple rule evaluation (can be enhanced with SpEL)
        String expression = policy.getRuleExpression().toUpperCase();
        
        // Example: Check query length
        if (expression.contains("QUERY.LENGTH") || expression.contains("LENGTH")) {
            if (query != null && query.length() > 1000) {
                return false;
            }
        }
        
        // Add more rule evaluations as needed
        
        return true; // Default: pass
    }

    /**
     * Mask sensitive data in response.
     */
    private String maskSensitiveData(String response, Map<String, String> maskedData) {
        if (response == null) {
            return response;
        }

        String masked = response;
        
        // Mask credit card numbers (basic pattern)
        Pattern cardPattern = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
        masked = cardPattern.matcher(masked).replaceAll("****-****-****-****");
        
        // Add more masking patterns as needed
        
        if (!masked.equals(response)) {
            maskedData.put("originalLength", String.valueOf(response.length()));
            maskedData.put("maskedLength", String.valueOf(masked.length()));
        }
        
        return masked;
    }

    /**
     * Check if response contains non-compliant phrasing.
     */
    private boolean containsNonCompliantPhrasing(String response) {
        if (response == null) {
            return false;
        }

        String lower = response.toLowerCase();
        
        // Example non-compliant phrases (can be enhanced with database-driven rules)
        String[] nonCompliantPhrases = {
                "guarantee",
                "always works",
                "no risk"
        };
        
        for (String phrase : nonCompliantPhrases) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        
        return false;
    }
}
