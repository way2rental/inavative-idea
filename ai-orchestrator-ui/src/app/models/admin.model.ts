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
  requiredParams: string[];
  llmPromptTemplate?: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
  // AI Intent Detection fields (NO HARDCODING)
  triggerPhrases?: string;
  exampleQueries?: string;
  category?: string;
  displayOrder?: number;
  icon?: string;
  // Multi-filter engine fields
  filterDefinitions?: string;
  securityFilters?: string;
  maxResults?: number;
  defaultSort?: string;
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
  requiredParams: string;
  llmPromptTemplate?: string;
  active: boolean;
  // AI Intent Detection fields (NO HARDCODING)
  triggerPhrases?: string;
  exampleQueries?: string;
  category?: string;
  displayOrder?: number;
  icon?: string;
  // Multi-filter engine fields
  filterDefinitions?: string;
  securityFilters?: string;
  maxResults?: number;
  defaultSort?: string;
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
  addedBy?: string;
  createdAt: string;
  updatedAt?: string;
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

// ===================== RESPONSE MAPPINGS =====================

export interface ResponseMapping {
  id: number;
  scenarioCode: string;
  sourceType: string;
  sourceField?: string;
  targetField: string;
  jsonPath: string;
  maskingType: string;
  displayOrder: number;
  active: boolean;
}

export interface ResponseMappingForm {
  scenarioCode: string;
  sourceType: string;
  sourceField?: string;
  targetField: string;
  jsonPath: string;
  maskingType: string;
  displayOrder: number;
  active: boolean;
}

export interface JsonPathTestRequest {
  jsonPath: string;
  sampleJson: string;
}

export interface JsonPathTestResult {
  success: boolean;
  result?: any;
  error?: string;
  expression: string;
}

// ===================== RBAC MANAGEMENT =====================

export interface RoleScenarioMapping {
  id: number;
  roleName: string;
  scenarioCode: string;
}

export interface RbacMatrix {
  roles: string[];
  scenarios: string[];
  mappings: RoleScenarioMapping[];
}

export interface BulkRbacRequest {
  roleName: string;
  scenarioCodes: string[];
}

// ===================== PROMPT TEMPLATES =====================

export interface PromptTemplate {
  id: number;
  promptKey: string;
  category: string;
  systemPrompt: string;
  userTemplate: string;
  responseFormat: string;
  temperature: number;
  maxTokens: number;
  version: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  historyCount: number;
}

export interface PromptFormData {
  promptKey: string;
  category: string;
  systemPrompt: string;
  userTemplate: string;
  responseFormat: string;
  temperature: number;
  maxTokens: number;
  enabled: boolean;
  createdBy?: string;
  updatedBy?: string;
}

// ===================== RESPONSE TEMPLATES =====================

export interface ResponseTemplate {
  id: number;
  scenarioCode: string;
  scenarioName?: string;
  responseType: string;
  templateContent: string;
  templateVariables?: string;
  conditions?: string;
  priority: number;
  templateVersion: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ResponseTemplateFormData {
  scenarioCode: string;
  responseType: string;
  templateContent: string;
  templateVariables?: string;
  conditions?: string;
  priority: number;
  templateVersion?: number;
  active: boolean;
}

// ===================== INTENT CONFIGURATIONS =====================
// NOTE: IntentConfig has been REMOVED and consolidated into Scenario.
// All intent configuration is now part of Scenario entity:
// - intentKey → scenarioCode
// - intentName → scenarioName
// - trainingPhrases → triggerPhrases / exampleQueries
// - confidenceThreshold → confidenceThreshold (added to Scenario)
// - followupGroup → followupGroup (added to Scenario)
// - category → category
// - priority → displayOrder

// ===================== FOLLOW-UP GROUPS =====================

export interface FollowUpQuestion {
  key: string;
  question: string;
  type: 'string' | 'date' | 'number' | 'boolean';
  required: boolean;
  validationPattern?: string;
  placeholder?: string;
  defaultValue?: string;
}

export interface FollowUpGroup {
  id: number;
  groupKey: string;
  description: string;
  scenarioCodes: string[];
  questions: FollowUpQuestion[];
  questionOrder: string[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export interface FollowUpGroupFormData {
  groupKey: string;
  description: string;
  scenarioCodes: string[];
  questions: FollowUpQuestion[];
  questionOrder: string[];
  active: boolean;
  createdBy?: string;
}

// ===================== POLICY RULES =====================

export interface PolicyRule {
  id: number;
  policyKey: string;
  policyName: string;
  description: string;
  ruleExpression: string;
  onFail: 'BLOCK' | 'WARN' | 'LOG';
  failureMessage: string;
  applicableScenarios: string[];
  applicableRoles: string[];
  priority: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export interface PolicyFormData {
  policyKey: string;
  policyName: string;
  description: string;
  ruleExpression: string;
  onFail: 'BLOCK' | 'WARN' | 'LOG';
  failureMessage: string;
  applicableScenarios: string[];
  applicableRoles: string[];
  priority: number;
  active: boolean;
  createdBy?: string;
}
