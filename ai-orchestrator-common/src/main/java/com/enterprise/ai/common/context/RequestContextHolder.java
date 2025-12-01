package com.enterprise.ai.common.context;

/**
 * Thread-local holder for request context.
 * Provides access to internal parameters without passing through LLM/external systems.
 * 
 * SECURITY CRITICAL:
 * - Context is injected at Request Ingress Layer
 * - Never accepted from frontend blindly
 * - Never logged in plain text to LLM prompts
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> contextHolder = new ThreadLocal<>();

    private RequestContextHolder() {
        // Utility class
    }

    /**
     * Set the current request context
     */
    public static void setContext(RequestContext context) {
        contextHolder.set(context);
    }

    /**
     * Get the current request context
     */
    public static RequestContext getContext() {
        return contextHolder.get();
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
     * Clear the current request context
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
     * Get role from context
     */
    public static String getRole() {
        RequestContext context = contextHolder.get();
        return context != null ? context.getRole() : null;
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
