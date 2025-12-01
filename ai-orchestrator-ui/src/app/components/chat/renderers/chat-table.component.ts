import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, TablePayload } from '../../../models/chat.model';

/**
 * TABLE Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders full-width responsive table.
 */
@Component({
  selector: 'app-chat-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-table-response">
      @if (response.title) {
        <h4 class="text-sm font-semibold text-gray-800 mb-3">{{ response.title }}</h4>
      }
      <div class="overflow-x-auto rounded-xl border border-gray-200">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gradient-to-r from-gray-50 to-gray-100">
            <tr>
              @for (col of columns; track col) {
                <th class="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  {{ col }}
                </th>
              }
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-100">
            @for (row of rows; track $index) {
              <tr class="hover:bg-gray-50 transition-colors">
                @for (cell of row; track $index) {
                  <td class="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                    {{ cell }}
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (response.footer) {
        <p class="text-xs text-gray-500 mt-2 italic">{{ response.footer }}</p>
      }
    </div>
  `
})
export class ChatTableComponent {
  @Input() response!: StructuredResponse;

  get payload(): TablePayload | null {
    return this.response?.payload as TablePayload;
  }

  get columns(): string[] {
    // Support both 'columns' and 'headers' for backward compatibility
    return this.payload?.columns || (this.payload as any)?.headers || [];
  }

  get rows(): string[][] {
    return this.payload?.rows || [];
  }
}
