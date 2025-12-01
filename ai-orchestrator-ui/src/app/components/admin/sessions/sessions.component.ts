import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { ChatSession } from '../../../models/admin.model';

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './sessions.component.html'
})
export class SessionsComponent implements OnInit {
  sessions: ChatSession[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;

  // Filters
  filterUserId = '';
  filterSessionId = '';

  // Selected session for details
  selectedSession: ChatSession | null = null;

  Math = Math; // Expose Math to template

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.isLoading = true;
    this.adminService.getChatSessions(this.currentPage, this.pageSize, this.filterUserId, this.filterSessionId).subscribe({
      next: (response) => {
        this.sessions = response.content;
        this.totalElements = response.totalElements;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load sessions: ' + error.message);
      }
    });
  }

  applyFilters(): void {
    this.currentPage = 0; // Reset to first page when filtering
    this.loadSessions();
  }

  clearFilters(): void {
    this.filterUserId = '';
    this.filterSessionId = '';
    this.currentPage = 0;
    this.loadSessions();
  }

  viewDetails(session: ChatSession): void {
    this.selectedSession = session;
  }

  closeDetails(): void {
    this.selectedSession = null;
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalElements) {
      this.currentPage++;
      this.loadSessions();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadSessions();
    }
  }

  getTotalPages(): number {
    return Math.ceil(this.totalElements / this.pageSize);
  }

  getStatusColor(status: string): string {
    switch (status?.toLowerCase()) {
      case 'active':
        return 'text-green-600';
      case 'completed':
        return 'text-blue-600';
      case 'expired':
        return 'text-gray-600';
      default:
        return 'text-gray-600';
    }
  }

  formatDate(dateString: string): string {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  formatDuration(start: string, end: string): string {
    if (!start || !end) return 'N/A';
    const startDate = new Date(start);
    const endDate = new Date(end);
    const durationMs = endDate.getTime() - startDate.getTime();
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }
}

