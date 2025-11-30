import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // Dashboard Stats - fetches from DB via backend
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/admin/dashboard/stats`).pipe(
      catchError(error => {
        console.error('Failed to load dashboard stats:', error);
        // Return empty stats instead of hardcoded data
        return of({
          totalScenarios: 0,
          activeScenarios: 0,
          totalSessions: 0,
          todayRequests: 0,
          avgResponseTime: 0,
          successRate: 0
        });
      })
    );
  }

  // Performance Metrics - fetches from DB via backend
  getPerformanceMetrics(): Observable<PerformanceMetrics> {
    return this.http.get<PerformanceMetrics>(`${this.baseUrl}/v2/chat/metrics`).pipe(
      catchError(error => {
        console.error('Failed to load performance metrics:', error);
        // Return empty metrics instead of hardcoded data
        return of({
          avgIntentDetectionMs: 0,
          avgDbExecutionMs: 0,
          avgFormattingMs: 0,
          avgTotalMs: 0,
          p95ResponseTime: 0,
          p99ResponseTime: 0,
          requestsPerMinute: 0
        });
      })
    );
  }

  // Ollama Status - fetches from backend health check
  getOllamaStatus(): Observable<OllamaStatus> {
    return this.http.get<OllamaStatus>(`${this.baseUrl}/ollama/health`).pipe(
      catchError(error => {
        console.error('Failed to load Ollama status:', error);
        // Return disconnected status instead of hardcoded data
        return of({
          connected: false,
          baseUrl: '',
          model: '',
          enabled: false,
          lastCheckTime: new Date().toISOString()
        });
      })
    );
  }

  // Scenarios CRUD - all data from DB
  getScenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>(`${this.baseUrl}/admin/scenarios`).pipe(
      catchError(error => {
        console.error('Failed to load scenarios:', error);
        return of([]); // Return empty array instead of hardcoded scenarios
      })
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
      catchError(error => {
        console.error('Failed to load audit logs:', error);
        return of({ content: [], totalElements: 0 }); // Return empty array instead of hardcoded logs
      })
    );
  }

  // Chat Sessions - all data from DB
  getChatSessions(page = 0, size = 20): Observable<{ content: ChatSession[]; totalElements: number }> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<{ content: ChatSession[]; totalElements: number }>(`${this.baseUrl}/admin/sessions`, { params }).pipe(
      catchError(error => {
        console.error('Failed to load chat sessions:', error);
        return of({ content: [], totalElements: 0 }); // Return empty array instead of hardcoded sessions
      })
    );
  }

  // URL Whitelist - all data from DB
  getUrlWhitelist(): Observable<UrlWhitelist[]> {
    return this.http.get<UrlWhitelist[]>(`${this.baseUrl}/admin/url-whitelist`).pipe(
      catchError(error => {
        console.error('Failed to load URL whitelist:', error);
        return of([]); // Return empty array instead of hardcoded URLs
      })
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
}
