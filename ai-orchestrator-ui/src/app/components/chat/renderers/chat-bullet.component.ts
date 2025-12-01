import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, BulletPayload } from '../../../models/chat.model';

/**
 * BULLET Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders bulleted list.
 */
@Component({
  selector: 'app-chat-bullet',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-bullet-response">
      @if (response.title) {
        <h4 class="text-sm font-semibold text-gray-800 mb-2">{{ response.title }}</h4>
      }
      <ul class="list-disc list-inside space-y-1">
        @for (item of items; track item) {
          <li class="text-sm text-gray-700">{{ item }}</li>
        }
      </ul>
      @if (response.footer) {
        <p class="text-xs text-gray-500 mt-2 italic">{{ response.footer }}</p>
      }
    </div>
  `
})
export class ChatBulletComponent {
  @Input() response!: StructuredResponse;

  get payload(): BulletPayload | null {
    return this.response?.payload as BulletPayload;
  }

  get items(): string[] {
    return this.payload?.items || [];
  }
}
