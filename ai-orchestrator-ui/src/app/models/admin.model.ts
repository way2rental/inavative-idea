export interface Scenario {
  id: number;
  scenarioCode: string;
  scenarioName: string;
  description: string;
  executionType: 'DB_QUERY' | 'HTTP_CALL' | 'FILE_READ' | 'KAFKA_CONSUME' | 'COMPOSITE';
  httpMethod?: string;
  httpUrl?: string;
  httpHeaders?: string;
  sqlQuery?: string;
  requestMapping?: string;
  responseMapping?: string;
  timeoutMs?: number;
  executorBean?: string;
  securityLevel: string;
  requiredParams: string[];
  optionalParams?: string[];
  llmPromptTemplate?: string;
  promptVersion: number;
  promptHistory?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ScenarioFormData {
  scenarioCode: string;
  scenarioName: string;
  description: string;
  executionType: 'DB_QUERY' | 'HTTP_CALL' | 'FILE_READ' | 'KAFKA_CONSUME' | 'COMPOSITE';
  httpMethod?: string;
  httpUrl?: string;
  httpHeaders?: string;
  sqlQuery?: string;
  requestMapping?: string;
  responseMapping?: string;
  timeoutMs?: number;
  executorBean?: string;
  securityLevel: string;
  requiredParams: string;
  optionalParams?: string;
  llmPromptTemplate?: string;
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

// ============================================
// ACTUATOR & SYSTEM MONITORING MODELS
// ============================================

export interface ActuatorHealth {
  status: string;
  components?: {
    [key: string]: {
      status: string;
      details?: any;
    };
  };
}

export interface ActuatorInfo {
  app?: {
    name?: string;
    version?: string;
    description?: string;
  };
  java?: {
    version?: string;
    vendor?: string;
    runtime?: string;
  };
  os?: {
    name?: string;
    version?: string;
    arch?: string;
  };
}

export interface ActuatorMetrics {
  names: string[];
}

export interface ActuatorMetricValue {
  name: string;
  measurements: Array<{
    statistic: string;
    value: number;
  }>;
  availableTags: Array<{
    tag: string;
    values: string[];
  }>;
}

export interface SystemInfo {
  jvm: {
    memory: {
      used: number;
      max: number;
      percentage: number;
    };
    threads: number;
    uptime: number;
  };
  system: {
    cpuCount: number;
    osName: string;
    osVersion: string;
  };
}

export interface CircuitBreakerInfo {
  name: string;
  state: string;
  failureRate: number;
  slowCallRate: number;
  bufferedCalls: number;
  failedCalls: number;
  successfulCalls: number;
}

export interface RateLimiterInfo {
  name: string;
  availablePermissions: number;
  numberOfWaitingThreads: number;
}
