import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, ErrorPayload } from '../../../models/chat.model';

/**
 * ERROR Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders red warning box with suggestions.
 */
@Component({
  selector: 'app-chat-error',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-error-response">
      <div class="bg-gradient-to-br from-red-50 to-rose-50 rounded-xl border border-red-200 p-4">
        <div class="flex items-start gap-3">
          <div class="flex-shrink-0 w-8 h-8 bg-red-100 rounded-full flex items-center justify-center">
            <svg class="w-4 h-4 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div class="flex-1">
            <p class="text-sm font-medium text-red-800">{{ errorMessage }}</p>
            @if (suggestions.length > 0) {
              <div class="mt-3">
                <p class="text-xs text-red-600 mb-2">Try one of these:</p>
                <div class="space-y-1">
                  @for (suggestion of suggestions; track suggestion) {
                    <button 
                      (click)="suggestionClicked.emit(suggestion)"
                      class="w-full text-left px-3 py-2 text-sm text-red-700 bg-white hover:bg-red-50 rounded-lg border border-red-100 transition-colors">
                      {{ suggestion }}
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `
})
export class ChatErrorComponent {
  @Input() response!: StructuredResponse;
  @Output() suggestionClicked = new EventEmitter<string>();

  get payload(): ErrorPayload | null {
    return this.response?.payload as ErrorPayload;
  }

  get errorMessage(): string {
    return this.payload?.message || 'An error occurred';
  }

  get suggestions(): string[] {
    return this.payload?.suggestions || [];
  }
}
