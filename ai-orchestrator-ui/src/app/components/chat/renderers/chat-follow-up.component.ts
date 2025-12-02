import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredResponse, FollowUpPayload } from '../../../models/chat.model';

/**
 * FOLLOW_UP Response Renderer per STRUCTURED_CHAT_RESPONSE_UPGRADE.md Section 7.
 * Renders highlighted question with missing parameters.
 * No outer wrapper - parent already provides chat bubble styling.
 */
@Component({
  selector: 'app-chat-follow-up',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="follow-up-content">
      <p class="text-gray-800 text-sm leading-relaxed">{{ question }}</p>
      <div *ngIf="missingParams.length > 0" class="mt-2 text-xs text-gray-500">
        <span class="font-medium">Looking for:</span>
        <span *ngFor="let param of missingParams; let last = last">
          {{ param }}{{ last ? '' : ', ' }}
        </span>
      </div>
    </div>
  `,
  styles: [`
    .follow-up-content {
      /* No additional styling needed - parent provides chat bubble */
    }
  `]
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
