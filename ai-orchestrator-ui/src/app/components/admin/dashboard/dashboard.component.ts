import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { DashboardStats, PerformanceMetrics, OllamaStatus, Scenario, AuditLog } from '../../../models/admin.model';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  stats: DashboardStats | null = null;
  metrics: PerformanceMetrics | null = null;
  ollamaStatus: OllamaStatus | null = null;
  recentScenarios: Scenario[] = [];
  recentLogs: AuditLog[] = [];
  isLoading = true;
  private refreshSubscription?: Subscription;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadDashboardData();
    // Auto-refresh every 30 seconds
    this.refreshSubscription = interval(30000).subscribe(() => {
      this.loadDashboardData(false);
    });
  }

  ngOnDestroy(): void {
    this.refreshSubscription?.unsubscribe();
  }

  loadDashboardData(showLoading = true): void {
    if (showLoading) this.isLoading = true;

    this.adminService.getDashboardStats().subscribe(stats => {
      this.stats = stats;
      this.isLoading = false;
    });

    this.adminService.getPerformanceMetrics().subscribe(metrics => {
      this.metrics = metrics;
    });

    this.adminService.getOllamaStatus().subscribe(status => {
      this.ollamaStatus = status;
    });

    this.adminService.getScenarios().subscribe(scenarios => {
      this.recentScenarios = scenarios.slice(0, 5);
    });

    this.adminService.getAuditLogs(0, 5).subscribe(response => {
      this.recentLogs = response.content;
    });
  }

  refreshCache(): void {
    this.adminService.refreshScenarioCache().subscribe({
      next: () => {
        alert('Cache refreshed successfully!');
        this.loadDashboardData();
      },
      error: () => alert('Failed to refresh cache')
    });
  }
}
