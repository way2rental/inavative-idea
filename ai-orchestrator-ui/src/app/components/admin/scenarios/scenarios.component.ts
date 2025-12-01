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

  // Filter properties
  filterScenarioCode = '';
  filterScenarioName = '';
  filterExecutionType = '';
  filterStatus = '';

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
      llmPromptTemplate: '',
      requiredParams: '',
      optionalParams: '',
      securityLevel: 'NORMAL',
      executionType: 'DB_QUERY',
      httpMethod: 'GET',
      httpUrl: '',
      httpHeaders: '',
      sqlQuery: '',
      requestMapping: '',
      responseMapping: '',
      timeoutMs: 5000,
      executorBean: '',
      active: true
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedScenario = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(scenario: Scenario): void {
    this.isEditing = true;
    this.selectedScenario = scenario;
    this.formData = {
      scenarioCode: scenario.scenarioCode,
      scenarioName: scenario.scenarioName,
      description: scenario.description,
      llmPromptTemplate: scenario.llmPromptTemplate || '',
      requiredParams: scenario.requiredParams.join(', '),
      optionalParams: scenario.optionalParams?.join(', ') || '',
      securityLevel: scenario.securityLevel,
      executionType: scenario.executionType,
      httpMethod: scenario.httpMethod || 'GET',
      httpUrl: scenario.httpUrl || '',
      httpHeaders: scenario.httpHeaders || '',
      sqlQuery: scenario.sqlQuery || '',
      requestMapping: scenario.requestMapping || '',
      responseMapping: scenario.responseMapping || '',
      timeoutMs: scenario.timeoutMs || 5000,
      executorBean: scenario.executorBean || '',
      active: scenario.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedScenario = null;
  }

  saveScenario(): void {
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
        scenario.scenarioCode.toLowerCase().includes(this.filterScenarioCode.toLowerCase());

      const matchesName = !this.filterScenarioName ||
        scenario.scenarioName.toLowerCase().includes(this.filterScenarioName.toLowerCase());

      const matchesType = !this.filterExecutionType ||
        scenario.executionType === this.filterExecutionType;

      const matchesStatus = !this.filterStatus ||
        (this.filterStatus === 'active' && scenario.active) ||
        (this.filterStatus === 'inactive' && !scenario.active);

      return matchesCode && matchesName && matchesType && matchesStatus;
    });
  }

  clearFilters(): void {
    this.filterScenarioCode = '';
    this.filterScenarioName = '';
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
      `Are you sure you want to delete "${scenario.scenarioName}"? This action cannot be undone.`,
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
}
