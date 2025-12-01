import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { IntentConfig, IntentFormData } from '../../../models/admin.model';

@Component({
  selector: 'app-intents',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './intents.component.html'
})
export class IntentsComponent implements OnInit {
  intents: IntentConfig[] = [];
  categories: string[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedIntent: IntentConfig | null = null;
  newPhrase = '';
  selectedCategory = '';

  formData: IntentFormData = this.getEmptyFormData();

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadIntents();
    this.loadCategories();
  }

  loadIntents(): void {
    this.isLoading = true;
    this.adminService.getIntents().subscribe({
      next: (intents) => {
        this.intents = intents;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  loadCategories(): void {
    this.adminService.getIntentCategories().subscribe({
      next: (categories) => {
        this.categories = categories;
      }
    });
  }

  getEmptyFormData(): IntentFormData {
    return {
      intentKey: '',
      intentName: '',
      description: '',
      trainingPhrases: [],
      confidenceThreshold: 0.72,
      followupGroup: '',
      scenarioCode: '',
      category: '',
      priority: 0,
      active: true
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedIntent = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(intent: IntentConfig): void {
    this.isEditing = true;
    this.selectedIntent = intent;
    this.formData = {
      intentKey: intent.intentKey,
      intentName: intent.intentName,
      description: intent.description,
      trainingPhrases: [...intent.trainingPhrases],
      confidenceThreshold: intent.confidenceThreshold,
      followupGroup: intent.followupGroup,
      scenarioCode: intent.scenarioCode,
      category: intent.category,
      priority: intent.priority,
      active: intent.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedIntent = null;
    this.newPhrase = '';
  }

  addPhrase(): void {
    if (this.newPhrase.trim()) {
      this.formData.trainingPhrases.push(this.newPhrase.trim());
      this.newPhrase = '';
    }
  }

  removePhrase(index: number): void {
    this.formData.trainingPhrases.splice(index, 1);
  }

  saveIntent(): void {
    if (this.isEditing && this.selectedIntent) {
      this.adminService.updateIntent(this.selectedIntent.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadIntents();
          this.loadCategories();
        },
        error: (err) => alert('Failed to update intent: ' + err.message)
      });
    } else {
      this.adminService.createIntent(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadIntents();
          this.loadCategories();
        },
        error: (err) => alert('Failed to create intent: ' + err.message)
      });
    }
  }

  toggleStatus(intent: IntentConfig): void {
    this.adminService.toggleIntentStatus(intent.id, !intent.active).subscribe({
      next: () => this.loadIntents(),
      error: () => alert('Failed to toggle status')
    });
  }

  deleteIntent(intent: IntentConfig): void {
    if (confirm(`Are you sure you want to delete "${intent.intentName}"?`)) {
      this.adminService.deleteIntent(intent.id).subscribe({
        next: () => this.loadIntents(),
        error: () => alert('Failed to delete intent')
      });
    }
  }

  filterByCategory(category: string): void {
    this.selectedCategory = category;
    if (category) {
      this.adminService.getIntentsByCategory(category).subscribe({
        next: (intents) => {
          this.intents = intents;
        }
      });
    } else {
      this.loadIntents();
    }
  }

  get filteredIntents(): IntentConfig[] {
    return this.intents;
  }
}
