import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AuditLog } from '../../../models/admin.model';

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './audit-logs.component.html'
})
export class AuditLogsComponent implements OnInit {
  logs: AuditLog[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;

  filterUserId = '';
  filterScenarioCode = '';

  selectedLog: AuditLog | null = null;

  Math = Math; // Expose Math to template

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    this.isLoading = true;
    const filters: { userId?: string; scenarioCode?: string } = {};
    if (this.filterUserId) filters.userId = this.filterUserId;
    if (this.filterScenarioCode) filters.scenarioCode = this.filterScenarioCode;

    this.adminService.getAuditLogs(this.currentPage, this.pageSize, filters).subscribe({
      next: (response) => {
        this.logs = response.content;
        this.totalElements = response.totalElements;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  clearFilters(): void {
    this.filterUserId = '';
    this.filterScenarioCode = '';
    this.currentPage = 0;
    this.loadLogs();
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalElements) {
      this.currentPage++;
      this.loadLogs();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadLogs();
    }
  }

  viewDetails(log: AuditLog): void {
    this.selectedLog = log;
  }

  closeDetails(): void {
    this.selectedLog = null;
  }

  getConfidenceColor(confidence: number): string {
    if (confidence >= 0.8) return 'text-emerald-400';
    if (confidence >= 0.6) return 'text-amber-400';
    return 'text-red-400';
  }

  formatJson(json: string): string {
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }
}
