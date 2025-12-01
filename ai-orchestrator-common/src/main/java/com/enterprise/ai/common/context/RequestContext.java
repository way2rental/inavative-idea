package com.enterprise.ai.common.context;

import lombok.Builder;
import lombok.Data;

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
 */
@Data
@Builder
public class RequestContext {

    /**
     * User ID from authentication
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
     * User role (ADMIN, USER, AUDITOR, OPERATOR)
     */
    private String role;

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
        return role != null && role.equalsIgnoreCase(requiredRole);
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roles) {
        if (role == null) return false;
        for (String r : roles) {
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
}
