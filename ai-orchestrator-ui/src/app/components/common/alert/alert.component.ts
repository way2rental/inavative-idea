import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertService, Alert } from '../../../services/alert.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Alert Container (Top Right) -->
    <div class="fixed top-4 right-4 z-50 space-y-3 max-w-md">
      @for (alert of alerts; track alert.id) {
        <div
          [class]="getAlertClasses(alert.type)"
          class="rounded-lg shadow-lg p-4 flex items-start gap-3 animate-slide-in-right border-l-4"
          role="alert">

          <!-- Icon -->
          <div [class]="getIconClasses(alert.type)" class="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center">
            @switch (alert.type) {
              @case ('success') {
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
                </svg>
              }
              @case ('error') {
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/>
                </svg>
              }
              @case ('warning') {
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
                </svg>
              }
              @case ('info') {
                <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/>
                </svg>
              }
            }
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <h4 class="font-semibold text-gray-900 text-sm mb-1">{{ alert.title }}</h4>
            <p class="text-gray-600 text-sm">{{ alert.message }}</p>
          </div>

          <!-- Close Button -->
          @if (alert.closable) {
            <button
              (click)="removeAlert(alert.id)"
              class="flex-shrink-0 text-gray-400 hover:text-gray-600 transition-colors">
              <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
              </svg>
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slideInRight {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    .animate-slide-in-right {
      animation: slideInRight 0.3s ease-out;
    }
  `]
})
export class AlertComponent implements OnInit, OnDestroy {
  alerts: Alert[] = [];
  private subscription?: Subscription;

  constructor(private alertService: AlertService) {}

  ngOnInit(): void {
    this.subscription = this.alertService.alerts$.subscribe(
      (alerts: Alert[]) => this.alerts = alerts
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  removeAlert(id: string): void {
    this.alertService.removeAlert(id);
  }

  getAlertClasses(type: Alert['type']): string {
    const baseClasses = 'bg-white';
    const borderClasses = {
      success: 'border-green-500',
      error: 'border-red-500',
      warning: 'border-amber-500',
      info: 'border-blue-500'
    };
    return `${baseClasses} ${borderClasses[type]}`;
  }

  getIconClasses(type: Alert['type']): string {
    const classes = {
      success: 'bg-green-100 text-green-600',
      error: 'bg-red-100 text-red-600',
      warning: 'bg-amber-100 text-amber-600',
      info: 'bg-blue-100 text-blue-600'
    };
    return classes[type];
  }
}

