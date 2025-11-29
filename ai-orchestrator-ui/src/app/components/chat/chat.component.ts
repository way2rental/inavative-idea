import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { ChatMessage, ChatRequest, ChatResponse } from '../../models/chat.model';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss'
})
export class ChatComponent {
  messages: ChatMessage[] = [];
  inputMessage = '';
  sessionId: string | null = null;
  isLoading = false;

  constructor(
    private apiService: ApiService,
    private authService: AuthService
  ) {}

  sendMessage(): void {
    if (!this.inputMessage.trim() || this.isLoading) return;

    const userMessage: ChatMessage = {
      role: 'user',
      content: this.inputMessage,
      timestamp: new Date()
    };
    this.messages.push(userMessage);

    const loadingMessage: ChatMessage = {
      role: 'assistant',
      content: 'Thinking...',
      timestamp: new Date(),
      isLoading: true
    };
    this.messages.push(loadingMessage);

    const request: ChatRequest = {
      userId: this.authService.getCurrentUser()?.username || 'anonymous',
      query: this.inputMessage,
      sessionId: this.sessionId || undefined
    };

    this.inputMessage = '';
    this.isLoading = true;

    this.apiService.chat(request).subscribe({
      next: (response: ChatResponse) => {
        // Remove loading message
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
        // Remove loading message
        this.messages = this.messages.filter(m => !m.isLoading);
        
        const errorMessage: ChatMessage = {
          role: 'assistant',
          content: 'Sorry, an error occurred. Please try again.',
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
}
