import { Component, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ChatMessage, ChatRequest, ChatResponse, StructuredResponse } from '../../models/chat.model';
import {
  ChatTextComponent,
  ChatBulletComponent,
  ChatKvComponent,
  ChatTableComponent,
  ChatFollowUpComponent,
  ChatErrorComponent,
  ChatMixedComponent
} from './renderers';

// CHUNK 3: Status message emoji patterns for intermediate status detection
const STATUS_EMOJI_PATTERNS = ['🔍', '📊', '📈', '💾', '✅', '❌', '⚠️', '🔐'];

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    // Structured response renderers per STRUCTURED_CHAT_RESPONSE_UPGRADE.md
    ChatTextComponent,
    ChatBulletComponent,
    ChatKvComponent,
    ChatTableComponent,
    ChatFollowUpComponent,
    ChatErrorComponent,
    ChatMixedComponent
  ],
  templateUrl: './chat.component.html'
})
export class ChatComponent implements AfterViewChecked {
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  messages: ChatMessage[] = [];
  inputMessage = '';
  sessionId: string | null = null;
  isLoading = false;
  isStreaming = false;
  streamingContent = '';
  showSidebar = true;
  isChatOpen = true; // Chat widget open by default

  // Pending context for follow-up responses
  private pendingContext: {
    scenario: string;
    params: { [key: string]: any };
    missingParams: string[];
  } | null = null;

  // Quick actions for Corporate Banking - matches database scenarios
  quickActions = [
    { icon: '💰', label: 'Account Balance', query: 'Check account balance for account ' },
    { icon: '📊', label: 'Account Summary', query: 'Show me account summary of account ' },
    { icon: '📜', label: 'Transaction History', query: 'Show transaction history for account ' },
    { icon: '💳', label: 'Card Details', query: 'Show card details for account ' },
    { icon: '🏦', label: 'Loan Status', query: 'Check loan status for account ' },
    { icon: '📈', label: 'Spending Analysis', query: 'Analyze spending for account ' },
    { icon: '📂', label: 'Investment Portfolio', query: 'Show investment portfolio' },
    { icon: '🎯', label: 'Credit Score', query: 'Check credit score for user ' }
  ];

