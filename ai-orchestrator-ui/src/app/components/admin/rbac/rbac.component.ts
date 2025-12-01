import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { Scenario } from '../../../models/admin.model';

interface RoleMapping {
  role: string;
  scenarios: string[];
}

interface RbacStatus {
  initialized: boolean;
  totalRoles: number;
  totalMappings: number;
}

@Component({
  selector: 'app-rbac',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './rbac.component.html'
})
export class RbacComponent implements OnInit {
  roleMappings: RoleMapping[] = [];
  scenarios: Scenario[] = [];
  rbacStatus: RbacStatus | null = null;
  isLoading = true;

  // Modal state
  showAddModal = false;
  availableRoles = ['USER', 'OPERATOR', 'ADMIN'];
  selectedRole = '';
  selectedScenario = '';

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading = true;

    // Load scenarios
    this.adminService.getScenarios().subscribe(scenarios => {
      this.scenarios = scenarios;
    });

    // Load RBAC status
    this.adminService.getRbacStatus().subscribe(status => {
      this.rbacStatus = status;
    });

    // Load role mappings
    this.adminService.getRbacMappings().subscribe({
      next: (mappings) => {
        this.roleMappings = Object.entries(mappings).map(([role, scenarios]) => ({
          role,
          scenarios: scenarios as string[]
        }));
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  getScenarioName(code: string): string {
    const scenario = this.scenarios.find(s => s.scenarioCode === code);
    return scenario?.scenarioName || code;
  }

  openAddModal(): void {
    this.selectedRole = '';
    this.selectedScenario = '';
    this.showAddModal = true;
  }

  closeAddModal(): void {
    this.showAddModal = false;
  }

  addMapping(): void {
    if (this.selectedRole && this.selectedScenario) {
      this.adminService.addRbacMapping(this.selectedRole, this.selectedScenario).subscribe({
        next: () => {
          this.closeAddModal();
          this.loadData();
          this.alertService.success('Success', 'Mapping added successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to add mapping')
      });
    }
  }

  async removeMapping(role: string, scenarioCode: string): Promise<void> {
    const confirmed = await this.alertService.confirm(
      'Remove Mapping',
      `Remove "${scenarioCode}" from "${role}"?`,
      'Remove',
      'Cancel'
    );

    if (confirmed) {
      this.adminService.removeRbacMapping(role, scenarioCode).subscribe({
        next: () => {
          this.loadData();
          this.alertService.success('Success', 'Mapping removed successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to remove mapping')
      });
    }
  }

  refreshCache(): void {
    this.adminService.refreshRbacCache().subscribe({
      next: () => {
        this.alertService.success('Cache Refreshed', 'RBAC cache refreshed successfully!');
        this.loadData();
      },
      error: () => this.alertService.error('Error', 'Failed to refresh cache')
    });
  }

  getUnmappedScenarios(role: string): Scenario[] {
    const mapping = this.roleMappings.find(m => m.role === role);
    const mappedCodes = mapping?.scenarios || [];
    return this.scenarios.filter(s => !mappedCodes.includes(s.scenarioCode) && s.active);
  }
}
