import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { RoleScenarioMapping, RbacMatrix } from '../../../models/admin.model';

@Component({
  selector: 'app-rbac-management',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './rbac-management.component.html'
})
export class RbacManagementComponent implements OnInit {
  matrix: RbacMatrix = { roles: [], scenarios: [], mappings: [] };
  isLoading = true;
  viewMode: 'matrix' | 'list' = 'matrix';

  showBulkModal = false;
  selectedRole = '';
  selectedScenarios: Set<string> = new Set();

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadMatrix();
  }

  loadMatrix(): void {
    this.isLoading = true;
    this.adminService.getRbacMatrix().subscribe({
      next: (matrix) => {
        this.matrix = matrix;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load RBAC matrix:', err);
        this.isLoading = false;
      }
    });
  }

  hasAccess(role: string, scenario: string): boolean {
    return this.matrix.mappings.some(m => m.roleName === role && m.scenarioCode === scenario);
  }

  toggleAccess(role: string, scenario: string, event: any): void {
    const checked = event.target.checked;
    if (checked) {
      this.adminService.grantRbacAccess(role, scenario).subscribe({
        next: () => this.loadMatrix(),
        error: (err) => console.error('Failed to grant access:', err)
      });
    } else {
      this.adminService.revokeRbacAccessByRoleAndScenario(role, scenario).subscribe({
        next: () => this.loadMatrix(),
        error: (err) => console.error('Failed to revoke access:', err)
      });
    }
  }

  openBulkModal(): void {
    this.selectedRole = this.matrix.roles[0] || '';
    this.selectedScenarios.clear();
    this.showBulkModal = true;
  }

  closeBulkModal(): void {
    this.showBulkModal = false;
  }

  toggleScenarioSelection(scenario: string): void {
    if (this.selectedScenarios.has(scenario)) {
      this.selectedScenarios.delete(scenario);
    } else {
      this.selectedScenarios.add(scenario);
    }
  }

  bulkAssign(): void {
    const scenarioCodes = Array.from(this.selectedScenarios);
    if (scenarioCodes.length === 0) {
      alert('Please select at least one scenario');
      return;
    }
    this.adminService.bulkGrantRbacAccess({ roleName: this.selectedRole, scenarioCodes }).subscribe({
      next: () => {
        this.loadMatrix();
        this.closeBulkModal();
      },
      error: (err) => console.error('Failed to bulk assign:', err)
    });
  }

  refreshCache(): void {
    this.adminService.refreshRbacCache().subscribe({
      next: (response) => {
        alert(response);
        this.loadMatrix();
      },
      error: (err) => console.error('Failed to refresh cache:', err)
    });
  }

  exportMatrix(): void {
    let csv = 'Role,Scenario,Access\n';
    this.matrix.roles.forEach(role => {
      this.matrix.scenarios.forEach(scenario => {
        const hasAccess = this.hasAccess(role, scenario);
        csv += `${role},${scenario},${hasAccess ? 'YES' : 'NO'}\n`;
      });
    });
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'rbac-matrix.csv';
    a.click();
  }
}

