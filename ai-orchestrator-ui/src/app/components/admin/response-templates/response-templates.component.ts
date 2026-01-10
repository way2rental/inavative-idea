import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { ResponseTemplate, ResponseTemplateFormData, Scenario } from '../../../models/admin.model';

@Component({
  selector: 'app-response-templates',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './response-templates.component.html'
})
export class ResponseTemplatesComponent implements OnInit {
  templates: ResponseTemplate[] = [];
  scenarios: Scenario[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedTemplate: ResponseTemplate | null = null;
  filteredTemplates: ResponseTemplate[] = [];
  filterScenario = '';

  formData: ResponseTemplateFormData = this.getEmptyFormData();

  // Static content - moved from template
  readonly responseTypes = ['TEXT', 'TABLE', 'KV', 'MIXED', 'FOLLOW_UP', 'ERROR'];
  readonly templateContentPlaceholder = `Enter Freemarker template...

Available variables:
- result: ScenarioResult object
- data: Response data
- scenarioCode: Scenario code
- scenarioName: Scenario name
- userQuery: User's query
- assistantName: Assistant name
- orgName: Organization name
- currencySymbol: Currency symbol`;

  readonly templateContentHelp = 'Use Freemarker syntax: ${variable}, ${variable.field}, <#list items as item>...</#list>';
  readonly templateVariablesPlaceholder = '{"variables": ["result", "data", "scenarioCode"]}';
  readonly templateVariablesHelp = 'Optional: Document available variables as JSON';
  readonly conditionsPlaceholder = '{"hasData": true, "dataType": "table"}';
  readonly conditionsHelp = 'Optional: Conditions for when this template should be used';
  readonly priorityHelp = 'Higher priority templates are selected first';

  readonly labels = {
    pageTitle: 'Response Templates',
    pageDescription: 'Manage Freemarker templates for scenario responses',
    addTemplate: 'Add Template',
    filterByScenario: 'Filter by Scenario:',
    allScenarios: 'All Scenarios',
    noTemplates: 'No response templates found. Click "Add Template" to create one.',
    createTemplate: 'Create Response Template',
    editTemplate: 'Edit Response Template',
    scenarioCode: 'Scenario Code',
    responseType: 'Response Type',
    priority: 'Priority',
    templateVersion: 'Template Version',
    templateContent: 'Template Content (Freemarker)',
    templateVariables: 'Template Variables (JSON)',
    conditions: 'Conditions (JSON)',
    active: 'Active',
    cancel: 'Cancel',
    saveTemplate: 'Save Template',
    edit: 'Edit',
    delete: 'Delete',
    disable: 'Disable',
    enable: 'Enable',
    selectScenario: 'Select Scenario'
  };

  readonly tableHeaders = {
    scenario: 'Scenario',
    responseType: 'Response Type',
    priority: 'Priority',
    version: 'Version',
    status: 'Status',
    templatePreview: 'Template Preview',
    actions: 'Actions'
  };

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadTemplates();
    this.loadScenarios();
  }

  loadTemplates(): void {
    this.isLoading = true;
    this.adminService.getResponseTemplates().subscribe({
      next: (templates) => {
        this.templates = templates;
        this.applyFilter();
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load response templates: ' + error.message);
      }
    });
  }

  loadScenarios(): void {
    this.adminService.getScenarios().subscribe({
      next: (scenarios) => {
        this.scenarios = scenarios.filter(s => s.active);
      },
      error: (error) => {
        // Log error but don't show alert - scenarios are optional for filtering
        console.warn('Failed to load scenarios for filtering:', error);
      }
    });
  }

  applyFilter(): void {
    if (!this.filterScenario) {
      this.filteredTemplates = this.templates;
    } else {
      this.filteredTemplates = this.templates.filter(t => t.scenarioCode === this.filterScenario);
    }
  }

  getEmptyFormData(): ResponseTemplateFormData {
    return {
      scenarioCode: '',
      responseType: 'TEXT',
      templateContent: '',
      templateVariables: '',
      conditions: '',
      priority: 0,
      templateVersion: 1,
      active: true
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedTemplate = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(template: ResponseTemplate): void {
    this.isEditing = true;
    this.selectedTemplate = template;
    this.formData = {
      scenarioCode: template.scenarioCode,
      responseType: template.responseType,
      templateContent: template.templateContent,
      templateVariables: template.templateVariables || '',
      conditions: template.conditions || '',
      priority: template.priority,
      templateVersion: template.templateVersion,
      active: template.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedTemplate = null;
  }

  saveTemplate(): void {
    if (!this.formData.scenarioCode || !this.formData.templateContent) {
      this.alertService.error('Validation Error', 'Scenario code and template content are required');
      return;
    }

    if (this.isEditing && this.selectedTemplate) {
      this.adminService.updateResponseTemplate(this.selectedTemplate.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadTemplates();
          this.alertService.success('Success', 'Response template updated successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to update template: ' + (err.error?.message || err.message))
      });
    } else {
      this.adminService.createResponseTemplate(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadTemplates();
          this.alertService.success('Success', 'Response template created successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to create template: ' + (err.error?.message || err.message))
      });
    }
  }

  toggleStatus(template: ResponseTemplate): void {
    const action = template.active ? 'disable' : 'enable';
    this.adminService.toggleResponseTemplateStatus(template.id, !template.active).subscribe({
      next: () => {
        this.loadTemplates();
        this.alertService.success('Success', `Template ${action}d successfully`);
      },
      error: (err) => this.alertService.error('Error', `Failed to ${action} template: ` + (err.error?.message || err.message))
    });
  }

  deleteTemplate(template: ResponseTemplate): void {
    const scenarioName = this.getScenarioName(template.scenarioCode);
    const confirmMessage = `Are you sure you want to delete the response template for "${scenarioName}"?`;
    
    if (confirm(confirmMessage)) {
      this.adminService.deleteResponseTemplate(template.id).subscribe({
        next: () => {
          this.loadTemplates();
          this.alertService.success('Success', 'Response template deleted successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to delete template: ' + (err.error?.message || err.message))
      });
    }
  }

  getScenarioName(scenarioCode: string): string {
    const scenario = this.scenarios.find(s => s.scenarioCode === scenarioCode);
    return scenario ? scenario.scenarioName : scenarioCode;
  }

  getModalTitle(): string {
    return this.isEditing ? this.labels.editTemplate : this.labels.createTemplate;
  }

  getToggleButtonText(template: ResponseTemplate): string {
    return template.active ? this.labels.disable : this.labels.enable;
  }
}
