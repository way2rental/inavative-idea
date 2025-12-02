import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { ResponseMapping, ResponseMappingForm, JsonPathTestRequest } from '../../../models/admin.model';
import { AdminLayoutComponent } from '../../shared/admin-layout/admin-layout.component';

@Component({
  selector: 'app-response-mappings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, AdminLayoutComponent],
  templateUrl: './response-mappings.component.html'
})
export class ResponseMappingsComponent implements OnInit {
  mappings: ResponseMapping[] = [];
  scenarios: string[] = [];
  selectedScenario = '';
  isLoading = true;

  // Pagination
  currentPage = 1;
  pageSize = 10;
  paginatedMappings: ResponseMapping[] = [];

  // Modals
  showFormModal = false;
  showTestModal = false;
  isEditMode = false;
  selectedMapping: ResponseMapping | null = null;

  // Form
  mappingForm: ResponseMappingForm = {
    scenarioCode: '',
    sourceType: 'DB_QUERY',
    sourceField: '',
    targetField: '',
    jsonPath: '',
    maskingType: 'NONE',
    displayOrder: 0,
    active: true
  };

  // JSONPath Tester
  jsonPathTest: JsonPathTestRequest = {
    jsonPath: '',
    sampleJson: ''
  };
  testResult: any = null;

  Math = Math;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadScenarios();
    this.loadMappings();
  }

  loadScenarios(): void {
    this.adminService.getResponseMappingScenarios().subscribe({
      next: (scenarios) => this.scenarios = scenarios,
      error: (err) => console.error('Failed to load scenarios:', err)
    });
  }

  loadMappings(): void {
    this.isLoading = true;
    this.adminService.getResponseMappings(this.selectedScenario || undefined).subscribe({
      next: (mappings) => {
        this.mappings = mappings;
        this.updatePaginatedData();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load mappings:', err);
        this.isLoading = false;
      }
    });
  }

  updatePaginatedData(): void {
    const startIndex = (this.currentPage - 1) * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.paginatedMappings = this.mappings.slice(startIndex, endIndex);
  }

  get totalPages(): number {
    return Math.ceil(this.mappings.length / this.pageSize);
  }

  get startRecord(): number {
    return (this.currentPage - 1) * this.pageSize + 1;
  }

  get endRecord(): number {
    return Math.min(this.currentPage * this.pageSize, this.mappings.length);
  }

  previousPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.updatePaginatedData();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
      this.updatePaginatedData();
    }
  }

  openCreateModal(): void {
    this.isEditMode = false;
    this.mappingForm = {
      scenarioCode: this.selectedScenario || '',
      sourceType: 'DB_QUERY',
      sourceField: '',
      targetField: '',
      jsonPath: '',
      maskingType: 'NONE',
      displayOrder: 0,
      active: true
    };
    this.showFormModal = true;
  }

  openEditModal(mapping: ResponseMapping): void {
    this.isEditMode = true;
    this.selectedMapping = mapping;
    this.mappingForm = { ...mapping };
    this.showFormModal = true;
  }

  closeFormModal(): void {
    this.showFormModal = false;
    this.selectedMapping = null;
  }

  saveMapping(): void {
    if (this.isEditMode && this.selectedMapping) {
      this.adminService.updateResponseMapping(this.selectedMapping.id, this.mappingForm).subscribe({
        next: () => {
          this.loadMappings();
          this.closeFormModal();
        },
        error: (err) => console.error('Failed to update mapping:', err)
      });
    } else {
      this.adminService.createResponseMapping(this.mappingForm).subscribe({
        next: () => {
          this.loadMappings();
          this.closeFormModal();
        },
        error: (err) => console.error('Failed to create mapping:', err)
      });
    }
  }

  deleteMapping(mapping: ResponseMapping): void {
    if (confirm(`Delete mapping for field "${mapping.targetField}"?`)) {
      this.adminService.deleteResponseMapping(mapping.id).subscribe({
        next: () => this.loadMappings(),
        error: (err) => console.error('Failed to delete mapping:', err)
      });
    }
  }

  toggleStatus(mapping: ResponseMapping): void {
    this.adminService.toggleResponseMappingStatus(mapping.id, !mapping.active).subscribe({
      next: () => this.loadMappings(),
      error: (err) => console.error('Failed to toggle status:', err)
    });
  }

  openTestModal(mapping?: ResponseMapping): void {
    if (mapping) {
      this.jsonPathTest.jsonPath = mapping.jsonPath;
    }
    this.jsonPathTest.sampleJson = '{\n  "key": "value"\n}';
    this.testResult = null;
    this.showTestModal = true;
  }

  closeTestModal(): void {
    this.showTestModal = false;
    this.testResult = null;
  }

  testJsonPath(): void {
    this.adminService.testJsonPath(this.jsonPathTest).subscribe({
      next: (result) => this.testResult = result,
      error: (err) => console.error('Failed to test JSONPath:', err)
    });
  }

  refreshCache(): void {
    this.adminService.refreshResponseMappingCache().subscribe({
      next: (response) => {
        alert(response.message);
        this.loadMappings();
      },
      error: (err) => console.error('Failed to refresh cache:', err)
    });
  }

  filterByScenario(): void {
    this.currentPage = 1;
    this.loadMappings();
  }
}

