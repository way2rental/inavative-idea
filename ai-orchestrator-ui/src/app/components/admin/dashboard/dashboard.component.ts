import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { DashboardStats, PerformanceMetrics, Scenario, AuditLog } from '../../../models/admin.model';
import { interval, Subscription } from 'rxjs';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, BaseChartDirective],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  // Expose Math to template
  Math = Math;

  stats: DashboardStats | null = null;
  metrics: PerformanceMetrics | null = null;
  recentScenarios: Scenario[] = [];
  recentLogs: AuditLog[] = [];
  isLoading = true;
  private refreshSubscription?: Subscription;

  // Sidebar state
  sidebarCollapsed = false;

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  // Chart Data
  requestsChartData: ChartData<'line'> = {
    labels: ['12 AM', '4 AM', '8 AM', '12 PM', '4 PM', '8 PM'],
    datasets: [{
      label: 'Requests',
      data: [45, 38, 62, 89, 105, 78],
      borderColor: '#97144D',
      backgroundColor: 'rgba(151, 20, 77, 0.1)',
      tension: 0.4,
      fill: true
    }]
  };

  requestsChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        cornerRadius: 8
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        grid: { color: 'rgba(0, 0, 0, 0.05)' }
      },
      x: {
        grid: { display: false }
      }
    }
  };

  responseTimeChartData: ChartData<'bar'> = {
    labels: ['< 100ms', '100-200ms', '200-300ms', '300-500ms', '> 500ms'],
    datasets: [{
      label: 'Requests',
      data: [120, 85, 45, 28, 12],
      backgroundColor: [
        'rgba(34, 197, 94, 0.8)',
        'rgba(59, 130, 246, 0.8)',
        'rgba(251, 191, 36, 0.8)',
        'rgba(249, 115, 22, 0.8)',
        'rgba(239, 68, 68, 0.8)'
      ],
      borderRadius: 8,
      borderSkipped: false
    }]
  };

  responseTimeChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        cornerRadius: 8
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        grid: { color: 'rgba(0, 0, 0, 0.05)' }
      },
      x: {
        grid: { display: false }
      }
    }
  };

  successRateChartData: ChartData<'line'> = {
    labels: ['Week 1', 'Week 2', 'Week 3', 'Week 4'],
    datasets: [{
      label: 'Success Rate (%)',
      data: [98.5, 99.2, 98.8, 99.5],
      borderColor: '#9333EA',
      backgroundColor: 'rgba(147, 51, 234, 0.1)',
      tension: 0.4,
      fill: true,
      pointBackgroundColor: '#9333EA',
      pointRadius: 6,
      pointHoverRadius: 8
    }]
  };

  successRateChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        cornerRadius: 8,
        callbacks: {
          label: (context: any) => `Success Rate: ${context.parsed.y}%`
        }
      }
    },
    scales: {
      y: {
        min: 95,
        max: 100,
        grid: { color: 'rgba(0, 0, 0, 0.05)' }
      },
      x: {
        grid: { display: false }
      }
    }
  };

  scenarioUsageChartData: ChartData<'doughnut'> = {
    labels: ['Account Summary', 'Transaction Status', 'Balance Inquiry', 'Transfer Funds', 'Others'],
    datasets: [{
      data: [35, 25, 18, 15, 7],
      backgroundColor: [
        'rgba(151, 20, 77, 0.8)',
        'rgba(59, 130, 246, 0.8)',
        'rgba(34, 197, 94, 0.8)',
        'rgba(251, 191, 36, 0.8)',
        'rgba(156, 163, 175, 0.8)'
      ],
      borderWidth: 0
    }]
  };

  scenarioUsageChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: {
          padding: 15,
          font: { size: 11 },
          usePointStyle: true
        }
      },
      tooltip: {
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        padding: 12,
        cornerRadius: 8,
        callbacks: {
          label: (context: any) => {
            const label = context.label || '';
            const value = context.parsed || 0;
            const total = (context.dataset.data as number[]).reduce((a: number, b: number) => a + b, 0);
            const percentage = ((value / total) * 100).toFixed(1);
            return `${label}: ${percentage}%`;
          }
        }
      }
    }
  };

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

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

    // Load dashboard stats
    this.adminService.getDashboardStats().subscribe(stats => {
      this.stats = stats;
      this.isLoading = false;
    });

    // Load performance metrics
    this.adminService.getPerformanceMetrics().subscribe(metrics => {
      this.metrics = metrics;
    });

    // Load recent scenarios
    this.adminService.getScenarios().subscribe(scenarios => {
      this.recentScenarios = scenarios.slice(0, 5);
    });

    // Load recent audit logs
    this.adminService.getAuditLogs(0, 5).subscribe(response => {
      this.recentLogs = response.content;
    });

    // Load analytics chart data from APIs
    this.loadAnalyticsCharts();
  }

  loadAnalyticsCharts(): void {
    // Load Requests Over Time
    this.adminService.getRequestsOverTime(24, 4).subscribe(data => {
      if (data && data.labels && data.data) {
        this.requestsChartData.labels = data.labels;
        this.requestsChartData.datasets[0].data = data.data;
      }
    });

    // Load Response Distribution
    this.adminService.getResponseDistribution().subscribe(data => {
      if (data && data.labels && data.data) {
        this.responseTimeChartData.labels = data.labels;
        this.responseTimeChartData.datasets[0].data = data.data;
      }
    });

    // Load Success Rate Trend
    this.adminService.getSuccessRateTrend(4).subscribe(data => {
      if (data && data.labels && data.data) {
        this.successRateChartData.labels = data.labels;
        this.successRateChartData.datasets[0].data = data.data;
      }
    });

    // Load Scenario Usage
    this.adminService.getScenarioUsage(5).subscribe(data => {
      if (data && data.labels && data.data) {
        this.scenarioUsageChartData.labels = data.labels;
        this.scenarioUsageChartData.datasets[0].data = data.data;
      }
    });
  }

  refreshCache(): void {
    this.adminService.refreshScenarioCache().subscribe({
      next: () => {
        this.alertService.success('Success', 'Cache refreshed successfully!');
        this.loadDashboardData();
      },
      error: () => this.alertService.error('Error', 'Failed to refresh cache')
    });
  }
}
