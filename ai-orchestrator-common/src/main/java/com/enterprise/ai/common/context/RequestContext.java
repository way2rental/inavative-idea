package com.enterprise.ai.common.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request context for internal parameters.
 * These parameters are STRICTLY INTERNAL and must never be exposed to LLMs or end users.
 * 
 * Used for:
 * - Access validation
 * - Policy evaluation
 * - Ownership verification
 * - Tenant isolation
 * - Row-level security
 * 
 * SECURITY CRITICAL:
 * - Set ONLY from JWT at request ingress layer
 * - NEVER accepted from frontend
 * - NEVER passed as method parameter
 */
@Data
@Builder
public class RequestContext {

    /**
     * User ID from authentication (from JWT subject)
     */
    private String userId;

    /**
     * Corporate code for tenant identification
     */
    private String corpCode;

    /**
     * Tenant ID for multi-tenant isolation
     */
    private String tenantId;

    /**
     * Organization ID for row-level security
     * Used interchangeably with tenantId for backward compatibility
     */
    private String orgId;

    /**
     * Primary user role (ADMIN, USER, AUDITOR, OPERATOR)
     */
    private String role;

    /**
     * List of all user roles (for multiple role support)
     */
    private List<String> roles;

    /**
     * IP address of the request
     */
    private String ipAddress;

    /**
     * Device identifier
     */
    private String deviceId;

    /**
     * Session identifier
     */
    private String sessionId;

    /**
     * Request timestamp
     */
    private long requestTimestamp;

    /**
     * Additional context attributes
     */
    private Map<String, Object> attributes;

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String requiredRole) {
        if (roles != null && !roles.isEmpty()) {
            return roles.stream().anyMatch(r -> r.equalsIgnoreCase(requiredRole));
        }
        return role != null && role.equalsIgnoreCase(requiredRole);
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... requiredRoles) {
        if (roles != null && !roles.isEmpty()) {
            for (String required : requiredRoles) {
                if (roles.stream().anyMatch(r -> r.equalsIgnoreCase(required))) {
                    return true;
                }
            }
            return false;
        }
        if (role == null) return false;
        for (String r : requiredRoles) {
            if (role.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    /**
     * Check if context belongs to same tenant
     */
    public boolean isSameTenant(String otherTenantId) {
        return tenantId != null && tenantId.equals(otherTenantId);
    }

    /**
     * Check if context belongs to same user
     */
    public boolean isSameUser(String otherUserId) {
        return userId != null && userId.equals(otherUserId);
    }

    /**
     * Get effective orgId (prefers orgId, falls back to tenantId)
     */
    public String getEffectiveOrgId() {
        return orgId != null ? orgId : tenantId;
    }
}
