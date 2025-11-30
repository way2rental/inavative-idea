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
  OllamaStatus
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

  // RBAC Management
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
}
