import { Component, Input } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { StructuredResponse } from '../../../models/chat.model';

/**
 * KV (Key-Value) Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders card-style key-value layout.
 */
@Component({
  selector: 'app-chat-kv',
  standalone: true,
  imports: [CommonModule, KeyValuePipe],
  template: `
    <div class="chat-kv-response">
      @if (response.title) {
        <h4 class="text-sm font-semibold text-gray-800 mb-3">{{ response.title }}</h4>
      }
      <div class="bg-gradient-to-br from-gray-50 to-white rounded-xl border border-gray-200 p-4 space-y-2">
        @for (item of payload | keyvalue; track item.key) {
          <div class="flex justify-between items-center py-1.5 border-b border-gray-100 last:border-0">
            <span class="text-xs font-medium text-gray-500 uppercase tracking-wide">{{ item.key }}</span>
            <span class="text-sm font-semibold text-gray-800">{{ item.value }}</span>
          </div>
        }
      </div>
      @if (response.footer) {
        <p class="text-xs text-gray-500 mt-2 italic">{{ response.footer }}</p>
      }
    </div>
  `
})
export class ChatKvComponent {
  @Input() response!: StructuredResponse;

  get payload(): Record<string, any> {
    return this.response?.payload || {};
  }
}
