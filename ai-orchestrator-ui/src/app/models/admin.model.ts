export interface Scenario {
  id: number;
  scenarioCode: string;
  scenarioName: string;
  description: string;
  intentPrompt?: string;
  responseTemplate?: string;
  requiredParams: string[];
  securityLevel: string;
  executionType: 'DB_QUERY' | 'HTTP_CALL' | 'COMPOSITE';
  httpMethod?: string;
  httpUrl?: string;
  sqlQuery?: string;
  requestMapping?: string;
  responseMapping?: string;
  promptVersion: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ScenarioFormData {
  scenarioCode: string;
  scenarioName: string;
  description: string;
  intentPrompt?: string;
  responseTemplate?: string;
  requiredParams: string;
  securityLevel: string;
  executionType: 'DB_QUERY' | 'HTTP_CALL' | 'COMPOSITE';
  httpMethod?: string;
  httpUrl?: string;
  sqlQuery?: string;
  requestMapping?: string;
  responseMapping?: string;
  active: boolean;
}

export interface AuditLog {
  id: number;
  sessionId: string;
  userId: string;
  scenarioCode: string;
  userQuery: string;
  detectedIntent: string;
  confidence: number;
  paramsExtracted: string;
  responseGenerated: string;
  executionTimeMs: number;
  errorDetails?: string;
  createdAt: string;
}

export interface ChatSession {
  id: number;
  sessionId: string;
  userId: string;
  startTime: string;
  lastActivityTime: string;
  messageCount: number;
  active: boolean;
}

export interface UrlWhitelist {
  id: number;
  urlPattern: string;
  description: string;
  allowedMethods: string;
  active: boolean;
  createdAt: string;
}

export interface ScenarioTestRequest {
  scenarioCode: string;
  testParams: Record<string, string>;
  dryRun: boolean;
}

export interface ScenarioTestResult {
  id: number;
  scenarioCode: string;
  testParams: string;
  testResult: string;
  success: boolean;
  executionTimeMs: number;
  errorMessage?: string;
  testedAt: string;
  testedBy: string;
}

export interface DashboardStats {
  totalScenarios: number;
  activeScenarios: number;
  totalSessions: number;
  todayRequests: number;
  avgResponseTime: number;
  successRate: number;
}

export interface PerformanceMetrics {
  avgIntentDetectionMs: number;
  avgDbExecutionMs: number;
  avgFormattingMs: number;
  avgTotalMs: number;
  p95ResponseTime: number;
  p99ResponseTime: number;
  requestsPerMinute: number;
}

export interface OllamaStatus {
  connected: boolean;
  baseUrl: string;
  model: string;
  enabled: boolean;
  lastCheckTime: string;
}
