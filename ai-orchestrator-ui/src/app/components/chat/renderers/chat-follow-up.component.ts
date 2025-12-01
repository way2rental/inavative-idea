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
      <div class="bg-white rounded-lg p-3 max-w-md">
        <p class="text-gray-800 text-sm leading-relaxed">{{ question }}</p>
      </div>
    </div>
  `,
  styles: [`
    .chat-follow-up-response {
      display: flex;
      margin-bottom: 1rem;
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
