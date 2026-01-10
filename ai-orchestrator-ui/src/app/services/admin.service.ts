import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  Scenario,
  ScenarioFormData,
  AuditLog,
  ChatSession,
  UrlWhitelist,
  ScenarioTestRequest,
  ScenarioTestResult,
  DashboardStats,
  PerformanceMetrics,
  PromptTemplate,
  PromptFormData,
  // IntentConfig, // REMOVED - Use Scenario instead
  // IntentFormData, // REMOVED - Use Scenario instead
  FollowUpGroup,
  FollowUpGroupFormData,
  FollowUpQuestion,
  PolicyRule,
  PolicyFormData,
  ResponseMapping,
  ResponseMappingForm,
  JsonPathTestRequest,
  JsonPathTestResult,
  RoleScenarioMapping,
  RbacMatrix,
  BulkRbacRequest,
  ResponseTemplate,
  ResponseTemplateFormData
} from '../models/admin.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private baseUrl = environment.apiUrl || 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  /**
   * Handle HTTP errors consistently across all admin endpoints
   */
  private handleError<T>(operation: string, fallback: T) {
    return (error: HttpErrorResponse): Observable<T> => {
      if (error.status === 403) {
        console.error(`${operation}: Access denied. User does not have required permissions.`);
      } else if (error.status === 401) {
        console.error(`${operation}: Unauthorized. Please log in again.`);
      } else {
        console.error(`${operation} failed:`, error.message);
      }
      return of(fallback);
    };
  }

  // Dashboard Stats - fetches from DB via backend
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/admin/dashboard/stats`).pipe(
      catchError(this.handleError('getDashboardStats', {
        totalScenarios: 0,
        activeScenarios: 0,
        totalSessions: 0,
        todayRequests: 0,
        avgResponseTime: 0,
        successRate: 0
      }))
    );
  }

  // Performance Metrics - fetches from DB via backend
  getPerformanceMetrics(): Observable<PerformanceMetrics> {
    return this.http.get<PerformanceMetrics>(`${this.baseUrl}/v2/chat/metrics`).pipe(
      catchError(this.handleError('getPerformanceMetrics', {
        avgIntentDetectionMs: 0,
        avgDbExecutionMs: 0,
        avgFormattingMs: 0,
        avgTotalMs: 0,
        p95ResponseTime: 0,
        p99ResponseTime: 0,
        requestsPerMinute: 0
      }))
    );
  }

//   // Ollama Status - fetches from backend health check
//   getOllamaStatus(): Observable<OllamaStatus> {
//     return this.http.get<OllamaStatus>(`${this.baseUrl}/ollama/health`).pipe(
//       catchError(this.handleError('getOllamaStatus', {
//         connected: false,
//         baseUrl: '',
//         model: '',
//         enabled: false,
//         lastCheckTime: new Date().toISOString()
//       }))
//     );
//   }

  // Scenarios CRUD - all data from DB
  getScenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>(`${this.baseUrl}/admin/scenarios`).pipe(
      catchError(this.handleError('getScenarios', []))
    );
  }

  getScenarioById(id: number): Observable<Scenario> {
    return this.http.get<Scenario>(`${this.baseUrl}/admin/scenarios/${id}`);
  }

  createScenario(scenario: ScenarioFormData): Observable<Scenario> {
    return this.http.post<Scenario>(`${this.baseUrl}/admin/scenarios`, scenario);
  }

  updateScenario(id: number, scenario: ScenarioFormData): Observable<Scenario> {
    return this.http.put<Scenario>(`${this.baseUrl}/admin/scenarios/${id}`, scenario);
  }

  deleteScenario(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/scenarios/${id}`);
  }

  toggleScenarioStatus(id: number, active: boolean): Observable<Scenario> {
    return this.http.patch<Scenario>(`${this.baseUrl}/admin/scenarios/${id}/status`, { active });
  }

  // Scenario Testing
  testScenario(request: ScenarioTestRequest): Observable<ScenarioTestResult> {
    return this.http.post<ScenarioTestResult>(`${this.baseUrl}/v2/scenario/test`, request);
  }

  getTestHistory(scenarioCode: string): Observable<ScenarioTestResult[]> {
    return this.http.get<ScenarioTestResult[]>(`${this.baseUrl}/admin/scenarios/${scenarioCode}/tests`);
  }

  // Audit Logs - all data from DB
  getAuditLogs(page = 0, size = 20, filters?: { userId?: string; scenarioCode?: string }): Observable<{ content: AuditLog[]; totalElements: number }> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (filters?.userId) params = params.set('userId', filters.userId);
    if (filters?.scenarioCode) params = params.set('scenarioCode', filters.scenarioCode);

    return this.http.get<{ content: AuditLog[]; totalElements: number }>(`${this.baseUrl}/admin/audit-logs`, { params }).pipe(
      catchError(this.handleError('getAuditLogs', { content: [], totalElements: 0 }))
    );
  }

  // Chat Sessions - all data from DB with filters
  getChatSessions(page = 0, size = 20, userId?: string, sessionId?: string): Observable<{ content: ChatSession[]; totalElements: number }> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (userId && userId.trim()) {
      params = params.set('userId', userId.trim());
    }
    if (sessionId && sessionId.trim()) {
      params = params.set('sessionId', sessionId.trim());
    }

    return this.http.get<{ content: ChatSession[]; totalElements: number }>(`${this.baseUrl}/admin/sessions`, { params }).pipe(
      catchError(this.handleError('getChatSessions', { content: [], totalElements: 0 }))
    );
  }

  // URL Whitelist - all data from DB
  getUrlWhitelist(): Observable<UrlWhitelist[]> {
    return this.http.get<UrlWhitelist[]>(`${this.baseUrl}/admin/url-whitelist`).pipe(
      catchError(this.handleError('getUrlWhitelist', []))
    );
  }

  addUrlToWhitelist(url: { urlPattern: string; description: string; allowedMethods: string }): Observable<UrlWhitelist> {
    return this.http.post<UrlWhitelist>(`${this.baseUrl}/admin/url-whitelist`, url);
  }

  removeUrlFromWhitelist(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/url-whitelist/${id}`);
  }

  // Cache Management
  refreshScenarioCache(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/admin/cache/scenarios/refresh`, {});
  }

  clearAllCache(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/admin/cache/clear`, {});
  }

  // ===================== PROMPT TEMPLATES =====================

  getPrompts(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(`${this.baseUrl}/admin/prompts`).pipe(
      catchError(this.handleError('getPrompts', []))
    );
  }

  getPromptsByCategory(category: string): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(`${this.baseUrl}/admin/prompts/category/${category}`).pipe(
      catchError(this.handleError('getPromptsByCategory', []))
    );
  }

  getPromptById(id: number): Observable<PromptTemplate> {
    return this.http.get<PromptTemplate>(`${this.baseUrl}/admin/prompts/${id}`);
  }

  getPromptByKey(promptKey: string): Observable<PromptTemplate> {
    return this.http.get<PromptTemplate>(`${this.baseUrl}/admin/prompts/key/${promptKey}`);
  }

  createPrompt(prompt: PromptFormData): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(`${this.baseUrl}/admin/prompts`, prompt);
  }

  updatePrompt(id: number, prompt: PromptFormData): Observable<PromptTemplate> {
    return this.http.put<PromptTemplate>(`${this.baseUrl}/admin/prompts/${id}`, prompt);
  }

  deletePrompt(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/prompts/${id}`);
  }

  togglePromptStatus(id: number, enabled: boolean): Observable<PromptTemplate> {
    return this.http.patch<PromptTemplate>(`${this.baseUrl}/admin/prompts/${id}/toggle`, { enabled });
  }

  rollbackPrompt(id: number, version: number): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(`${this.baseUrl}/admin/prompts/${id}/rollback/${version}`, {});
  }

  getPromptHistory(id: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/prompts/${id}/history`).pipe(
      catchError(this.handleError('getPromptHistory', []))
    );
  }

  testPrompt(id: number, testData: Record<string, any>): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/prompts/${id}/test`, testData);
  }

  // ===================== RESPONSE TEMPLATES =====================

  getResponseTemplates(): Observable<ResponseTemplate[]> {
    return this.http.get<ResponseTemplate[]>(`${this.baseUrl}/admin/response-templates`).pipe(
      catchError(this.handleError('getResponseTemplates', []))
    );
  }

  getActiveResponseTemplates(): Observable<ResponseTemplate[]> {
    return this.http.get<ResponseTemplate[]>(`${this.baseUrl}/admin/response-templates/active`).pipe(
      catchError(this.handleError('getActiveResponseTemplates', []))
    );
  }

  getResponseTemplatesByScenario(scenarioCode: string): Observable<ResponseTemplate[]> {
    return this.http.get<ResponseTemplate[]>(`${this.baseUrl}/admin/response-templates/scenario/${scenarioCode}`).pipe(
      catchError(this.handleError('getResponseTemplatesByScenario', []))
    );
  }

  getActiveResponseTemplatesByScenario(scenarioCode: string): Observable<ResponseTemplate[]> {
    return this.http.get<ResponseTemplate[]>(`${this.baseUrl}/admin/response-templates/scenario/${scenarioCode}/active`).pipe(
      catchError(this.handleError('getActiveResponseTemplatesByScenario', []))
    );
  }

  getResponseTemplateById(id: number): Observable<ResponseTemplate> {
    return this.http.get<ResponseTemplate>(`${this.baseUrl}/admin/response-templates/${id}`);
  }

  createResponseTemplate(template: ResponseTemplateFormData): Observable<ResponseTemplate> {
    return this.http.post<ResponseTemplate>(`${this.baseUrl}/admin/response-templates`, template);
  }

  updateResponseTemplate(id: number, template: ResponseTemplateFormData): Observable<ResponseTemplate> {
    return this.http.put<ResponseTemplate>(`${this.baseUrl}/admin/response-templates/${id}`, template);
  }

  deleteResponseTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/response-templates/${id}`);
  }

  toggleResponseTemplateStatus(id: number, active: boolean): Observable<ResponseTemplate> {
    return this.http.patch<ResponseTemplate>(`${this.baseUrl}/admin/response-templates/${id}/toggle`, { active });
  }

  // ===================== INTENT CONFIGURATIONS =====================
  // NOTE: IntentConfig has been REMOVED. Intent detection is now handled
  // directly by Scenario entity. All intent configuration fields (training_phrases,
  // confidence_threshold, category, etc.) are now part of Scenario.
  // Use Scenario admin methods instead (getScenarios, createScenario, etc.).

  // ===================== FOLLOW-UP GROUPS =====================

  getFollowUpGroups(): Observable<FollowUpGroup[]> {
    return this.http.get<FollowUpGroup[]>(`${this.baseUrl}/admin/followups`).pipe(
      catchError(this.handleError('getFollowUpGroups', []))
    );
  }

  getActiveFollowUpGroups(): Observable<FollowUpGroup[]> {
    return this.http.get<FollowUpGroup[]>(`${this.baseUrl}/admin/followups/active`).pipe(
      catchError(this.handleError('getActiveFollowUpGroups', []))
    );
  }

  getFollowUpGroupById(id: number): Observable<FollowUpGroup> {
    return this.http.get<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}`);
  }

  getFollowUpGroupByKey(groupKey: string): Observable<FollowUpGroup> {
    return this.http.get<FollowUpGroup>(`${this.baseUrl}/admin/followups/key/${groupKey}`);
  }

  createFollowUpGroup(group: FollowUpGroupFormData): Observable<FollowUpGroup> {
    return this.http.post<FollowUpGroup>(`${this.baseUrl}/admin/followups`, group);
  }

  updateFollowUpGroup(id: number, group: FollowUpGroupFormData): Observable<FollowUpGroup> {
    return this.http.put<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}`, group);
  }

  deleteFollowUpGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/followups/${id}`);
  }

  toggleFollowUpGroupStatus(id: number, active: boolean): Observable<FollowUpGroup> {
    return this.http.patch<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}/toggle`, { active });
  }

  addQuestionToGroup(id: number, question: FollowUpQuestion): Observable<FollowUpGroup> {
    return this.http.post<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}/questions`, question);
  }

  updateQuestionInGroup(id: number, questionKey: string, question: FollowUpQuestion): Observable<FollowUpGroup> {
    return this.http.put<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}/questions/${questionKey}`, question);
  }

  removeQuestionFromGroup(id: number, questionKey: string): Observable<FollowUpGroup> {
    return this.http.delete<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}/questions/${questionKey}`);
  }

  updateQuestionOrder(id: number, order: string[]): Observable<FollowUpGroup> {
    return this.http.put<FollowUpGroup>(`${this.baseUrl}/admin/followups/${id}/order`, order);
  }

  // ===================== POLICY RULES =====================

  getPolicies(): Observable<PolicyRule[]> {
    return this.http.get<PolicyRule[]>(`${this.baseUrl}/admin/policies`).pipe(
      catchError(this.handleError('getPolicies', []))
    );
  }

  getActivePolicies(): Observable<PolicyRule[]> {
    return this.http.get<PolicyRule[]>(`${this.baseUrl}/admin/policies/active`).pipe(
      catchError(this.handleError('getActivePolicies', []))
    );
  }

  getOrderedPolicies(): Observable<PolicyRule[]> {
    return this.http.get<PolicyRule[]>(`${this.baseUrl}/admin/policies/ordered`).pipe(
      catchError(this.handleError('getOrderedPolicies', []))
    );
  }

  getPolicyById(id: number): Observable<PolicyRule> {
    return this.http.get<PolicyRule>(`${this.baseUrl}/admin/policies/${id}`);
  }

  createPolicy(policy: PolicyFormData): Observable<PolicyRule> {
    return this.http.post<PolicyRule>(`${this.baseUrl}/admin/policies`, policy);
  }

  updatePolicy(id: number, policy: PolicyFormData): Observable<PolicyRule> {
    return this.http.put<PolicyRule>(`${this.baseUrl}/admin/policies/${id}`, policy);
  }

  deletePolicy(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/policies/${id}`);
  }

  togglePolicyStatus(id: number, active: boolean): Observable<PolicyRule> {
    return this.http.patch<PolicyRule>(`${this.baseUrl}/admin/policies/${id}/toggle`, { active });
  }

  testPolicy(id: number, testContext: Record<string, any>): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/policies/${id}/test`, testContext);
  }

  // ===================== RESPONSE MAPPINGS =====================

  getResponseMappings(scenarioCode?: string): Observable<ResponseMapping[]> {
    const params: any = {};
    if (scenarioCode) {
      params.scenarioCode = scenarioCode;
    }
    return this.http.get<ResponseMapping[]>(`${this.baseUrl}/admin/response-mappings`, { params }).pipe(
      catchError(this.handleError('getResponseMappings', []))
    );
  }

  getResponseMappingById(id: number): Observable<ResponseMapping> {
    return this.http.get<ResponseMapping>(`${this.baseUrl}/admin/response-mappings/${id}`);
  }

  getResponseMappingScenarios(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/response-mappings/scenarios`).pipe(
      catchError(this.handleError('getResponseMappingScenarios', []))
    );
  }

  createResponseMapping(mapping: ResponseMappingForm): Observable<ResponseMapping> {
    return this.http.post<ResponseMapping>(`${this.baseUrl}/admin/response-mappings`, mapping);
  }

  updateResponseMapping(id: number, mapping: ResponseMappingForm): Observable<ResponseMapping> {
    return this.http.put<ResponseMapping>(`${this.baseUrl}/admin/response-mappings/${id}`, mapping);
  }

  deleteResponseMapping(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/response-mappings/${id}`);
  }

  toggleResponseMappingStatus(id: number, active: boolean): Observable<ResponseMapping> {
    return this.http.patch<ResponseMapping>(`${this.baseUrl}/admin/response-mappings/${id}/status`, { active });
  }

  testJsonPath(request: JsonPathTestRequest): Observable<JsonPathTestResult> {
    return this.http.post<JsonPathTestResult>(`${this.baseUrl}/admin/response-mappings/test`, request);
  }

  refreshResponseMappingCache(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/admin/response-mappings/cache/refresh`, {});
  }

  deleteResponseMappingsByScenario(scenarioCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/admin/response-mappings/scenario/${scenarioCode}`);
  }

  // ===================== RBAC MANAGEMENT (ENHANCED) =====================

  getAllRbacMappings(): Observable<RoleScenarioMapping[]> {
    return this.http.get<RoleScenarioMapping[]>(`${this.baseUrl}/admin/rbac/mappings`).pipe(
      catchError(this.handleError('getAllRbacMappings', []))
    );
  }

  getRbacMappingById(id: number): Observable<RoleScenarioMapping> {
    return this.http.get<RoleScenarioMapping>(`${this.baseUrl}/admin/rbac/mappings/${id}`);
  }

  getAllRoles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/rbac/roles`).pipe(
      catchError(this.handleError('getAllRoles', []))
    );
  }

  getAllMappedScenarios(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/rbac/scenarios`).pipe(
      catchError(this.handleError('getAllMappedScenarios', []))
    );
  }

  getScenariosByRole(roleName: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/rbac/roles/${roleName}/scenarios`).pipe(
      catchError(this.handleError('getScenariosByRole', []))
    );
  }

  getRolesByScenario(scenarioCode: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/rbac/scenarios/${scenarioCode}/roles`).pipe(
      catchError(this.handleError('getRolesByScenario', []))
    );
  }

  checkRbacAccess(roleName: string, scenarioCode: string): Observable<{ hasAccess: boolean }> {
    return this.http.get<{ hasAccess: boolean }>(`${this.baseUrl}/admin/rbac/check-access`, {
      params: { roleName, scenarioCode }
    });
  }

  grantRbacAccess(roleName: string, scenarioCode: string): Observable<RoleScenarioMapping> {
    return this.http.post<RoleScenarioMapping>(`${this.baseUrl}/admin/rbac/mappings`, { roleName, scenarioCode });
  }

  revokeRbacAccess(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/rbac/mappings/${id}`);
  }

  revokeRbacAccessByRoleAndScenario(roleName: string, scenarioCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/admin/rbac/revoke`, {
      params: { roleName, scenarioCode }
    });
  }

  bulkGrantRbacAccess(request: BulkRbacRequest): Observable<RoleScenarioMapping[]> {
    return this.http.post<RoleScenarioMapping[]>(`${this.baseUrl}/admin/rbac/mappings/bulk`, request);
  }

  revokeAllFromRole(roleName: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/admin/rbac/roles/${roleName}/revoke-all`);
  }

  revokeAllFromScenario(scenarioCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/admin/rbac/scenarios/${scenarioCode}/revoke-all`);
  }

  getRbacMatrix(): Observable<RbacMatrix> {
    return this.http.get<RbacMatrix>(`${this.baseUrl}/admin/rbac/matrix`).pipe(
      catchError(this.handleError('getRbacMatrix', { roles: [], scenarios: [], mappings: [] }))
    );
  }

