import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertService, Modal } from '../../../services/alert.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (modal) {
      <!-- Modal Overlay -->
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in">
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/50 backdrop-blur-sm"
          (click)="handleBackdropClick()">
        </div>

        <!-- Modal Container -->
        <div class="relative bg-white rounded-xl shadow-2xl max-w-md w-full animate-scale-in">
          <!-- Modal Header -->
          <div class="px-6 py-4 border-b border-gray-200">
            <h3 class="text-lg font-semibold text-gray-900">{{ modal.title }}</h3>
          </div>

          <!-- Modal Body -->
          <div class="px-6 py-6">
            <p class="text-gray-600">{{ modal.message }}</p>
          </div>

          <!-- Modal Footer -->
          <div class="px-6 py-4 bg-gray-50 rounded-b-xl flex items-center justify-end gap-3">
            @if (modal.type === 'confirm') {
              <button
                (click)="handleCancel()"
                class="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors font-medium">
                {{ modal.cancelText || 'Cancel' }}
              </button>
              <button
                (click)="handleConfirm()"
                class="px-4 py-2 bg-axis-burgundy text-white rounded-lg hover:bg-axis-burgundy-dark transition-colors font-medium">
                {{ modal.confirmText || 'Confirm' }}
              </button>
            } @else {
              <button
                (click)="handleConfirm()"
                class="px-4 py-2 bg-axis-burgundy text-white rounded-lg hover:bg-axis-burgundy-dark transition-colors font-medium">
                OK
              </button>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    @keyframes fadeIn {
      from {
        opacity: 0;
      }
      to {
        opacity: 1;
      }
    }

    @keyframes scaleIn {
      from {
        transform: scale(0.95);
        opacity: 0;
      }
      to {
        transform: scale(1);
        opacity: 1;
      }
    }

    .animate-fade-in {
      animation: fadeIn 0.2s ease-out;
    }

    .animate-scale-in {
      animation: scaleIn 0.2s ease-out;
    }
  `]
})
export class ModalComponent implements OnInit, OnDestroy {
  modal: Modal | null = null;
  private subscription?: Subscription;

  constructor(private alertService: AlertService) {}

  ngOnInit(): void {
    this.subscription = this.alertService.modal$.subscribe(
      (modal: Modal | null) => this.modal = modal
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  handleConfirm(): void {
    if (this.modal?.onConfirm) {
      this.modal.onConfirm();
    } else {
      this.alertService.closeModal();
    }
  }

  handleCancel(): void {
    if (this.modal?.onCancel) {
      this.modal.onCancel();
    } else {
      this.alertService.closeModal();
    }
  }

  handleBackdropClick(): void {
    if (this.modal?.type !== 'confirm') {
      this.alertService.closeModal();
    }
  }
}