  constructor(
    private apiService: ApiService,
    private authService: AuthService
  ) {}

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch(err) {}
  }

  toggleChat(): void {
    this.isChatOpen = !this.isChatOpen;
  }

  // Insert query into input field instead of sending directly
  insertQuery(query: string): void {
    this.inputMessage = query;
    // Focus the input field
    setTimeout(() => {
      const inputElement = document.querySelector('input[type="text"]') as HTMLInputElement;
      if (inputElement) {
        inputElement.focus();
        // Move cursor to end
        inputElement.setSelectionRange(inputElement.value.length, inputElement.value.length);
      }
    }, 100);
  }

  sendMessage(query?: string): void {
    const message = query || this.inputMessage;
    if (!message.trim() || this.isLoading) return;

    const userMessage: ChatMessage = {
      role: 'user',
      content: message,
      timestamp: new Date()
    };
    this.messages.push(userMessage);

    // Check if we have pending context (previous FOLLOW_UP)
    let enhancedQuery = message;
    if (this.pendingContext) {
      // If user provided just a value (likely the missing param), enhance the query
      const firstMissingParam = this.pendingContext.missingParams[0];
      if (firstMissingParam && !message.toLowerCase().includes(this.pendingContext.scenario.toLowerCase())) {
        // User likely provided just the missing value, reconstruct intent
        enhancedQuery = `${this.pendingContext.scenario.replace(/_/g, ' ').toLowerCase()} for ${firstMissingParam} ${message}`;
        console.log('[Context] Enhanced query:', enhancedQuery);
      }
    }

    const request: ChatRequest = {
      userId: this.authService.getCurrentUser()?.username || 'anonymous',
      query: enhancedQuery,
      sessionId: this.sessionId || undefined
    };

    this.inputMessage = '';
    this.isLoading = true;

    // Try streaming first
    this.streamMessage(request);
  }

  streamMessage(request: ChatRequest): void {
    this.isStreaming = true;
    this.streamingContent = '';

    const assistantMessage: ChatMessage = {
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isLoading: true,
      isStreaming: true,
      statusMessages: []
    };
    this.messages.push(assistantMessage);

    let isCollectingFinalResponse = false;
    let finalResponseBuffer = '';
    let hasReceivedStart = false;
    let hasReceivedDone = false;

    // STRUCTURED_CHAT_RESPONSE_UPGRADE: Handle proper event types including 'response' and 'progress'
    this.apiService.chatStream(request).subscribe({
      next: (event: { event: string; data: string }) => {
        const { event: eventType, data: chunk } = event;

        // Handle different SSE event types per STRUCTURED_CHAT_RESPONSE_UPGRADE.md
        switch (eventType) {
          case 'start':
            // Stream initialization - show processing indicator
            hasReceivedStart = true;
            assistantMessage.statusMessages = [chunk || 'Processing your request...'];
            assistantMessage.isLoading = true;
            break;

          case 'progress':
            // Progress/status message (Phase 1 per spec)
            const progressMessage = chunk.trim();
            if (progressMessage) {
              assistantMessage.statusMessages = [progressMessage];
            }
            break;

          case 'response':
            // STRUCTURED_CHAT_RESPONSE_UPGRADE: Final structured JSON response (Phase 2)
            // This is the single JSON chunk containing the complete structured response
            try {
              console.log('[SSE] Received response event, chunk length:', chunk.length);
              console.log('[SSE] Response chunk preview:', chunk.substring(0, 100));

              const structuredResponse: StructuredResponse = JSON.parse(chunk);
              console.log('[SSE] Parsed structured response, type:', structuredResponse.type);

              assistantMessage.structured = structuredResponse;
              assistantMessage.content = undefined; // Clear content, use structured
              assistantMessage.statusMessages = [];
              assistantMessage.isLoading = false;
              assistantMessage.isStreaming = false;
              isCollectingFinalResponse = true;

              console.log('[SSE] Message updated with structured response');
            } catch (e) {
              console.error('[SSE] Failed to parse structured response:', e);
              console.error('[SSE] Raw chunk:', chunk);
              // Fallback to text content
              assistantMessage.content = chunk;
              assistantMessage.isLoading = false;
            }
            break;

          case 'message':
            // Regular message token - accumulate for final response (legacy support)
            // Check if this is a status message (starts with emoji) - use constant
            const isStatusMessage = STATUS_EMOJI_PATTERNS.some(emoji => chunk.trim().startsWith(emoji));

            if (isStatusMessage && !isCollectingFinalResponse) {
              // This is an intermediate status message
              const trimmedChunk = chunk.trim();
              if (trimmedChunk) {
                assistantMessage.statusMessages = [trimmedChunk];
                assistantMessage.content = '';
              }
            } else {
              // This is part of the final response
              isCollectingFinalResponse = true;
              assistantMessage.statusMessages = [];
              finalResponseBuffer += chunk;

              // Try to extract JSON from buffer (may have text prefix/suffix)
              const jsonMatch = finalResponseBuffer.match(/\{[\s\S]*\}/);
              if (jsonMatch) {
                try {
                  const structuredResponse: StructuredResponse = JSON.parse(jsonMatch[0]);
                  // Successfully parsed as JSON - treat as structured response
                  assistantMessage.structured = structuredResponse;
                  assistantMessage.content = undefined;
                  assistantMessage.isLoading = false;
                  console.log('[SSE] Parsed structured response:', structuredResponse.type);

                  // If this is a FOLLOW_UP, save the context for next message
                  if (structuredResponse.type === 'FOLLOW_UP') {
                    const payload = structuredResponse.payload as any;
                    this.pendingContext = {
                      scenario: structuredResponse.scenario || '',
                      params: {}, // Will be filled when user provides values
                      missingParams: payload.missingParams || []
                    };
                    assistantMessage.pendingContext = this.pendingContext;
                    console.log('[Context] Saved pending context:', this.pendingContext);
                  } else {
                    // Clear pending context on successful response
                    this.pendingContext = null;
                  }
                } catch (e) {
                  // Not valid JSON yet or malformed, keep accumulating as text
                  assistantMessage.content = finalResponseBuffer;
                  assistantMessage.isLoading = false;
                  console.debug('[SSE] Failed to parse JSON, showing as text');
                }
              } else {
                // No JSON pattern found yet, keep as plain text
                assistantMessage.content = finalResponseBuffer;
                assistantMessage.isLoading = false;
              }
            }
            break;

          case 'done':
            // Stream completed - finalize message
            hasReceivedDone = true;
            this.isLoading = false;
            this.isStreaming = false;
            assistantMessage.isLoading = false;
            assistantMessage.isStreaming = false;
            assistantMessage.statusMessages = [];
            break;

          case 'error':
            // Error occurred - convert to structured ERROR response
            try {
              const errorData = JSON.parse(chunk);
              assistantMessage.structured = {
                type: 'ERROR',
                payload: {
                  message: errorData.message || chunk,
                  suggestions: errorData.suggestions || ['Check Transaction Status', 'Account Summary']
                }
              };
              assistantMessage.content = undefined;
            } catch {
              assistantMessage.structured = {
                type: 'ERROR',
                payload: {
                  message: chunk || 'An error occurred. Please try again.',
                  suggestions: ['Check Transaction Status', 'Account Summary']
                }
              };
              assistantMessage.content = undefined;
            }
            assistantMessage.isLoading = false;
            assistantMessage.isStreaming = false;
            assistantMessage.statusMessages = [];
            assistantMessage.isError = true;
            break;

          case 'followup':
            // Follow-up question - convert to structured FOLLOW_UP response
            try {
              const followupData = JSON.parse(chunk);
              assistantMessage.structured = {
                type: 'FOLLOW_UP',
                payload: {
                  missingParams: followupData.missingParams || [],
                  question: followupData.question || chunk
                },
                scenario: followupData.scenario
              };
              assistantMessage.content = undefined;
              assistantMessage.followUp = followupData;
            } catch {
              assistantMessage.content = chunk;
            }
            assistantMessage.isLoading = false;
            break;

          case 'unknown':
            // Unknown scenario - convert to structured ERROR with suggestions
            try {
              const unknownData = JSON.parse(chunk);
              assistantMessage.structured = {
                type: 'ERROR',
                payload: {
                  message: unknownData.message || 'I didn\'t understand that.',
                  suggestions: unknownData.options || ['Transaction Status', 'Account Summary']
                }
              };
              assistantMessage.content = undefined;
              assistantMessage.suggestions = unknownData.options || [];
            } catch {
              assistantMessage.content = chunk;
            }
            assistantMessage.isLoading = false;
            break;

          default:
            // Fallback for unknown event types - handle as message
            if (chunk) {
              // This is part of the final response
              isCollectingFinalResponse = true;
              assistantMessage.statusMessages = [];
              finalResponseBuffer += chunk;

              // Try to extract JSON from the buffer (might have text prefix)
              const jsonMatch = finalResponseBuffer.match(/\{[\s\S]*\}/);
              if (jsonMatch) {
                try {
                  const structuredResponse: StructuredResponse = JSON.parse(jsonMatch[0]);
                  // Successfully parsed as JSON - treat as structured response
                  assistantMessage.structured = structuredResponse;
                  assistantMessage.content = undefined;
                  assistantMessage.isLoading = false;
                  console.log('[SSE] Extracted and parsed structured response:', structuredResponse.type);

                  // Save pending context for FOLLOW_UP
                  if (structuredResponse.type === 'FOLLOW_UP') {
                    const payload = structuredResponse.payload as any;
                    this.pendingContext = {
                      scenario: structuredResponse.scenario || '',
                      params: {},
                      missingParams: payload.missingParams || []
                    };
                    assistantMessage.pendingContext = this.pendingContext;
                    console.log('[Context] Saved pending context:', this.pendingContext);
                  } else {
                    this.pendingContext = null;
                  }
                } catch (e) {
                  // Not valid JSON yet or malformed, keep accumulating
                  console.debug('[SSE] JSON extraction failed, accumulating...');
                }
              } else {
                // No JSON pattern found, treat as plain text
                assistantMessage.content = finalResponseBuffer;
                assistantMessage.isLoading = false;
              }
            }
        }
      },
      error: () => {
        // Fallback to regular chat on stream error
        this.messages.pop();
        this.regularChat(request);
      },
      complete: () => {
        // Ensure UI is never left in loading state
        console.log('[SSE] Stream complete callback fired');
        console.log('[SSE] Final message state:', {
          hasStructured: !!assistantMessage.structured,
          structuredType: assistantMessage.structured?.type,
          hasContent: !!assistantMessage.content,
          contentPreview: assistantMessage.content?.substring(0, 50),
          isLoading: assistantMessage.isLoading,
          isStreaming: assistantMessage.isStreaming
        });

        this.isLoading = false;
        this.isStreaming = false;
        assistantMessage.isLoading = false;
        assistantMessage.isStreaming = false;
        assistantMessage.statusMessages = [];

        // If we never received a 'done' event, log warning
        if (!hasReceivedDone) {
          console.warn('[SSE] Stream completed without receiving done event');
        }
      }
    });
  }

  regularChat(request: ChatRequest): void {
    const loadingMessage: ChatMessage = {
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isLoading: true
    };
    this.messages.push(loadingMessage);

    this.apiService.chat(request).subscribe({
      next: (response: ChatResponse) => {
        this.messages = this.messages.filter(m => !m.isLoading);

        const assistantMessage: ChatMessage = {
          role: 'assistant',
          content: response.message,
          timestamp: new Date()
        };
        this.messages.push(assistantMessage);

        this.sessionId = response.sessionId;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Chat error:', error);
        this.messages = this.messages.filter(m => !m.isLoading);

        const errorMessage: ChatMessage = {
          role: 'assistant',
          content: 'Sorry, I encountered an error. Please try again or check if the backend is running.',
          timestamp: new Date()
        };
        this.messages.push(errorMessage);
        this.isLoading = false;
      }
    });
  }

  clearChat(): void {
    this.messages = [];
    this.sessionId = null;
  }

  onKeyPress(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  getCurrentUser(): string {
    return this.authService.getCurrentUser()?.username || 'User';
  }

  getUserRole(): string {
    return this.authService.getCurrentUser()?.role || 'USER';
  }

  isAdmin(): boolean {
    return this.authService.getCurrentUser()?.role === 'ADMIN';
  }

  logout(): void {
    this.authService.logout();
    window.location.href = '/login';
  }

  toggleSidebar(): void {
    this.showSidebar = !this.showSidebar;
  }

  /**
   * Format AI response with proper HTML structure
   * Handles tables, lists, code blocks, and formatting
   */
  formatResponse(content: string): string {
    if (!content) return '';

    let formatted = content;

    // Remove any leading/trailing whitespace
    formatted = formatted.trim();

    // Format JSON objects/arrays (pretty print)
    formatted = formatted.replace(/```json\n([\s\S]*?)```/g, (match, json) => {
      try {
        const parsed = JSON.parse(json);
        const pretty = JSON.stringify(parsed, null, 2);
        return `<div class="bg-gray-50 rounded-lg p-3 my-2 overflow-x-auto border border-gray-200">
          <pre class="text-xs text-gray-800 font-mono">${this.escapeHtml(pretty)}</pre>
        </div>`;
      } catch {
        return `<div class="bg-gray-50 rounded-lg p-3 my-2 overflow-x-auto border border-gray-200">
          <pre class="text-xs text-gray-800 font-mono">${this.escapeHtml(json)}</pre>
        </div>`;
      }
    });

    // Format code blocks
    formatted = formatted.replace(/```(\w+)?\n([\s\S]*?)```/g, (match, lang, code) => {
      return `<div class="bg-gray-50 rounded-lg p-3 my-2 overflow-x-auto border border-gray-200">
        ${lang ? `<div class="text-xs text-gray-500 mb-2">${lang}</div>` : ''}
        <pre class="text-xs text-gray-800 font-mono">${this.escapeHtml(code)}</pre>
      </div>`;
    });

    // Format inline code
    formatted = formatted.replace(/`([^`]+)`/g, '<code class="bg-gray-100 px-1.5 py-0.5 rounded text-sm text-axis-burgundy">$1</code>');

    // Format bold text
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-gray-900">$1</strong>');

    // Format bullet lists
    formatted = formatted.replace(/^- (.+)$/gm, '<li class="ml-4">$1</li>');
    formatted = formatted.replace(/(<li class="ml-4">.*<\/li>\n?)+/g, '<ul class="list-disc list-inside my-2 space-y-1">$&</ul>');

    // Format numbered lists
    formatted = formatted.replace(/^\d+\. (.+)$/gm, '<li class="ml-4">$1</li>');
    formatted = formatted.replace(/(<li class="ml-4">.*<\/li>\n?)+/g, '<ol class="list-decimal list-inside my-2 space-y-1">$&</ol>');

    // Format headers
    formatted = formatted.replace(/^### (.+)$/gm, '<h3 class="text-base font-semibold text-gray-900 mt-3 mb-2">$1</h3>');
    formatted = formatted.replace(/^## (.+)$/gm, '<h2 class="text-lg font-semibold text-gray-900 mt-4 mb-2">$1</h2>');
    formatted = formatted.replace(/^# (.+)$/gm, '<h1 class="text-xl font-bold text-gray-900 mt-4 mb-3">$1</h1>');

    // Format tables (simple markdown-style tables)
    formatted = formatted.replace(/\|(.+)\|\n\|[-\s:|]+\|\n((?:\|.+\|\n?)+)/g, (match, header, rows) => {
      const headers = header.split('|').map((h: string) => h.trim()).filter((h: string) => h);
      const rowsArray = rows.trim().split('\n').map((row: string) =>
        row.split('|').map((cell: string) => cell.trim()).filter((cell: string) => cell)
      );

      let table = '<div class="overflow-x-auto my-3"><table class="min-w-full divide-y divide-gray-200 border border-gray-200 rounded-lg">';
      table += '<thead class="bg-gray-50"><tr>';
      headers.forEach((h: string) => {
        table += `<th class="px-4 py-2 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">${h}</th>`;
      });
      table += '</tr></thead><tbody class="bg-white divide-y divide-gray-200">';
      rowsArray.forEach((row: string[]) => {
        table += '<tr class="hover:bg-gray-50">';
        row.forEach((cell: string) => {
          table += `<td class="px-4 py-2 text-sm text-gray-700">${cell}</td>`;
        });
        table += '</tr>';
      });
      table += '</tbody></table></div>';
      return table;
    });

    // Format line breaks
    formatted = formatted.replace(/\n\n/g, '<br/><br/>');
    formatted = formatted.replace(/\n/g, '<br/>');

    // Add spacing for emojis
    formatted = formatted.replace(/([\u{1F300}-\u{1F9FF}])/gu, '<span class="inline-block mr-1">$1</span>');

    return formatted;
  }

  /**
   * Escape HTML to prevent XSS
   */
  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}
