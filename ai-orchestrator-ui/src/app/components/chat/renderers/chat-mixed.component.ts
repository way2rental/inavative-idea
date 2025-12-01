import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, MixedPayload, TablePayload } from '../../../models/chat.model';

/**
 * MIXED Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders text + table combination.
 */
@Component({
  selector: 'app-chat-mixed',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chat-mixed-response">
      @if (response.title) {
        <h4 class="text-sm font-semibold text-gray-800 mb-2">{{ response.title }}</h4>
      }

      <!-- Text section -->
      @if (textContent) {
        <p class="text-sm text-gray-700 leading-relaxed mb-3">{{ textContent }}</p>
      }

      <!-- Table section -->
      @if (table) {
        <div class="overflow-x-auto rounded-xl border border-gray-200">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gradient-to-r from-gray-50 to-gray-100">
              <tr>
                @for (col of table.columns; track col) {
                  <th class="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    {{ col }}
                  </th>
                }
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-100">
              @for (row of table.rows; track $index) {
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
      }

      @if (response.footer) {
        <p class="text-xs text-gray-500 mt-2 italic">{{ response.footer }}</p>
      }
    </div>
  `
})
export class ChatMixedComponent {
  @Input() response!: StructuredResponse;

  get payload(): MixedPayload | null {
    return this.response?.payload as MixedPayload;
  }

  get textContent(): string {
    return this.payload?.text || '';
  }

  get table(): TablePayload | null {
    const tableData = this.payload?.table || null;
    // Support both 'columns' and 'headers' for backward compatibility
    if (tableData && !(tableData as any).columns && (tableData as any).headers) {
      return {
        columns: (tableData as any).headers,
        rows: tableData.rows || []
      };
    }
    return tableData;
  }
}
