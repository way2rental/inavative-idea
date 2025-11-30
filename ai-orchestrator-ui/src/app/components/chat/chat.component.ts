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

  // Quick actions for main page
  quickActions = [
    { icon: '💳', label: 'Transaction Status', query: 'What is the status of transaction TXN123?' },
    { icon: '📁', label: 'File Status', query: 'Check status of file salary_batch.csv' },
    { icon: '📊', label: 'Account Summary', query: 'Show summary for account ACC456' },
    { icon: '💰', label: 'Balance Inquiry', query: 'What is my current balance?' }
  ];

  // Quick services for AHA-style chat widget
  quickServices = [
    { icon: '🏦', label: 'Open Current Account', query: 'How to open a current account?', isNew: false },
    { icon: '💰', label: 'Open Savings Account', query: 'How to open a savings account?', isNew: false },
    { icon: '📈', label: 'Open Fixed Deposit', query: 'How to open a fixed deposit?', isNew: false },
    { icon: '💳', label: 'Credit Card', query: 'Tell me about credit card services', isNew: false },
    { icon: '🏠', label: 'Retail Loan Services', query: 'What retail loan services are available?', isNew: true },
    { icon: '📊', label: 'Account', query: 'Show my account details', isNew: false },
    { icon: '💵', label: 'Debit Card', query: 'Debit card related queries', isNew: false },
    { icon: '📱', label: 'WhatsApp Banking', query: 'How to use WhatsApp banking?', isNew: true },
    { icon: '🔗', label: 'API Support', query: 'API integration support', isNew: false },
    { icon: '🏢', label: 'Corporate Banking', query: 'Corporate banking services', isNew: true },
    { icon: '💼', label: 'Neo For Business', query: 'Tell me about Neo for Business', isNew: false }
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
      isLoading: true
    };
    this.messages.push(assistantMessage);

    // Use regular chat as EventSource may not be available
    this.apiService.chatStream(request).subscribe({
      next: (chunk: string) => {
        this.streamingContent += chunk;
        assistantMessage.content = this.streamingContent;
        assistantMessage.isLoading = false;
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
}
