import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { PromptTemplate, PromptFormData } from '../../../models/admin.model';

@Component({
  selector: 'app-prompts',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './prompts.component.html'
})
export class PromptsComponent implements OnInit {
  prompts: PromptTemplate[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedPrompt: PromptTemplate | null = null;
  showHistoryModal = false;
  promptHistory: any[] = [];
  showTestModal = false;
  testData: Record<string, string> = {};
  testResult: any = null;

  formData: PromptFormData = this.getEmptyFormData();
  categories = ['SYSTEM', 'USER', 'INTENT', 'FORMAT', 'FOLLOWUP'];
  responseFormats = ['TEXT', 'JSON', 'MARKDOWN'];

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadPrompts();
  }

  loadPrompts(): void {
    this.isLoading = true;
    this.adminService.getPrompts().subscribe({
      next: (prompts) => {
        this.prompts = prompts;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  getEmptyFormData(): PromptFormData {
    return {
      promptKey: '',
      category: 'USER',
      systemPrompt: '',
      userTemplate: '',
      responseFormat: 'TEXT',
      temperature: 0.7,
      maxTokens: 1024,
      enabled: true
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedPrompt = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(prompt: PromptTemplate): void {
    this.isEditing = true;
    this.selectedPrompt = prompt;
    this.formData = {
      promptKey: prompt.promptKey,
      category: prompt.category,
      systemPrompt: prompt.systemPrompt,
      userTemplate: prompt.userTemplate,
      responseFormat: prompt.responseFormat,
      temperature: prompt.temperature,
      maxTokens: prompt.maxTokens,
      enabled: prompt.enabled
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedPrompt = null;
  }

  savePrompt(): void {
    if (this.isEditing && this.selectedPrompt) {
      this.adminService.updatePrompt(this.selectedPrompt.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPrompts();
        },
        error: (err) => alert('Failed to update prompt: ' + err.message)
      });
    } else {
      this.adminService.createPrompt(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadPrompts();
        },
        error: (err) => alert('Failed to create prompt: ' + err.message)
      });
    }
  }

  toggleStatus(prompt: PromptTemplate): void {
    this.adminService.togglePromptStatus(prompt.id, !prompt.enabled).subscribe({
      next: () => this.loadPrompts(),
      error: () => alert('Failed to toggle status')
    });
  }

  deletePrompt(prompt: PromptTemplate): void {
    if (confirm(`Are you sure you want to delete "${prompt.promptKey}"?`)) {
      this.adminService.deletePrompt(prompt.id).subscribe({
        next: () => this.loadPrompts(),
        error: () => alert('Failed to delete prompt')
      });
    }
  }

  viewHistory(prompt: PromptTemplate): void {
    this.selectedPrompt = prompt;
    this.adminService.getPromptHistory(prompt.id).subscribe({
      next: (history) => {
        this.promptHistory = history;
        this.showHistoryModal = true;
      },
      error: () => alert('Failed to load history')
    });
  }

  closeHistoryModal(): void {
    this.showHistoryModal = false;
    this.promptHistory = [];
  }

  rollbackToVersion(version: number): void {
    if (this.selectedPrompt && confirm(`Rollback to version ${version}?`)) {
      this.adminService.rollbackPrompt(this.selectedPrompt.id, version).subscribe({
        next: () => {
          this.closeHistoryModal();
          this.loadPrompts();
          alert('Rolled back successfully!');
        },
        error: () => alert('Failed to rollback')
      });
    }
  }

  openTestModal(prompt: PromptTemplate): void {
    this.selectedPrompt = prompt;
    this.testData = {};
    this.testResult = null;
    // Extract placeholders from template
    const matches = prompt.userTemplate?.match(/\{\{(\w+)\}\}/g) || [];
    matches.forEach(match => {
      const key = match.replace(/[{}]/g, '');
      this.testData[key] = '';
    });
    this.showTestModal = true;
  }

  closeTestModal(): void {
    this.showTestModal = false;
    this.testData = {};
    this.testResult = null;
  }

  runTest(): void {
    if (this.selectedPrompt) {
      this.adminService.testPrompt(this.selectedPrompt.id, this.testData).subscribe({
        next: (result) => {
          this.testResult = result;
        },
        error: () => alert('Test failed')
      });
    }
  }

  getTestDataKeys(): string[] {
    return Object.keys(this.testData);
  }
}
