package com.enterprise.ai.api.controller;

/**
 * DEPRECATED: This controller has been merged into RbacAdminController.
 *
 * All functionality from this controller is now available in:
 * @see RbacAdminController
 *
 * This file is kept for reference only and should be deleted after verification.
 *
 * Migration completed: December 2, 2025
 * Reason: Conflicting endpoint mappings with existing RbacAdminController
 * Solution: Merged both controllers into unified RbacAdminController
 */

/*
 * ORIGINAL CODE MOVED TO: RbacAdminController.java
 *
 * All endpoints from this controller are now available in RbacAdminController:
 * - GET /api/admin/rbac/mappings
 * - GET /api/admin/rbac/mappings/{id}
 * - GET /api/admin/rbac/roles
 * - GET /api/admin/rbac/scenarios
 * - GET /api/admin/rbac/roles/{roleName}/scenarios
 * - GET /api/admin/rbac/scenarios/{scenarioCode}/roles
 * - GET /api/admin/rbac/check-access
 * - GET /api/admin/rbac/matrix
 * - POST /api/admin/rbac/mappings
 * - POST /api/admin/rbac/mappings/bulk
 * - DELETE /api/admin/rbac/mappings/{id}
 * - DELETE /api/admin/rbac/revoke
 * - DELETE /api/admin/rbac/roles/{roleName}/revoke-all
 * - DELETE /api/admin/rbac/scenarios/{scenarioCode}/revoke-all
 * - POST /api/admin/rbac/cache/refresh
 *
 * The merged controller also maintains backward compatibility with legacy endpoints.
 */
