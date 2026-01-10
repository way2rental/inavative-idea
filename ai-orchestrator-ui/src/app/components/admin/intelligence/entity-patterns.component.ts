import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';

@Component({
  selector: 'app-entity-patterns',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './entity-patterns.component.html'
})
export class EntityPatternsComponent implements OnInit {
  patterns: any[] = [];
  filteredPatterns: any[] = [];
  isLoading = true;
  showModal = false;
  selectedPattern: any = null;
  isEditing = false;

  // Filter
  filterEntityType = '';
  filterPatternType = '';
  filterStatus = '';

  // Form data
  formData: any = {
    entityType: '',
    patternType: 'REGEX',
    patternDefinition: '',
    displayName: '',
    description: '',
    validationRule: '',
    examples: '[]',
    confidenceBoost: 0.0,
    priority: 0,
    active: true,
    configJson: '{}'
  };

  patternTypes = ['REGEX', 'NER_MODEL', 'CONTEXT_BASED', 'VALIDATION'];
  commonEntityTypes = ['ACCOUNT_ID', 'TRANSACTION_ID', 'AMOUNT', 'DATE', 'EMAIL', 'PHONE'];

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadPatterns();
  }

  loadPatterns(): void {
    this.isLoading = true;
    this.adminService.getEntityPatterns().subscribe({
      next: (patterns) => {
        this.patterns = patterns;
        this.filteredPatterns = patterns;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load patterns: ' + error.message);
      }
    });
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedPattern = null;
    this.formData = {
      entityType: '',
      patternType: 'REGEX',
      patternDefinition: '',
      displayName: '',
      description: '',
      validationRule: '',
      examples: '[]',
      confidenceBoost: 0.0,
      priority: 0,
      active: true,
      configJson: '{}'
    };
    this.showModal = true;
  }

  openEditModal(pattern: any): void {
    this.isEditing = true;
    this.selectedPattern = pattern;
    this.formData = {
      entityType: pattern.entityType,
      patternType: pattern.patternType,
      patternDefinition: pattern.patternDefinition,
      displayName: pattern.displayName || '',
      description: pattern.description || '',
      validationRule: pattern.validationRule || '',
      examples: pattern.examples || '[]',
      confidenceBoost: pattern.confidenceBoost || 0.0,
      priority: pattern.priority || 0,
      active: pattern.active !== undefined ? pattern.active : true,
      configJson: pattern.configJson || '{}'
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedPattern = null;
    this.isEditing = false;
  }

  savePattern(): void {
    if (!this.formData.entityType || !this.formData.patternDefinition) {
      this.alertService.error('Validation Error', 'Entity Type and Pattern Definition are required');
      return;
    }

    if (this.isEditing && this.selectedPattern) {
      this.adminService.updateEntityPattern(this.selectedPattern.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPatterns();
          this.alertService.success('Success', 'Pattern updated successfully');
        },
        error: (error) => {
          this.alertService.error('Error', 'Failed to update pattern: ' + error.message);
        }
      });
    } else {
      this.adminService.createEntityPattern(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPatterns();
          this.alertService.success('Success', 'Pattern created successfully');
        },
        error: (error) => {
          this.alertService.error('Error', 'Failed to create pattern: ' + error.message);
        }
      });
    }
  }

  deletePattern(pattern: any): void {
    if (confirm(`Are you sure you want to delete pattern for ${pattern.entityType}?`)) {
      this.adminService.deleteEntityPattern(pattern.id).subscribe({
        next: () => {
          this.loadPatterns();
          this.alertService.success('Success', 'Pattern deleted successfully');
        },
        error: (error) => {
          this.alertService.error('Error', 'Failed to delete pattern');
        }
      });
    }
  }

  toggleStatus(pattern: any): void {
    const update = { ...pattern, active: !pattern.active };
    this.adminService.updateEntityPattern(pattern.id, update).subscribe({
      next: () => {
        this.loadPatterns();
        this.alertService.success('Success', `Pattern ${!pattern.active ? 'enabled' : 'disabled'} successfully`);
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to toggle pattern status');
      }
    });
  }

  applyFilters(): void {
    this.filteredPatterns = this.patterns.filter(pattern => {
      const matchesType = !this.filterEntityType || pattern.entityType === this.filterEntityType;
      const matchesPatternType = !this.filterPatternType || pattern.patternType === this.filterPatternType;
      const matchesStatus = !this.filterStatus || 
        (this.filterStatus === 'active' && pattern.active) ||
        (this.filterStatus === 'inactive' && !pattern.active);
      return matchesType && matchesPatternType && matchesStatus;
    });
  }

  clearFilters(): void {
    this.filterEntityType = '';
    this.filterPatternType = '';
    this.filterStatus = '';
    this.filteredPatterns = this.patterns;
  }

  getPatternTypeColor(type: string): string {
    const colors: Record<string, string> = {
      'REGEX': 'bg-blue-100 text-blue-700',
      'NER_MODEL': 'bg-purple-100 text-purple-700',
      'CONTEXT_BASED': 'bg-green-100 text-green-700',
      'VALIDATION': 'bg-amber-100 text-amber-700'
    };
    return colors[type] || 'bg-gray-100 text-gray-700';
  }

  formatJson(json: string): string {
    if (!json) return '{}';
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }
}
