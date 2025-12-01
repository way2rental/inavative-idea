import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, FollowUpPayload } from '../../../models/chat.model';

/**
 * FOLLOW_UP Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders highlighted question with missing parameters.
 */
@Component({
  selector: 'app-chat-follow-up',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-follow-up-response">
      <div class="bg-gradient-to-br from-amber-50 to-orange-50 rounded-xl border border-amber-200 p-4">
        <div class="flex items-start gap-3">
          <div class="flex-shrink-0 w-8 h-8 bg-amber-100 rounded-full flex items-center justify-center">
            <svg class="w-4 h-4 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div class="flex-1">
            <p class="text-sm font-medium text-amber-800 mb-2">{{ question }}</p>
            @if (missingParams.length > 0) {
              <div class="flex flex-wrap gap-2 mt-2">
                @for (param of missingParams; track param) {
                  <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 border border-amber-200">
                    {{ param }}
                  </span>
                }
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `
})
export class ChatFollowUpComponent {
  @Input() response!: StructuredResponse;
  @Output() paramClicked = new EventEmitter<string>();

  get payload(): FollowUpPayload | null {
    return this.response?.payload as FollowUpPayload;
  }

  get question(): string {
    return this.payload?.question || 'Please provide more information';
  }

  get missingParams(): string[] {
    return this.payload?.missingParams || [];
  }
}
