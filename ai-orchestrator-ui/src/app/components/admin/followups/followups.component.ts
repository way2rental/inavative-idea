import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { AlertService } from '../../../services/alert.service';
import { FollowUpGroup, FollowUpGroupFormData, FollowUpQuestion } from '../../../models/admin.model';

@Component({
  selector: 'app-followups',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './followups.component.html'
})
export class FollowUpsComponent implements OnInit {
  followUpGroups: FollowUpGroup[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  selectedGroup: FollowUpGroup | null = null;
  showQuestionModal = false;
  selectedQuestionIndex = -1;

  formData: FollowUpGroupFormData = this.getEmptyFormData();
  questionForm: FollowUpQuestion = this.getEmptyQuestion();
  newScenarioCode = '';

  questionTypes = ['string', 'date', 'number', 'boolean'];

  constructor(
    private adminService: AdminService,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.loadFollowUpGroups();
  }

  loadFollowUpGroups(): void {
    this.isLoading = true;
    this.adminService.getFollowUpGroups().subscribe({
      next: (groups) => {
        this.followUpGroups = groups;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.alertService.error('Error', 'Failed to load follow-up groups');
      }
    });
  }

  getEmptyFormData(): FollowUpGroupFormData {
    return {
      groupKey: '',
      description: '',
      scenarioCodes: [],
      questions: [],
      questionOrder: [],
      active: true
    };
  }

  getEmptyQuestion(): FollowUpQuestion {
    return {
      key: '',
      question: '',
      type: 'string',
      required: true,
      validationPattern: '',
      placeholder: '',
      defaultValue: ''
    };
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.selectedGroup = null;
    this.formData = this.getEmptyFormData();
    this.showModal = true;
  }

  openEditModal(group: FollowUpGroup): void {
    this.isEditing = true;
    this.selectedGroup = group;
    this.formData = {
      groupKey: group.groupKey,
      description: group.description,
      scenarioCodes: [...group.scenarioCodes],
      questions: group.questions.map(q => ({ ...q })),
      questionOrder: [...group.questionOrder],
      active: group.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedGroup = null;
    this.newScenarioCode = '';
  }

  addScenarioCode(): void {
    if (this.newScenarioCode.trim() && !this.formData.scenarioCodes.includes(this.newScenarioCode.trim())) {
      this.formData.scenarioCodes.push(this.newScenarioCode.trim());
      this.newScenarioCode = '';
    }
  }

  removeScenarioCode(index: number): void {
    this.formData.scenarioCodes.splice(index, 1);
  }

  openQuestionModal(index: number = -1): void {
    this.selectedQuestionIndex = index;
    if (index >= 0 && this.formData.questions[index]) {
      this.questionForm = { ...this.formData.questions[index] };
    } else {
      this.questionForm = this.getEmptyQuestion();
    }
    this.showQuestionModal = true;
  }

  closeQuestionModal(): void {
    this.showQuestionModal = false;
    this.selectedQuestionIndex = -1;
    this.questionForm = this.getEmptyQuestion();
  }

  saveQuestion(): void {
    if (!this.questionForm.key.trim() || !this.questionForm.question.trim()) {
      this.alertService.error('Error', 'Question key and text are required');
      return;
    }

    if (this.selectedQuestionIndex >= 0) {
      // Update existing question
      this.formData.questions[this.selectedQuestionIndex] = { ...this.questionForm };
    } else {
      // Add new question
      this.formData.questions.push({ ...this.questionForm });
      this.formData.questionOrder.push(this.questionForm.key);
    }
    this.closeQuestionModal();
  }

  removeQuestion(index: number): void {
    const question = this.formData.questions[index];
    this.formData.questions.splice(index, 1);
    const orderIndex = this.formData.questionOrder.indexOf(question.key);
    if (orderIndex >= 0) {
      this.formData.questionOrder.splice(orderIndex, 1);
    }
  }

  moveQuestionUp(index: number): void {
    if (index > 0) {
      [this.formData.questions[index], this.formData.questions[index - 1]] =
        [this.formData.questions[index - 1], this.formData.questions[index]];
      [this.formData.questionOrder[index], this.formData.questionOrder[index - 1]] =
        [this.formData.questionOrder[index - 1], this.formData.questionOrder[index]];
    }
  }

  moveQuestionDown(index: number): void {
    if (index < this.formData.questions.length - 1) {
      [this.formData.questions[index], this.formData.questions[index + 1]] =
        [this.formData.questions[index + 1], this.formData.questions[index]];
      [this.formData.questionOrder[index], this.formData.questionOrder[index + 1]] =
        [this.formData.questionOrder[index + 1], this.formData.questionOrder[index]];
    }
  }

  saveGroup(): void {
    if (this.isEditing && this.selectedGroup) {
      this.adminService.updateFollowUpGroup(this.selectedGroup.id, this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadFollowUpGroups();
          this.alertService.success('Success', 'Follow-up group updated successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to update follow-up group: ' + err.message)
      });
    } else {
      this.adminService.createFollowUpGroup(this.formData).subscribe({
        next: () => {
          this.closeModal();
          this.loadFollowUpGroups();
          this.alertService.success('Success', 'Follow-up group created successfully');
        },
        error: (err) => this.alertService.error('Error', 'Failed to create follow-up group: ' + err.message)
      });
    }
  }

  toggleStatus(group: FollowUpGroup): void {
    this.adminService.toggleFollowUpGroupStatus(group.id, !group.active).subscribe({
      next: () => this.loadFollowUpGroups(),
      error: () => this.alertService.error('Error', 'Failed to toggle status')
    });
  }

  deleteGroup(group: FollowUpGroup): void {
    if (confirm(`Are you sure you want to delete "${group.groupKey}"?`)) {
      this.adminService.deleteFollowUpGroup(group.id).subscribe({
        next: () => {
          this.loadFollowUpGroups();
          this.alertService.success('Deleted', 'Follow-up group deleted successfully');
        },
        error: () => this.alertService.error('Error', 'Failed to delete follow-up group')
      });
    }
  }
}