//   refreshRbacCache(): Observable<{ message: string }> {
//     return this.http.post<{ message: string }>(`${this.baseUrl}/admin/rbac/cache/refresh`, {});
//   }

  // RBAC Management (Legacy - keeping for backward compatibility)
  getRbacMappings(): Observable<Record<string, string[]>> {
    return this.http.get<Record<string, string[]>>(`${this.baseUrl}/admin/rbac/mappings`).pipe(
      catchError(this.handleError('getRbacMappings', {}))
    );
  }

  getRbacStatus(): Observable<{ initialized: boolean; totalRoles: number; totalMappings: number }> {
    return this.http.get<{ initialized: boolean; totalRoles: number; totalMappings: number }>(`${this.baseUrl}/admin/rbac/status`).pipe(
      catchError(this.handleError('getRbacStatus', { initialized: false, totalRoles: 0, totalMappings: 0 }))
    );
  }

  addRbacMapping(role: string, scenarioCode: string): Observable<string> {
    return this.http.post<string>(`${this.baseUrl}/admin/rbac/mappings`, { role, scenarioCode });
  }

  removeRbacMapping(role: string, scenarioCode: string): Observable<string> {
    return this.http.delete<string>(`${this.baseUrl}/admin/rbac/mappings`, {
      body: { role, scenarioCode }
    });
  }

  refreshRbacCache(): Observable<string> {
    return this.http.post<string>(`${this.baseUrl}/admin/rbac/refresh`, {});
  }

  // Analytics APIs
  getRequestsOverTime(hours: number = 24, interval: number = 4): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/analytics/requests-over-time?hours=${hours}&interval=${interval}`).pipe(
      catchError(this.handleError('getRequestsOverTime', { labels: [], data: [], period: '' }))
    );
  }

  getResponseDistribution(): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/analytics/response-distribution`).pipe(
      catchError(this.handleError('getResponseDistribution', { labels: [], data: [], average: 0 }))
    );
  }

  getSuccessRateTrend(weeks: number = 4): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/analytics/success-rate-trend?weeks=${weeks}`).pipe(
      catchError(this.handleError('getSuccessRateTrend', { labels: [], data: [], overall: 0 }))
    );
  }

  getScenarioUsage(limit: number = 5): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/analytics/scenario-usage?limit=${limit}`).pipe(
      catchError(this.handleError('getScenarioUsage', { labels: [], data: [], total: 0 }))
    );
  }

  // ===================== INTELLIGENCE ADMIN =====================

  // Fallback Layers
  getFallbackLayers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/intelligence/layers`).pipe(
      catchError(this.handleError('getFallbackLayers', []))
    );
  }

  updateFallbackLayer(id: number, layer: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/intelligence/layers/${id}`, layer).pipe(
      catchError(this.handleError('updateFallbackLayer', null))
    );
  }

  refreshLayers(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/admin/intelligence/layers/refresh`, {}).pipe(
      catchError(this.handleError('refreshLayers', { message: 'Failed to refresh layers' }))
    );
  }

  // Rules
  getRules(scenarioCode?: string): Observable<any[]> {
    const params: any = {};
    if (scenarioCode) params.scenarioCode = scenarioCode;
    return this.http.get<any[]>(`${this.baseUrl}/admin/intelligence/rules`, { params }).pipe(
      catchError(this.handleError('getRules', []))
    );
  }

  createRule(rule: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/intelligence/rules`, rule);
  }

  updateRule(id: number, rule: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/intelligence/rules/${id}`, rule);
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/intelligence/rules/${id}`);
  }

  // Keywords
  getKeywords(scenarioCode?: string): Observable<any[]> {
    const params: any = {};
    if (scenarioCode) params.scenarioCode = scenarioCode;
    return this.http.get<any[]>(`${this.baseUrl}/admin/intelligence/keywords`, { params }).pipe(
      catchError(this.handleError('getKeywords', []))
    );
  }

  createKeyword(keyword: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/intelligence/keywords`, keyword);
  }

  updateKeyword(id: number, keyword: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/intelligence/keywords/${id}`, keyword);
  }

  deleteKeyword(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/intelligence/keywords/${id}`);
  }

  // Embeddings
  getEmbeddings(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/admin/intelligence/embeddings`).pipe(
      catchError(this.handleError('getEmbeddings', []))
    );
  }

  generateEmbedding(scenarioCode: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/intelligence/embeddings/${scenarioCode}`, {});
  }

  generateAllEmbeddings(): Observable<{ message: string; count: number }> {
    return this.http.post<{ message: string; count: number }>(`${this.baseUrl}/admin/intelligence/embeddings/generate-all`, {});
  }

  refreshEmbedding(scenarioCode: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/intelligence/embeddings/${scenarioCode}/refresh`, {});
  }

  // Entity Patterns
  getEntityPatterns(entityType?: string): Observable<any[]> {
    const params: any = {};
    if (entityType) params.entityType = entityType;
    return this.http.get<any[]>(`${this.baseUrl}/admin/entity-patterns`, { params }).pipe(
      catchError(this.handleError('getEntityPatterns', []))
    );
  }

  getEntityPattern(id: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/admin/entity-patterns/${id}`);
  }

  createEntityPattern(pattern: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/admin/entity-patterns`, pattern);
  }

  updateEntityPattern(id: number, pattern: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/admin/entity-patterns/${id}`, pattern);
  }

  deleteEntityPattern(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/entity-patterns/${id}`);
  }

  // Context Memory
  getContextMemory(sessionId?: string, entityType?: string): Observable<any[]> {
    const params: any = {};
    if (sessionId) params.sessionId = sessionId;
    if (entityType) params.entityType = entityType;
    return this.http.get<any[]>(`${this.baseUrl}/admin/context-memory`, { params }).pipe(
      catchError(this.handleError('getContextMemory', []))
    );
  }

  clearContextMemory(sessionId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/admin/context-memory/session/${sessionId}`);
  }

  cleanupOldMemory(daysOld: number = 7): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/admin/context-memory/cleanup`, {}, {
      params: { daysOld: daysOld.toString() }
    });
  }
}
