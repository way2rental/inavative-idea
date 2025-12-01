export interface ChatRequest {
  userId: string;
  query: string;
  sessionId?: string;
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

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  isLoading?: boolean;
  isStreaming?: boolean;
  statusMessages?: string[];
  isError?: boolean;         // CHUNK 3: Flag for error messages
  followUp?: FollowUpData;   // CHUNK 3: Follow-up question data
  suggestions?: string[];    // CHUNK 3: Suggestions for unknown scenarios
}

// CHUNK 3: Follow-up event payload
export interface FollowUpData {
  scenario: string;
  missingParams: string[];
  question: string;
}

// CHUNK 3: SSE Event structure
export interface SSEEvent {
  event: 'start' | 'message' | 'done' | 'error' | 'followup' | 'unknown';
  data: string;
}
