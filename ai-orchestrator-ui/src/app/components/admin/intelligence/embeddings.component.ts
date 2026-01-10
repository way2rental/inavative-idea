import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';

@Component({
  selector: 'app-embeddings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './embeddings.component.html'
})
export class EmbeddingsComponent implements OnInit {
  embeddings: any[] = [];
  filteredEmbeddings: any[] = [];
  isLoading = true;
  isGenerating = false;
  showGenerateModal = false;
  selectedScenarioCode = '';

  // Filter
  filterScenarioCode = '';
  filterStatus = '';

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadEmbeddings();
  }

  loadEmbeddings(): void {
    this.isLoading = true;
    this.adminService.getEmbeddings().subscribe({
      next: (embeddings) => {
        this.embeddings = embeddings;
        this.filteredEmbeddings = embeddings;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load embeddings: ' + error.message);
      }
    });
  }

  openGenerateModal(scenarioCode?: string): void {
    this.selectedScenarioCode = scenarioCode || '';
    this.showGenerateModal = true;
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
    this.selectedScenarioCode = '';
  }

  generateEmbedding(): void {
    if (!this.selectedScenarioCode) {
      this.alertService.error('Validation Error', 'Please enter a scenario code');
      return;
    }

    this.isGenerating = true;
    this.adminService.generateEmbedding(this.selectedScenarioCode).subscribe({
      next: () => {
        this.isGenerating = false;
        this.closeGenerateModal();
        this.loadEmbeddings();
        this.alertService.success('Success', 'Embedding generated successfully');
      },
      error: (error) => {
        this.isGenerating = false;
        this.alertService.error('Error', 'Failed to generate embedding: ' + error.message);
      }
    });
  }

  generateAllEmbeddings(): void {
    if (!confirm('This will generate embeddings for all scenarios. This may take a while. Continue?')) {
      return;
    }

    this.isGenerating = true;
    this.adminService.generateAllEmbeddings().subscribe({
      next: (result) => {
        this.isGenerating = false;
        this.loadEmbeddings();
        this.alertService.success('Success', `Generated ${result.count} embeddings successfully`);
      },
      error: (error) => {
        this.isGenerating = false;
        this.alertService.error('Error', 'Failed to generate embeddings: ' + error.message);
      }
    });
  }

  refreshEmbedding(embedding: any): void {
    this.isGenerating = true;
    this.adminService.refreshEmbedding(embedding.scenarioCode).subscribe({
      next: () => {
        this.isGenerating = false;
        this.loadEmbeddings();
        this.alertService.success('Success', 'Embedding refreshed successfully');
      },
      error: (error) => {
        this.isGenerating = false;
        this.alertService.error('Error', 'Failed to refresh embedding');
      }
    });
  }

  applyFilters(): void {
    this.filteredEmbeddings = this.embeddings.filter(embedding => {
      const matchesCode = !this.filterScenarioCode ||
        embedding.scenarioCode.toLowerCase().includes(this.filterScenarioCode.toLowerCase());
      const matchesStatus = !this.filterStatus ||
        (this.filterStatus === 'active' && embedding.active) ||
        (this.filterStatus === 'inactive' && !embedding.active);
      return matchesCode && matchesStatus;
    });
  }

  clearFilters(): void {
    this.filterScenarioCode = '';
    this.filterStatus = '';
    this.filteredEmbeddings = this.embeddings;
  }

  getVectorDimension(embedding: any): number {
    if (!embedding.embeddingVector) return 0;
    try {
      const vector = JSON.parse(embedding.embeddingVector);
      return Array.isArray(vector) ? vector.length : 0;
    } catch {
      return 0;
    }
  }

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleString();
  }
}
