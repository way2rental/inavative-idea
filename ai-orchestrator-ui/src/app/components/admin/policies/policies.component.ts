import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { PolicyRule, PolicyFormData } from '../../../models/admin.model';

@Component({
  selector: 'app-policies',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './policies.component.html'
})
export class PoliciesComponent implements OnInit {
  policies: PolicyRule[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedPolicy: PolicyRule | null = null;
  showTestModal = false;
  testContext: Record<string, any> = {};
  testResult: any = null;

  formData: PolicyFormData = this.getEmptyFormData();
  newScenario = '';
  newRole = '';

  onFailOptions = ['BLOCK', 'WARN', 'LOG'];

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadPolicies();
  }

  loadPolicies(): void {
    this.isLoading = true;
    this.adminService.getPolicies().subscribe({
      next: (policies) => {
        this.policies = policies;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load policies');
      }
    });
  }

  getEmptyFormData(): PolicyFormData {
    return {
      policyKey: '',
      policyName: '',
      description: '',
      ruleExpression: '',
      onFail: 'BLOCK',
      failureMessage: '',
      applicableScenarios: [],
      applicableRoles: [],
      priority: 0,
      active: true
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedPolicy = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(policy: PolicyRule): void {
    this.isEditing = true;
    this.selectedPolicy = policy;
    this.formData = {
      policyKey: policy.policyKey,
      policyName: policy.policyName,
      description: policy.description,
      ruleExpression: policy.ruleExpression,
      onFail: policy.onFail,
      failureMessage: policy.failureMessage,
      applicableScenarios: [...policy.applicableScenarios],
      applicableRoles: [...policy.applicableRoles],
      priority: policy.priority,
      active: policy.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedPolicy = null;
    this.newScenario = '';
    this.newRole = '';
  }

  addScenario(): void {
    if (this.newScenario.trim() && !this.formData.applicableScenarios.includes(this.newScenario.trim())) {
      this.formData.applicableScenarios.push(this.newScenario.trim());
      this.newScenario = '';
    }
  }

  removeScenario(index: number): void {
    this.formData.applicableScenarios.splice(index, 1);
  }

  addRole(): void {
    if (this.newRole.trim() && !this.formData.applicableRoles.includes(this.newRole.trim())) {
      this.formData.applicableRoles.push(this.newRole.trim());
      this.newRole = '';
    }
  }

  removeRole(index: number): void {
    this.formData.applicableRoles.splice(index, 1);
  }

  savePolicy(): void {
    if (this.isEditing && this.selectedPolicy) {
      this.adminService.updatePolicy(this.selectedPolicy.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPolicies();
          this.alertService.success('Success', 'Policy updated successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to update policy: ' + err.message)
      });
    } else {
      this.adminService.createPolicy(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPolicies();
          this.alertService.success('Success', 'Policy created successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to create policy: ' + err.message)
      });
    }
  }

  toggleStatus(policy: PolicyRule): void {
    this.adminService.togglePolicyStatus(policy.id, !policy.active).subscribe({
      next: () => this.loadPolicies(),
      error: () => this.alertService.error('Error', 'Failed to toggle status')
    });
  }

  deletePolicy(policy: PolicyRule): void {
    if (confirm(`Are you sure you want to delete "${policy.policyName}"?`)) {
      this.adminService.deletePolicy(policy.id).subscribe({
        next: () => {
          this.loadPolicies();
          this.alertService.success('Deleted', 'Policy deleted successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to delete policy')
      });
    }
  }

  openTestModal(policy: PolicyRule): void {
    this.selectedPolicy = policy;
    this.testContext = { query: '', userId: '' };
    this.testResult = null;
    this.showTestModal = true;
  }

  closeTestModal(): void {
    this.showTestModal = false;
    this.testContext = {};
    this.testResult = null;
  }

  runTest(): void {
    if (this.selectedPolicy) {
      this.adminService.testPolicy(this.selectedPolicy.id, this.testContext).subscribe({
        next: (result) => {
          this.testResult = result;
        },
        error: () => this.alertService.error('Error', 'Test failed')
      });
    }
  }

  getOnFailClass(onFail: string): string {
    switch (onFail) {
      case 'BLOCK': return 'badge-danger';
      case 'WARN': return 'badge-warning';
      case 'LOG': return 'badge-info';
      default: return 'badge bg-gray-100 text-gray-600';
    }
  }
}
