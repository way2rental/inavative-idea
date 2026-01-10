import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';

@Component({
  selector: 'app-intelligence-layers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './intelligence-layers.component.html'
})
export class IntelligenceLayersComponent implements OnInit {
  layers: any[] = [];
  filteredLayers: any[] = [];
  isLoading = true;
  showModal = false;
  selectedLayer: any = null;
  isEditing = false;

  // Filter
  filterStatus = '';
  filterType = '';

  // Form data
  formData: any = {
    confidenceThreshold: 0.8,
    priority: 1,
    active: true,
    configJson: '{}'
  };

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadLayers();
  }

  loadLayers(): void {
    this.isLoading = true;
    this.adminService.getFallbackLayers().subscribe({
      next: (layers) => {
        this.layers = layers;
        this.filteredLayers = layers;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load layers: ' + error.message);
      }
    });
  }

  openEditModal(layer: any): void {
    this.isEditing = true;
    this.selectedLayer = layer;
    this.formData = {
      confidenceThreshold: layer.confidenceThreshold || 0.8,
      priority: layer.priority || 1,
      active: layer.active !== undefined ? layer.active : true,
      configJson: layer.configJson || '{}'
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedLayer = null;
    this.isEditing = false;
  }

  saveLayer(): void {
    if (!this.selectedLayer) return;

    this.adminService.updateFallbackLayer(this.selectedLayer.id, this.formData).subscribe({
      next: () => {
        this.closeModal();
        this.loadLayers();
        this.alertService.success('Success', 'Layer updated successfully');
        // Refresh layers from database
        this.refreshLayers();
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to update layer: ' + error.message);
      }
    });
  }

  toggleStatus(layer: any): void {
    const update = {
      active: !layer.active,
      confidenceThreshold: layer.confidenceThreshold,
      priority: layer.priority,
      configJson: layer.configJson
    };
    this.adminService.updateFallbackLayer(layer.id, update).subscribe({
      next: () => {
        this.loadLayers();
        this.alertService.success('Success', `Layer ${!layer.active ? 'enabled' : 'disabled'} successfully`);
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to toggle layer status');
      }
    });
  }

  refreshLayers(): void {
    this.adminService.refreshLayers().subscribe({
      next: () => {
        this.loadLayers();
        this.alertService.success('Success', 'Layers refreshed from database');
      },
      error: (error) => {
        this.alertService.error('Error', 'Failed to refresh layers');
      }
    });
  }

  applyFilters(): void {
    this.filteredLayers = this.layers.filter(layer => {
      const matchesStatus = !this.filterStatus || 
        (this.filterStatus === 'active' && layer.active) ||
        (this.filterStatus === 'inactive' && !layer.active);
      const matchesType = !this.filterType || layer.layerType === this.filterType;
      return matchesStatus && matchesType;
    });
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterType = '';
    this.filteredLayers = this.layers;
  }

  getLayerTypeColor(type: string): string {
    const colors: Record<string, string> = {
      'ML': 'bg-purple-100 text-purple-700',
      'EMBEDDING': 'bg-blue-100 text-blue-700',
      'RULES': 'bg-green-100 text-green-700',
      'KEYWORDS': 'bg-amber-100 text-amber-700',
      'CONVERSATIONAL': 'bg-gray-100 text-gray-700'
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
