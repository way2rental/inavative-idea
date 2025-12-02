import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AlertService } from '../../../services/alert.service';
import { environment } from '../../../../environments/environment';

interface SystemConfig {
  id: number;
  configKey: string;
  configValue: string;
  jsonValue?: string;
  category: string;
  description: string;
  defaultValue: string;
  valueType: string;
  editable: boolean;
  visible: boolean;
  tenantId?: string;
  createdAt: string;
  updatedAt?: string;
}

@Component({
  selector: 'app-system-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './system-config.component.html'
})
export class SystemConfigComponent implements OnInit {
  configs: SystemConfig[] = [];
  filteredConfigs: SystemConfig[] = [];
  categories: string[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedConfig: SystemConfig | null = null;
  selectedCategory = '';
  searchTerm = '';

  formData = this.getEmptyFormData();

  private apiUrl = `${environment.apiUrl}/api/admin/config`;

  constructor(
    private http: HttpClient,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadConfigs();
    this.loadCategories();
  }

  loadConfigs(): void {
    this.isLoading = true;
    this.http.get<SystemConfig[]>(this.apiUrl).subscribe({
      next: (configs) => {
        this.configs = configs;
        this.applyFilters();
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load configurations');
      }
    });
  }

  loadCategories(): void {
    this.http.get<string[]>(`${this.apiUrl}/categories`).subscribe({
      next: (categories) => {
        this.categories = categories;
      },
      error: () => {
        // Categories are non-critical, silently fallback to empty
        this.categories = [];
      }
    });
  }

  getEmptyFormData() {
    return {
      configKey: '',
      configValue: '',
      jsonValue: '',
      category: 'CUSTOM',
      description: '',
      defaultValue: '',
      valueType: 'STRING',
      editable: true,
      visible: true,
      tenantId: ''
    };
  }

  applyFilters(): void {
    this.filteredConfigs = this.configs.filter(config => {
      const matchesCategory = !this.selectedCategory || config.category === this.selectedCategory;
      const matchesSearch = !this.searchTerm || 
        config.configKey.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        config.description?.toLowerCase().includes(this.searchTerm.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedConfig = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(config: SystemConfig): void {
    if (!config.editable) {
      this.alertService.error('Error', 'This configuration is not editable');
      return;
    }
    this.isEditing = true;
    this.selectedConfig = config;
    this.formData = {
      configKey: config.configKey,
      configValue: config.configValue,
      jsonValue: config.jsonValue || '',
      category: config.category,
      description: config.description,
      defaultValue: config.defaultValue,
      valueType: config.valueType,
      editable: config.editable,
      visible: config.visible,
      tenantId: config.tenantId || ''
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedConfig = null;
  }

  saveConfig(): void {
    if (this.isEditing && this.selectedConfig) {
      this.http.put<SystemConfig>(`${this.apiUrl}/${this.selectedConfig.id}`, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadConfigs();
          this.alertService.success('Success', 'Configuration updated successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to update configuration')
      });
    } else {
      this.http.post<SystemConfig>(this.apiUrl, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadConfigs();
          this.alertService.success('Success', 'Configuration created successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to create configuration')
      });
    }
  }

  resetToDefault(config: SystemConfig): void {
    if (confirm(`Reset "${config.configKey}" to default value "${config.defaultValue}"?`)) {
      this.http.post<SystemConfig>(`${this.apiUrl}/key/${config.configKey}/reset`, {}).subscribe({
        next: () => {
          this.loadConfigs();
          this.alertService.success('Success', 'Configuration reset to default');
        },
        error: () => this.alertService.error('Error', 'Failed to reset configuration')
      });
    }
  }

  deleteConfig(config: SystemConfig): void {
    if (confirm(`Are you sure you want to delete "${config.configKey}"?`)) {
      this.http.delete(`${this.apiUrl}/${config.id}`).subscribe({
        next: () => {
          this.loadConfigs();
          this.alertService.success('Deleted', 'Configuration deleted successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to delete configuration')
      });
    }
  }

  refreshCache(): void {
    this.http.post(`${this.apiUrl}/cache/refresh`, {}).subscribe({
      next: () => {
        this.alertService.success('Success', 'Configuration cache refreshed');
      },
      error: () => this.alertService.error('Error', 'Failed to refresh cache')
    });
  }

  getConfigsByCategory(category: string): SystemConfig[] {
    return this.filteredConfigs.filter(c => c.category === category);
  }

  getConfigCountByCategory(category: string): number {
    return this.filteredConfigs.filter(c => c.category === category).length;
  }

  hasCategoryConfigs(category: string): boolean {
    return this.filteredConfigs.some(c => c.category === category);
  }

  getCategoryColor(category: string): string {
    switch (category) {
      case 'BRANDING': return 'bg-purple-100 text-purple-700';
      case 'PERFORMANCE': return 'bg-blue-100 text-blue-700';
      case 'SECURITY': return 'bg-red-100 text-red-700';
      case 'MESSAGES': return 'bg-green-100 text-green-700';
      case 'LLM': return 'bg-amber-100 text-amber-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  }

  getValueTypeIcon(type: string): string {
    switch (type) {
      case 'STRING': return '📝';
      case 'LONG': return '🔢';
      case 'DOUBLE': return '🔢';
      case 'BOOLEAN': return '✓✗';
      case 'JSON': return '{}';
      default: return '📄';
    }
  }
}
