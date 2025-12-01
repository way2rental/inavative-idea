import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, TextPayload } from '../../../models/chat.model';

/**
 * TEXT Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders simple paragraph text.
 */
@Component({
  selector: 'app-chat-text',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-text-response">
      @if (response.title) {
        <h4 class="text-sm font-semibold text-gray-800 mb-2">{{ response.title }}</h4>
      }
      <p class="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{{ message }}</p>
      @if (response.footer) {
        <p class="text-xs text-gray-500 mt-2 italic">{{ response.footer }}</p>
      }
    </div>
  `
})
export class ChatTextComponent {
  @Input() response!: StructuredResponse;

  get payload(): TextPayload | null {
    return this.response?.payload as TextPayload;
  }

  get message(): string {
    return this.payload?.message || '';
  }
}
