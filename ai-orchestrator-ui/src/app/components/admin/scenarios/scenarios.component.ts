import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { Scenario, ScenarioFormData } from '../../../models/admin.model';

@Component({
  selector: 'app-scenarios',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './scenarios.component.html'
})
export class ScenariosComponent implements OnInit {
  scenarios: Scenario[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedScenario: Scenario | null = null;

  formData: ScenarioFormData = this.getEmptyFormData();

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadScenarios();
  }

  loadScenarios(): void {
    this.isLoading = true;
    this.adminService.getScenarios().subscribe({
      next: (scenarios) => {
        this.scenarios = scenarios;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
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
        },
        error: (err) => alert('Failed to update scenario: ' + err.message)
      });
    } else {
      this.adminService.createScenario(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadScenarios();
        },
        error: (err) => alert('Failed to create scenario: ' + err.message)
      });
    }
  }

  toggleStatus(scenario: Scenario): void {
    this.adminService.toggleScenarioStatus(scenario.id, !scenario.active).subscribe({
      next: () => this.loadScenarios(),
      error: () => alert('Failed to toggle status')
    });
  }

  deleteScenario(scenario: Scenario): void {
    if (confirm(`Are you sure you want to delete "${scenario.scenarioName}"?`)) {
      this.adminService.deleteScenario(scenario.id).subscribe({
        next: () => this.loadScenarios(),
        error: () => alert('Failed to delete scenario')
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
          alert(`Test ${result.success ? 'PASSED' : 'FAILED'}\n\nExecution: ${result.executionTimeMs}ms\n\nResult: ${result.testResult}`);
        },
        error: () => alert('Test failed')
      });
    }
  }
}
