package com.enterprise.ai.common.context;

import java.util.List;

/**
 * Thread-local holder for request context.
 * Provides access to internal parameters without passing through LLM/external systems.
 * 
 * SECURITY CRITICAL:
 * - Context is injected at Request Ingress Layer (JWT Filter)
 * - Never accepted from frontend blindly
 * - Never logged in plain text to LLM prompts
 * - Must be cleared at end of request to prevent thread-leak
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> contextHolder = new ThreadLocal<>();

    private RequestContextHolder() {
        // Utility class
    }

    /**
     * Set the current request context
     */
    public static void set(RequestContext context) {
        contextHolder.set(context);
    }

    /**
     * Alias for set() - for backward compatibility
     */
    public static void setContext(RequestContext context) {
        set(context);
    }

    /**
     * Get the current request context
     */
    public static RequestContext get() {
        return contextHolder.get();
    }

    /**
     * Alias for get() - for backward compatibility
     */
    public static RequestContext getContext() {
        return get();
    }

    /**
     * Get the current request context or throw if not present
     */
    public static RequestContext getContextRequired() {
        RequestContext context = contextHolder.get();
        if (context == null) {
            throw new IllegalStateException("Request context not initialized");
        }
        return context;
    }

    /**
     * Clear the current request context.
     * MUST be called at end of request to prevent thread-leak in Tomcat thread pool.
     */
    public static void clear() {
        contextHolder.remove();
    }

    /**
     * Get user ID from context
     */
    public static String getUserId() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * Get tenant ID from context
     */
    public static String getTenantId() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getTenantId() : null;
    }

    /**
     * Get org ID from context (for row-level security)
     */
    public static String getOrgId() {
        RequestContext context = contextHolder.get();
        if (context == null) return null;
        // orgId takes precedence, fallback to tenantId
        return context.getOrgId() != null ? context.getOrgId() : context.getTenantId();
    }

    /**
     * Get role from context
     */
    public static String getRole() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getRole() : null;
    }

    /**
     * Get all roles from context
     */
    public static List<String> getRoles() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getRoles() : null;
    }

    /**
     * Get session ID from context
     */
    public static String getSessionId() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getSessionId() : null;
    }

    /**
     * Check if context is present
     */
    public static boolean hasContext() {
        return contextHolder.get() != null;
    }

    /**
     * Check if user has required role
     */
    public static boolean hasRole(String role) {
        RequestContext context = contextHolder.get();
        return context != null && context.hasRole(role);
    }

    /**
     * Check if user has any of required roles
     */
    public static boolean hasAnyRole(String... roles) {
        RequestContext context = contextHolder.get();
        return context != null && context.hasAnyRole(roles);
    }
}
