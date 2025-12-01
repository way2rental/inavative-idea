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
}
