import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { Scenario, ScenarioFormData } from '../../../models/admin.model';

@Component({
  selector: 'app-scenarios',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './scenarios.component.html'
})
export class ScenariosComponent implements OnInit {
  scenarios: Scenario[] = [];
  filteredScenarios: Scenario[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedScenario: Scenario | null = null;
  activeTab = 'basic'; // basic, query, filters, ai, advanced

  // Filter properties
  filterScenarioCode = '';
  filterCategory = '';
  filterExecutionType = '';
  filterStatus = '';

  // Categories for dropdown
  categories = ['Account', 'Transaction', 'Payment', 'Card', 'Loan', 'Investment', 'Analytics', 'Other'];
  icons = ['wallet', 'receipt', 'send', 'credit-card', 'building-bank', 'piggy-bank', 'chart-pie', 'file-text', 'users', 'settings'];

  formData: ScenarioFormData = this.getEmptyFormData();

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadScenarios();
  }

  loadScenarios(): void {
    this.isLoading = true;
    this.adminService.getScenarios().subscribe({
      next: (scenarios) => {
        this.scenarios = scenarios;
        this.filteredScenarios = scenarios;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load scenarios: ' + error.message);
      }
    });
  }

  getEmptyFormData(): ScenarioFormData {
    return {
      scenarioCode: '',
      scenarioName: '',
      description: '',
      llmPromptTemplate: '', // Response Template (Freemarker)
      requiredParams: '',
      executionType: 'DB_QUERY',
      httpMethod: 'GET',
      httpUrl: '',
      httpHeaders: '',
      sqlQuery: '',
      requestMapping: '',
      responseMapping: '',
      timeoutMs: 5000,
      active: true,
      // AI Intent Detection fields
      triggerPhrases: '',
      exampleQueries: '',
      category: 'Other',
      displayOrder: 0,
      icon: 'file-text',
      // Multi-filter engine fields
      filterDefinitions: '',
      securityFilters: '',
      maxResults: 100,
      defaultSort: ''
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedScenario = null;
    this.formData = this.getEmptyFormData();
    this.activeTab = 'basic';
    this.showModal = true;
  }

  openEditModal(scenario: Scenario): void {
    this.isEditing = true;
    this.selectedScenario = scenario;
    this.formData = {
      scenarioCode: scenario.scenarioCode,
      scenarioName: scenario.scenarioName || '',
      description: scenario.description || '',
      llmPromptTemplate: scenario.llmPromptTemplate || '', // Response template (Freemarker)
      requiredParams: scenario.requiredParams?.join(', ') || '',
      executionType: scenario.executionType,
      httpMethod: scenario.httpMethod || 'GET',
      httpUrl: scenario.httpUrl || '',
      httpHeaders: scenario.httpHeaders || '',
      sqlQuery: scenario.sqlQuery || '',
      requestMapping: scenario.requestMapping || '',
      responseMapping: scenario.responseMapping || '',
      timeoutMs: scenario.timeoutMs || 5000,
      active: scenario.active,
      // AI Intent Detection fields
      triggerPhrases: scenario.triggerPhrases || '',
      exampleQueries: scenario.exampleQueries || '',
      category: scenario.category || 'Other',
      displayOrder: scenario.displayOrder || 0,
      icon: scenario.icon || 'file-text',
      // Multi-filter engine fields
      filterDefinitions: scenario.filterDefinitions || '',
      securityFilters: scenario.securityFilters || '',
      maxResults: scenario.maxResults || 100,
      defaultSort: scenario.defaultSort || ''
    };
    this.activeTab = 'basic';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedScenario = null;
  }

  saveScenario(): void {
    // Validate required fields
    if (!this.formData.scenarioCode || !this.formData.scenarioName) {
      this.alertService.error('Validation Error', 'Scenario Code and Name are required');
      return;
    }

    if (this.isEditing && this.selectedScenario) {
      this.adminService.updateScenario(this.selectedScenario.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadScenarios();
          this.alertService.success('Success', 'Scenario updated successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to update scenario: ' + err.message)
      });
    } else {
      this.adminService.createScenario(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadScenarios();
          this.alertService.success('Success', 'Scenario created successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to create scenario: ' + err.message)
      });
    }
  }

  toggleStatus(scenario: Scenario): void {
    this.adminService.toggleScenarioStatus(scenario.id, !scenario.active).subscribe({
      next: () => {
        this.loadScenarios();
        this.alertService.success('Success', `Scenario ${!scenario.active ? 'enabled' : 'disabled'} successfully`);
      },
      error: () => this.alertService.error('Error', 'Failed to toggle status')
    });
  }

  applyFilters(): void {
    this.filteredScenarios = this.scenarios.filter(scenario => {
      const matchesCode = !this.filterScenarioCode ||
        scenario.scenarioCode.toLowerCase().includes(this.filterScenarioCode.toLowerCase()) ||
        (scenario.scenarioName && scenario.scenarioName.toLowerCase().includes(this.filterScenarioCode.toLowerCase()));

      const matchesCategory = !this.filterCategory ||
        scenario.category === this.filterCategory;

      const matchesType = !this.filterExecutionType ||
        scenario.executionType === this.filterExecutionType;

      const matchesStatus = !this.filterStatus ||
        (this.filterStatus === 'active' && scenario.active) ||
        (this.filterStatus === 'inactive' && !scenario.active);

      return matchesCode && matchesCategory && matchesType && matchesStatus;
    });
  }

  clearFilters(): void {
    this.filterScenarioCode = '';
    this.filterCategory = '';
    this.filterExecutionType = '';
    this.filterStatus = '';
    this.filteredScenarios = this.scenarios;
  }

  viewDetails(scenario: Scenario): void {
    this.selectedScenario = scenario;
  }

  closeDetails(): void {
    this.selectedScenario = null;
  }

  async deleteScenario(scenario: Scenario): Promise<void> {
    const confirmed = await this.alertService.confirm(
      'Delete Scenario',
      `Are you sure you want to delete "${scenario.scenarioName || scenario.scenarioCode}"? This action cannot be undone.`,
      'Delete',
      'Cancel'
    );

    if (confirmed) {
      this.adminService.deleteScenario(scenario.id).subscribe({
        next: () => {
          this.loadScenarios();
          this.alertService.success('Deleted', 'Scenario deleted successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to delete scenario')
      });
    }
  }

  testScenario(scenario: Scenario): void {
    const params: Record<string, string> = {};
    scenario.requiredParams.forEach(param => {
      const value = prompt(`Enter value for ${param}:`);
      if (value) params[param] = value;
    });

    if (Object.keys(params).length === scenario.requiredParams.length) {
      this.adminService.testScenario({
        scenarioCode: scenario.scenarioCode,
        testParams: params,
        dryRun: true
      }).subscribe({
        next: (result) => {
          if (result.success) {
            this.alertService.success(
              'Test Passed',
              `Execution completed in ${result.executionTimeMs}ms`
            );
          } else {
            this.alertService.error(
              'Test Failed',
              `Execution time: ${result.executionTimeMs}ms\nError: ${result.testResult}`
            );
          }
        },
        error: () => this.alertService.error('Error', 'Test failed')
      });
    }
  }

  // Helper for JSON formatting
  formatJson(json: string | undefined): string {
    if (!json) return '';
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }

  // Get icon class
  getIconClass(icon: string | undefined): string {
    const iconMap: Record<string, string> = {
      'wallet': 'M21 12V7H5a2 2 0 0 1 0-4h14v4',
      'receipt': 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2',
      'credit-card': 'M3 10h18M7 15h.01M11 15h2M6 19h12a2 2 0 002-2V7a2 2 0 00-2-2H6a2 2 0 00-2 2v10a2 2 0 002 2z',
      'chart-pie': 'M11 3.055A9.001 9.001 0 1020.945 13H11V3.055z M20.488 9H15V3.512A9.025 9.025 0 0120.488 9z'
    };
    return iconMap[icon || 'file-text'] || iconMap['file-text'];
  }

  getCategoryColor(category: string | undefined): string {
    const colors: Record<string, string> = {
      'Account': 'badge-info',
      'Transaction': 'badge-success',
      'Payment': 'badge-primary',
      'Card': 'badge-warning',
      'Loan': 'badge-danger',
      'Investment': 'badge-primary',
      'Analytics': 'badge-info'
    };
    return colors[category || 'Other'] || 'badge-secondary';
  }
}
