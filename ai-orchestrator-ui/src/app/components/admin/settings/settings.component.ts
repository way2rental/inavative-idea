import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AlertService } from '../../../services/alert.service';
import { ActuatorHealth, ActuatorInfo, ActuatorMetrics, ActuatorMetricValue, SystemInfo } from '../../../models/admin.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  isLoading = true;
  activeTab: 'health' | 'info' | 'metrics' | 'config' = 'health';

  // Actuator Data
  health: ActuatorHealth | null = null;
  info: ActuatorInfo | null = null;
  metrics: ActuatorMetrics | null = null;
  systemInfo: SystemInfo | null = null;

  // Metric Details
  selectedMetric: string | null = null;
  metricValue: ActuatorMetricValue | null = null;

  // Common Metrics to Display
  commonMetrics = [
    { key: 'jvm.memory.used', label: 'JVM Memory Used', unit: 'bytes' },
    { key: 'jvm.memory.max', label: 'JVM Memory Max', unit: 'bytes' },
    { key: 'jvm.threads.live', label: 'Live Threads', unit: 'count' },
    { key: 'system.cpu.usage', label: 'System CPU Usage', unit: 'percentage' },
    { key: 'process.uptime', label: 'Process Uptime', unit: 'seconds' },
    { key: 'hikaricp.connections.active', label: 'Active DB Connections', unit: 'count' },
    { key: 'http.server.requests', label: 'HTTP Requests', unit: 'count' }
  ];

  metricValues: Map<string, number> = new Map();

  private readonly baseUrl = 'http://localhost:8080/actuator';

  constructor(
    private http: HttpClient,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadAllData();
  }

  loadAllData(): void {
    this.isLoading = true;
    this.loadHealth();
    this.loadInfo();
    this.loadMetrics();
    this.loadCommonMetrics();
  }

  loadHealth(): void {
    this.http.get<ActuatorHealth>(`${this.baseUrl}/health`).subscribe({
      next: (data) => {
        this.health = data;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Failed to load health', error);
        this.alertService.error('Error', 'Failed to load health status');
        this.isLoading = false;
      }
    });
  }

  loadInfo(): void {
    this.http.get<ActuatorInfo>(`${this.baseUrl}/info`).subscribe({
      next: (data) => {
        this.info = data;
      },
      error: (error) => {
        console.error('Failed to load info', error);
      }
    });
  }

  loadMetrics(): void {
    this.http.get<ActuatorMetrics>(`${this.baseUrl}/metrics`).subscribe({
      next: (data) => {
        this.metrics = data;
      },
      error: (error) => {
        console.error('Failed to load metrics', error);
      }
    });
  }

  loadCommonMetrics(): void {
    this.commonMetrics.forEach(metric => {
      this.http.get<ActuatorMetricValue>(`${this.baseUrl}/metrics/${metric.key}`).subscribe({
        next: (data) => {
          const value = data.measurements.find(m => m.statistic === 'VALUE')?.value || 0;
          this.metricValues.set(metric.key, value);
        },
        error: () => {
          this.metricValues.set(metric.key, 0);
        }
      });
    });
  }

  loadMetricDetail(metricName: string): void {
    this.selectedMetric = metricName;
    this.http.get<ActuatorMetricValue>(`${this.baseUrl}/metrics/${metricName}`).subscribe({
      next: (data) => {
        this.metricValue = data;
      },
      error: (error) => {
        console.error('Failed to load metric detail', error);
        this.alertService.error('Error', 'Failed to load metric details');
      }
    });
  }

  closeMetricDetail(): void {
    this.selectedMetric = null;
    this.metricValue = null;
  }

  setActiveTab(tab: 'health' | 'info' | 'metrics' | 'config'): void {
    this.activeTab = tab;
  }

  getHealthStatusColor(status: string): string {
    switch (status?.toUpperCase()) {
      case 'UP':
        return 'text-green-600 bg-green-100';
      case 'DOWN':
        return 'text-red-600 bg-red-100';
      case 'OUT_OF_SERVICE':
        return 'text-orange-600 bg-orange-100';
      case 'UNKNOWN':
        return 'text-gray-600 bg-gray-100';
      default:
        return 'text-gray-600 bg-gray-100';
    }
  }

  getHealthIcon(status: string): string {
    switch (status?.toUpperCase()) {
      case 'UP':
        return '✓';
      case 'DOWN':
        return '✗';
      case 'OUT_OF_SERVICE':
        return '⚠';
      default:
        return '?';
    }
  }

  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

  formatUptime(seconds: number): string {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${days}d ${hours}h ${minutes}m`;
  }

  formatPercentage(value: number): string {
    return (value * 100).toFixed(2) + '%';
  }

  getMetricValue(key: string): number {
    return this.metricValues.get(key) || 0;
  }

  refreshAll(): void {
    this.alertService.info('Refreshing', 'Reloading all system data...');
    this.loadAllData();
    setTimeout(() => {
      this.alertService.success('Refreshed', 'System data reloaded successfully');
    }, 1000);
  }
}

