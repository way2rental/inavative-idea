import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';

@Component({
  selector: 'app-context-memory',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './context-memory.component.html'
})
export class ContextMemoryComponent implements OnInit {
  memories: any[] = [];
  filteredMemories: any[] = [];
  isLoading = true;
  showCleanupModal = false;
  cleanupDays = 7;

  // Filter
  filterSessionId = '';
  filterEntityType = '';

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadMemories();
  }

  loadMemories(): void {
    this.isLoading = true;
    const sessionId = this.filterSessionId || undefined;
    const entityType = this.filterEntityType || undefined;
    
    this.adminService.getContextMemory(sessionId, entityType).subscribe({
      next: (memories) => {
        this.memories = memories;
        this.filteredMemories = memories;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load context memory: ' + error.message);
      }
    });
  }

  clearSession(sessionId: string): void {
    if (!confirm(`Are you sure you want to clear context memory for session ${sessionId}?`)) {
      return;
    }

    this.adminService.clearContextMemory(sessionId).subscribe({
      next: () => {
        this.loadMemories();
        this.alertService.success('Success', 'Context memory cleared successfully');
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to clear context memory');
      }
    });
  }

  openCleanupModal(): void {
    this.showCleanupModal = true;
  }

  closeCleanupModal(): void {
    this.showCleanupModal = false;
    this.cleanupDays = 7;
  }

  cleanupOldMemory(): void {
    if (!confirm(`This will delete context memory older than ${this.cleanupDays} days. Continue?`)) {
      return;
    }

    this.adminService.cleanupOldMemory(this.cleanupDays).subscribe({
      next: () => {
        this.closeCleanupModal();
        this.loadMemories();
        this.alertService.success('Success', `Cleaned up context memory older than ${this.cleanupDays} days`);
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to cleanup old memory');
      }
    });
  }

  applyFilters(): void {
    this.loadMemories();
  }

  clearFilters(): void {
    this.filterSessionId = '';
    this.filterEntityType = '';
    this.loadMemories();
  }

  formatJson(json: string): string {
    if (!json) return '{}';
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleString();
  }
}
