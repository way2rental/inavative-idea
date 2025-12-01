import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Alert {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;
  closable?: boolean;
}

export interface Modal {
  id: string;
  title: string;
  message: string;
  type: 'confirm' | 'info' | 'custom';
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  customTemplate?: any;
}

@Injectable({
  providedIn: 'root'
})
export class AlertService {
  private alertSubject = new BehaviorSubject<Alert[]>([]);
  private modalSubject = new BehaviorSubject<Modal | null>(null);

  alerts$ = this.alertSubject.asObservable();
  modal$ = this.modalSubject.asObservable();

  /**
   * Show a success alert
   */
  success(title: string, message: string, duration: number = 5000): void {
    this.showAlert('success', title, message, duration);
  }

  /**
   * Show an error alert
   */
  error(title: string, message: string, duration: number = 7000): void {
    this.showAlert('error', title, message, duration);
  }

  /**
   * Show a warning alert
   */
  warning(title: string, message: string, duration: number = 5000): void {
    this.showAlert('warning', title, message, duration);
  }

  /**
   * Show an info alert
   */
  info(title: string, message: string, duration: number = 5000): void {
    this.showAlert('info', title, message, duration);
  }

  /**
   * Show a confirmation modal
   */
  confirm(
    title: string,
    message: string,
    confirmText: string = 'Confirm',
    cancelText: string = 'Cancel'
  ): Promise<boolean> {
    return new Promise((resolve) => {
      const modal: Modal = {
        id: this.generateId(),
        title,
        message,
        type: 'confirm',
        confirmText,
        cancelText,
        onConfirm: () => {
          this.closeModal();
          resolve(true);
        },
        onCancel: () => {
          this.closeModal();
          resolve(false);
        }
      };
      this.modalSubject.next(modal);
    });
  }

  /**
   * Show an info modal
   */
  showModal(title: string, message: string): void {
    const modal: Modal = {
      id: this.generateId(),
      title,
      message,
      type: 'info',
      onConfirm: () => this.closeModal()
    };
    this.modalSubject.next(modal);
  }

  /**
   * Close the current modal
   */
  closeModal(): void {
    this.modalSubject.next(null);
  }

  /**
   * Remove a specific alert
   */
  removeAlert(id: string): void {
    const currentAlerts = this.alertSubject.value.filter(alert => alert.id !== id);
    this.alertSubject.next(currentAlerts);
  }

  /**
   * Clear all alerts
   */
  clearAlerts(): void {
    this.alertSubject.next([]);
  }

  private showAlert(
    type: Alert['type'],
    title: string,
    message: string,
    duration?: number
  ): void {
    const alert: Alert = {
      id: this.generateId(),
      type,
      title,
      message,
      duration,
      closable: true
    };

    const currentAlerts = this.alertSubject.value;
    this.alertSubject.next([...currentAlerts, alert]);

    if (duration && duration > 0) {
      setTimeout(() => {
        this.removeAlert(alert.id);
      }, duration);
    }
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

