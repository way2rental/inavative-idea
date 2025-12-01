import { Component, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ChatMessage, ChatRequest, ChatResponse } from '../../models/chat.model';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
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

    const request: ChatRequest = {
      userId: this.authService.getCurrentUser()?.username || 'anonymous',
      query: message,
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
    let hasSeenFinalResponseStart = false;

    // Use regular chat as EventSource may not be available
    this.apiService.chatStream(request).subscribe({
      next: (chunk: string) => {
        if (!chunk || chunk.trim() === '') return;

        const trimmedChunk = chunk.trim();

        // Status/intermediate message patterns
        const statusPatterns = ['🔍', '✅', '☑️', '📊', '📈', '💾', '❌', '⚠️', '🔐', '📋'];
        const isStatusMessage = statusPatterns.some(emoji => trimmedChunk.startsWith(emoji));

        // Check if this looks like the start of final AI response
        // Final responses typically start with narrative text, not emojis or status indicators
        const looksLikeFinalResponse = !isStatusMessage &&
                                       !trimmedChunk.includes('Request understood') &&
                                       !trimmedChunk.includes('Verifying permissions') &&
                                       !trimmedChunk.includes('Access granted') &&
                                       !trimmedChunk.includes('Fetching your data') &&
                                       !trimmedChunk.includes('Data retrieved') &&
                                       !trimmedChunk.includes('Preparing your response') &&
                                       trimmedChunk.length > 10;

        if (looksLikeFinalResponse) {
          hasSeenFinalResponseStart = true;
        }

        if (isStatusMessage && !hasSeenFinalResponseStart) {
          // This is an intermediate status message - show only the latest one
          assistantMessage.statusMessages = [trimmedChunk];
          assistantMessage.content = '';
          assistantMessage.isLoading = true;
        } else if (hasSeenFinalResponseStart) {
          // We're now in the final response phase
          isCollectingFinalResponse = true;

          // Clear status messages when final response starts
          if (!finalResponseBuffer && assistantMessage.statusMessages && assistantMessage.statusMessages.length > 0) {
            assistantMessage.statusMessages = [];
          }

          // Accumulate final response
          finalResponseBuffer += chunk;
          assistantMessage.content = this.cleanFinalResponse(finalResponseBuffer);
          assistantMessage.isLoading = false;
        }
      },
      error: () => {
        // Fallback to regular chat
        this.messages.pop();
        this.regularChat(request);
      },
      complete: () => {
        this.isLoading = false;
        this.isStreaming = false;
        assistantMessage.isLoading = false;
        assistantMessage.isStreaming = false;
        assistantMessage.statusMessages = [];

        // Final cleanup of the response
        if (assistantMessage.content) {
          assistantMessage.content = this.cleanFinalResponse(assistantMessage.content);
        }
      }
    });
  }

  /**
   * Clean up the final response by removing status messages and formatting properly
   */
  private cleanFinalResponse(content: string): string {
    if (!content) return '';

    let cleaned = content;

    // Remove any lingering status messages that might have leaked through
    const statusPhrases = [
      /✅\s*Request understood.*?\n/gi,
      /☑️\s*Request understood.*?\n/gi,
      /🔍\s*Verifying permissions.*?\n/gi,
      /✅\s*Access granted.*?\n/gi,
      /☑️\s*Access granted.*?\n/gi,
      /📊\s*Fetching your data.*?\n/gi,
      /✅\s*Data retrieved.*?\n/gi,
      /☑️\s*Data retrieved.*?\n/gi,
      /📋\s*Preparing your response.*?\n/gi,
      /🔐\s*[^\n]*\n/gi,
      /Request understood[^\n]*\n/gi,
      /Verifying permissions[^\n]*\n/gi,
      /Access granted[^\n]*\n/gi,
      /Fetching your data[^\n]*\n/gi,
      /Data retrieved[^\n]*\n/gi,
      /Preparing your response[^\n]*\n/gi
    ];

    statusPhrases.forEach(pattern => {
      cleaned = cleaned.replace(pattern, '');
    });

    // Remove multiple consecutive newlines
    cleaned = cleaned.replace(/\n{3,}/g, '\n\n');

    // Trim whitespace
    cleaned = cleaned.trim();

    return cleaned;
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

    // Remove any remaining status messages that leaked through
    const statusPatterns = [
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Request understood[^\n]*\n?/gi,
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Verifying permissions[^\n]*\n?/gi,
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Access granted[^\n]*\n?/gi,
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Fetching your data[^\n]*\n?/gi,
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Data retrieved[^\n]*\n?/gi,
      /[✅☑️🔍📊📈💾❌⚠️🔐📋]\s*Preparing your response[^\n]*\n?/gi
    ];

    statusPatterns.forEach(pattern => {
      formatted = formatted.replace(pattern, '');
    });

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

    // Format bold text (including emoji + bold patterns)
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-gray-900">$1</strong>');

    // Format bullet lists
    formatted = formatted.replace(/^[•\-]\s+(.+)$/gm, '<li class="ml-4 my-1">$1</li>');
    formatted = formatted.replace(/(<li class="ml-4 my-1">.*?<\/li>\s*)+/gs, '<ul class="list-disc list-inside my-3 space-y-1.5 pl-2">$&</ul>');

    // Format numbered lists
    formatted = formatted.replace(/^\d+\.\s+(.+)$/gm, '<li class="ml-4 my-1">$1</li>');
    formatted = formatted.replace(/(<li class="ml-4 my-1">.*?<\/li>\s*)+/gs, '<ol class="list-decimal list-inside my-3 space-y-1.5 pl-2">$&</ol>');

    // Format key-value pairs (e.g., "Date: value", "Amount: value")
    formatted = formatted.replace(/^(\w+(?:\s+\w+)?):\s*(.+)$/gm,
      '<div class="my-1"><span class="font-medium text-gray-700">$1:</span> <span class="text-gray-900">$2</span></div>');

    // Format headers
    formatted = formatted.replace(/^###\s+(.+)$/gm, '<h3 class="text-base font-semibold text-gray-900 mt-4 mb-2">$1</h3>');
    formatted = formatted.replace(/^##\s+(.+)$/gm, '<h2 class="text-lg font-semibold text-gray-900 mt-4 mb-2">$1</h2>');
    formatted = formatted.replace(/^#\s+(.+)$/gm, '<h1 class="text-xl font-bold text-gray-900 mt-4 mb-3">$1</h1>');

    // Format tables (simple markdown-style tables)
    formatted = formatted.replace(/\|(.+)\|\n\|[-\s:|]+\|\n((?:\|.+\|\n?)+)/g, (match, header, rows) => {
      const headers = header.split('|').map((h: string) => h.trim()).filter((h: string) => h);
      const rowsArray = rows.trim().split('\n').map((row: string) =>
        row.split('|').map((cell: string) => cell.trim()).filter((cell: string) => cell)
      );

      let table = '<div class="overflow-x-auto my-4 rounded-lg border border-gray-200 shadow-sm">';
      table += '<table class="min-w-full divide-y divide-gray-200">';
      table += '<thead class="bg-gradient-to-r from-axis-burgundy to-red-800 text-white"><tr>';
      headers.forEach((h: string) => {
        table += `<th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider">${h}</th>`;
      });
      table += '</tr></thead><tbody class="bg-white divide-y divide-gray-100">';
      rowsArray.forEach((row: string[], index: number) => {
        const bgClass = index % 2 === 0 ? 'bg-white' : 'bg-gray-50';
        table += `<tr class="${bgClass} hover:bg-blue-50 transition-colors">`;
        row.forEach((cell: string) => {
          table += `<td class="px-4 py-3 text-sm text-gray-800 whitespace-nowrap">${cell}</td>`;
        });
        table += '</tr>';
      });
      table += '</tbody></table></div>';
      return table;
    });

    // Format sections with emoji headers (e.g., "📋 Transaction History:")
    formatted = formatted.replace(/([\u{1F300}-\u{1F9FF}])\s*([^:\n]+):/gu,
      '<div class="flex items-center gap-2 my-3 pb-2 border-b border-gray-200"><span class="text-2xl">$1</span><span class="text-lg font-semibold text-gray-800">$2</span></div>');

    // Format line breaks (but preserve structure)
    formatted = formatted.replace(/\n\n\n+/g, '\n\n'); // Collapse multiple line breaks
    formatted = formatted.replace(/\n\n/g, '</p><p class="my-2">');
    formatted = formatted.replace(/\n/g, '<br/>');

    // Wrap in paragraph tags
    formatted = '<p class="my-2">' + formatted + '</p>';

    // Clean up empty paragraphs
    formatted = formatted.replace(/<p class="my-2"><\/p>/g, '');

    // Add spacing for standalone emojis
    formatted = formatted.replace(/([\u{1F300}-\u{1F9FF}])/gu, '<span class="inline-block mx-1">$1</span>');

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
