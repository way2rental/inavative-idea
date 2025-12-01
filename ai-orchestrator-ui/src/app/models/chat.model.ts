/**
 * Request type for chat messages.
 * MUST match backend ChatRequest.RequestType enum exactly.
 */
export type RequestType = 'QUERY' | 'CONFIRMATION' | 'CLARIFICATION';

/**
 * Chat request contract - MUST match backend ChatRequest.java exactly.
 * All optional fields MUST be sent during follow-up interactions.
 */
export interface ChatRequest {
  userId: string;
  query: string;
  sessionId?: string;
  // Follow-up flow fields (MANDATORY for confirmation/clarification)
  requestType?: RequestType;
  confirmed?: boolean;
  selectedOption?: number;
  pendingActionParams?: { [key: string]: any };
  pendingScenario?: string;
  dryRun?: boolean;
}

export interface ChatResponse {
  sessionId: string;
  message: string;
  followUpRequired: boolean;
  missingParams?: string[];
  scenario?: string;
  meta?: ResponseMeta;
}

export interface ResponseMeta {
  sources?: string[];
  executionId?: string;
  additionalInfo?: { [key: string]: any };
}

// ============================================
// STRUCTURED CHAT RESPONSE (per STRUCTURED_CHAT_RESPONSE_UPGRADE.md)
// ============================================

/**
 * Response types per spec Section 4.
 * Each type determines UI rendering behavior.
 */
export type StructuredResponseType = 'TEXT' | 'BULLET' | 'TABLE' | 'KV' | 'MIXED' | 'FOLLOW_UP' | 'ERROR';

/**
 * Structured response contract per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 3.
 * All final AI responses MUST follow this structure.
 */
export interface StructuredResponse {
  type: StructuredResponseType;
  title?: string;
  confidence?: number;
  payload: any;
  footer?: string;
  sessionId?: string;
  scenario?: string;
}

/**
 * TEXT payload: Simple paragraph message
 */
export interface TextPayload {
  message: string;
}

/**
 * BULLET payload: List of items
 */
export interface BulletPayload {
  items: string[];
}

/**
 * KV payload: Key-value pairs
 */
export interface KvPayload {
  [key: string]: string | number;
}

/**
 * TABLE payload: Columns and rows
 */
export interface TablePayload {
  columns: string[];
  rows: string[][];
}

/**
 * MIXED payload: Text + table combination
 */
export interface MixedPayload {
  text: string;
  table: TablePayload;
}

/**
 * FOLLOW_UP payload: Missing parameters and question
 */
export interface FollowUpPayload {
  missingParams: string[];
  question: string;
}

/**
 * ERROR payload: Error message and suggestions
 */
export interface ErrorPayload {
  message: string;
  suggestions: string[];
}

/**
 * Chat message model per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 6.1.
 *
 * Key changes:
 * - content: used ONLY for streaming status messages
 * - structured: final response (rendered by type)
 * - isStreaming: indicates status phase
 */
export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content?: string;              // Only for streaming status messages
  structured?: StructuredResponse; // Final structured response
  timestamp: Date;
  isLoading?: boolean;
  isStreaming?: boolean;
  statusMessages?: string[];
  isError?: boolean;
  followUp?: FollowUpData;
  suggestions?: string[];
  // Context for follow-up responses
  pendingContext?: {
    scenario: string;
    params: { [key: string]: any };
    missingParams: string[];
  };
}

// Legacy support: Follow-up event payload
export interface FollowUpData {
  scenario: string;
  missingParams: string[];
  question: string;
}

// SSE Event structure
export interface SSEEvent {
  event: 'start' | 'message' | 'progress' | 'response' | 'done' | 'error' | 'followup' | 'unknown';
  data: string;
}
