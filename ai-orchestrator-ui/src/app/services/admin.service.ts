import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
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

  // Dashboard Stats
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/admin/dashboard/stats`).pipe(
      catchError(() => of({
        totalScenarios: 3,
        activeScenarios: 3,
        totalSessions: 127,
        todayRequests: 45,
        avgResponseTime: 234,
        successRate: 98.5
      }))
    );
  }

  // Performance Metrics
  getPerformanceMetrics(): Observable<PerformanceMetrics> {
    return this.http.get<PerformanceMetrics>(`${this.baseUrl}/v2/chat/metrics`).pipe(
      catchError(() => of({
        avgIntentDetectionMs: 89,
        avgDbExecutionMs: 12,
        avgFormattingMs: 45,
        avgTotalMs: 234,
        p95ResponseTime: 450,
        p99ResponseTime: 780,
        requestsPerMinute: 2.3
      }))
    );
  }

  // Ollama Status
  getOllamaStatus(): Observable<OllamaStatus> {
    return this.http.get<OllamaStatus>(`${this.baseUrl}/ollama/health`).pipe(
      catchError(() => of({
        connected: false,
        baseUrl: 'http://localhost:11434',
        model: 'llama3:8b',
        enabled: true,
        lastCheckTime: new Date().toISOString()
      }))
    );
  }

  // Scenarios CRUD
  getScenarios(): Observable<Scenario[]> {
    return this.http.get<Scenario[]>(`${this.baseUrl}/admin/scenarios`).pipe(
      catchError(() => of([
        {
          id: 1,
          scenarioCode: 'TXN_STATUS',
          scenarioName: 'Transaction Status',
          description: 'Check the status of a transaction by transaction ID',
          requiredParams: ['transactionId'],
          securityLevel: 'NORMAL',
          executionType: 'DB_QUERY' as const,
          sqlQuery: 'SELECT * FROM transactions WHERE txn_id = :transactionId',
          promptVersion: 1,
          active: true,
          createdAt: '2024-01-15T10:00:00Z',
          updatedAt: '2024-01-15T10:00:00Z'
        },
        {
          id: 2,
          scenarioCode: 'FILE_STATUS',
          scenarioName: 'File Processing Status',
          description: 'Check the processing status of uploaded files',
          requiredParams: ['fileName'],
          securityLevel: 'NORMAL',
          executionType: 'DB_QUERY' as const,
          sqlQuery: 'SELECT * FROM file_uploads WHERE file_name = :fileName',
          promptVersion: 1,
          active: true,
          createdAt: '2024-01-15T10:00:00Z',
          updatedAt: '2024-01-15T10:00:00Z'
        },
        {
          id: 3,
          scenarioCode: 'ACCOUNT_SUMMARY',
          scenarioName: 'Account Summary',
          description: 'Get account summary and balance information',
          requiredParams: ['accountId'],
          securityLevel: 'SENSITIVE',
          executionType: 'HTTP_CALL' as const,
          httpMethod: 'GET',
          httpUrl: '/api/accounts/{accountId}/summary',
          promptVersion: 1,
          active: true,
          createdAt: '2024-01-15T10:00:00Z',
          updatedAt: '2024-01-15T10:00:00Z'
        }
      ]))
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

  // Audit Logs
  getAuditLogs(page = 0, size = 20, filters?: { userId?: string; scenarioCode?: string }): Observable<{ content: AuditLog[]; totalElements: number }> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (filters?.userId) params = params.set('userId', filters.userId);
    if (filters?.scenarioCode) params = params.set('scenarioCode', filters.scenarioCode);
    
    return this.http.get<{ content: AuditLog[]; totalElements: number }>(`${this.baseUrl}/admin/audit-logs`, { params }).pipe(
      catchError(() => of({
        content: [
          {
            id: 1,
            sessionId: 'sess-001',
            userId: 'john.doe',
            scenarioCode: 'TXN_STATUS',
            userQuery: 'What is the status of TXN123?',
            detectedIntent: 'TXN_STATUS',
            confidence: 0.95,
            paramsExtracted: '{"transactionId": "TXN123"}',
            responseGenerated: 'Transaction TXN123 is completed successfully.',
            executionTimeMs: 234,
            createdAt: new Date().toISOString()
          },
          {
            id: 2,
            sessionId: 'sess-002',
            userId: 'jane.smith',
            scenarioCode: 'ACCOUNT_SUMMARY',
            userQuery: 'Show my account balance',
            detectedIntent: 'ACCOUNT_SUMMARY',
            confidence: 0.88,
            paramsExtracted: '{"accountId": "ACC456"}',
            responseGenerated: 'Your account balance is ₹50,000.',
            executionTimeMs: 312,
            createdAt: new Date().toISOString()
          }
        ],
        totalElements: 2
      }))
    );
  }

  // Chat Sessions
  getChatSessions(page = 0, size = 20): Observable<{ content: ChatSession[]; totalElements: number }> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<{ content: ChatSession[]; totalElements: number }>(`${this.baseUrl}/admin/sessions`, { params }).pipe(
      catchError(() => of({
        content: [
          {
            id: 1,
            sessionId: 'sess-001',
            userId: 'john.doe',
            startTime: new Date(Date.now() - 3600000).toISOString(),
            lastActivityTime: new Date().toISOString(),
            messageCount: 5,
            active: true
          },
          {
            id: 2,
            sessionId: 'sess-002',
            userId: 'jane.smith',
            startTime: new Date(Date.now() - 7200000).toISOString(),
            lastActivityTime: new Date(Date.now() - 1800000).toISOString(),
            messageCount: 3,
            active: false
          }
        ],
        totalElements: 2
      }))
    );
  }

  // URL Whitelist
  getUrlWhitelist(): Observable<UrlWhitelist[]> {
    return this.http.get<UrlWhitelist[]>(`${this.baseUrl}/admin/url-whitelist`).pipe(
      catchError(() => of([
        {
          id: 1,
          urlPattern: '/api/accounts/*',
          description: 'Account service endpoints',
          allowedMethods: 'GET,POST',
          active: true,
          createdAt: '2024-01-15T10:00:00Z'
        },
        {
          id: 2,
          urlPattern: '/api/transactions/*',
          description: 'Transaction service endpoints',
          allowedMethods: 'GET',
          active: true,
          createdAt: '2024-01-15T10:00:00Z'
        }
      ]))
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
