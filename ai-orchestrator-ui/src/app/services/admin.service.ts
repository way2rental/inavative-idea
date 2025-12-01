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
  OllamaStatus,
  PromptTemplate,
  PromptFormData,
  IntentConfig,
  IntentFormData,
  FollowUpGroup,
  FollowUpGroupFormData,
  FollowUpQuestion,
  PolicyRule,
  PolicyFormData
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

  // Ollama Status - fetches from backend health check
  getOllamaStatus(): Observable<OllamaStatus> {
    return this.http.get<OllamaStatus>(`${this.baseUrl}/ollama/health`).pipe(
      catchError(this.handleError('getOllamaStatus', {
        connected: false,
        baseUrl: '',
        model: '',
        enabled: false,
        lastCheckTime: new Date().toISOString()
      }))
    );
  }

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

  // Chat Sessions - all data from DB
  getChatSessions(page = 0, size = 20): Observable<{ content: ChatSession[]; totalElements: number }> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
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

  // ===================== INTENT CONFIGURATIONS =====================

  getIntents(): Observable<IntentConfig[]> {
    return this.http.get<IntentConfig[]>(`${this.baseUrl}/admin/intents`).pipe(
      catchError(this.handleError('getIntents', []))
    );
  }

  getActiveIntents(): Observable<IntentConfig[]> {
    return this.http.get<IntentConfig[]>(`${this.baseUrl}/admin/intents/active`).pipe(
      catchError(this.handleError('getActiveIntents', []))
    );
  }

  getIntentsByCategory(category: string): Observable<IntentConfig[]> {
    return this.http.get<IntentConfig[]>(`${this.baseUrl}/admin/intents/category/${category}`).pipe(
      catchError(this.handleError('getIntentsByCategory', []))
    );
  }

  getIntentById(id: number): Observable<IntentConfig> {
    return this.http.get<IntentConfig>(`${this.baseUrl}/admin/intents/${id}`);
  }

  createIntent(intent: IntentFormData): Observable<IntentConfig> {
    return this.http.post<IntentConfig>(`${this.baseUrl}/admin/intents`, intent);
  }

  updateIntent(id: number, intent: IntentFormData): Observable<IntentConfig> {
    return this.http.put<IntentConfig>(`${this.baseUrl}/admin/intents/${id}`, intent);
  }

  deleteIntent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/intents/${id}`);
  }

  toggleIntentStatus(id: number, active: boolean): Observable<IntentConfig> {
    return this.http.patch<IntentConfig>(`${this.baseUrl}/admin/intents/${id}/toggle`, { active });
  }

  addTrainingPhrases(id: number, phrases: string[]): Observable<IntentConfig> {
    return this.http.post<IntentConfig>(`${this.baseUrl}/admin/intents/${id}/training-phrases`, phrases);
  }

  removeTrainingPhrases(id: number, phrases: string[]): Observable<IntentConfig> {
    return this.http.request<IntentConfig>('DELETE', `${this.baseUrl}/admin/intents/${id}/training-phrases`, { body: phrases });
  }

  getIntentCategories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/intents/categories`).pipe(
      catchError(this.handleError('getIntentCategories', []))
    );
  }

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
}
