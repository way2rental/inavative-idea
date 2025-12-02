import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService, FeedbackStats, MessageFeedback, FeedbackDetail, ChatMessageDetail } from '../../../services/api.service';

@Component({
  selector: 'app-feedback-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './feedback-dashboard.component.html'
})
export class FeedbackDashboardComponent implements OnInit {
  stats: FeedbackStats | null = null;
  allFeedback: MessageFeedback[] = [];
  selectedFeedback: MessageFeedback | null = null;
  selectedFeedbackDetail: FeedbackDetail | null = null;
  conversationHistory: ChatMessageDetail[] = [];
  isLoading = true;
  isLoadingDetails = false;

  // Pagination properties
  currentPage = 1;
  pageSize = 10;
  totalRecords = 0;
  paginatedFeedback: MessageFeedback[] = [];

  Math = Math; // Expose Math to template

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadStats();
    this.loadAllFeedback();
  }

  loadStats(): void {
    this.apiService.getFeedbackStats().subscribe({
      next: (stats) => this.stats = stats,
      error: (err) => console.error('Failed to load stats:', err)
    });
  }

  loadAllFeedback(): void {
    this.isLoading = true;
    this.apiService.getAllFeedback().subscribe({
      next: (feedback) => {
        this.allFeedback = feedback;
        this.totalRecords = feedback.length;
        this.updatePaginatedData();
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load feedback:', err);
        this.isLoading = false;
      }
    });
  }

  updatePaginatedData(): void {
    const startIndex = (this.currentPage - 1) * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.paginatedFeedback = this.allFeedback.slice(startIndex, endIndex);
  }

  get totalPages(): number {
    return Math.ceil(this.totalRecords / this.pageSize);
  }

  get startRecord(): number {
    return (this.currentPage - 1) * this.pageSize + 1;
  }

  get endRecord(): number {
    const end = this.currentPage * this.pageSize;
    return Math.min(end, this.totalRecords);
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.updatePaginatedData();
    }
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

  getPageNumbers(): number[] {
    const pages: number[] = [];
    const maxVisiblePages = 5;

    if (this.totalPages <= maxVisiblePages) {
      for (let i = 1; i <= this.totalPages; i++) {
        pages.push(i);
      }
    } else {
      let start = Math.max(1, this.currentPage - 2);
      let end = Math.min(this.totalPages, start + maxVisiblePages - 1);

      if (end - start < maxVisiblePages - 1) {
        start = Math.max(1, end - maxVisiblePages + 1);
      }

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
    }

    return pages;
  }

  viewDetails(fb: MessageFeedback): void {
    this.selectedFeedback = fb;
    this.isLoadingDetails = true;
    this.selectedFeedbackDetail = null;
    this.conversationHistory = [];

    // Fetch full session details and conversation history
    this.apiService.getSessionMessages(fb.sessionId).subscribe({
      next: (messages) => {
        this.conversationHistory = messages;
        this.isLoadingDetails = false;

        // Create enhanced feedback detail
        this.selectedFeedbackDetail = {
          ...fb,
          conversationHistory: messages
        };
      },
      error: (err) => {
        console.error('Failed to load session details:', err);
        this.isLoadingDetails = false;
        // Still show basic feedback even if details fail
        this.selectedFeedbackDetail = {
          ...fb,
          conversationHistory: []
        };
      }
    });
  }

  closeDetails(): void {
    this.selectedFeedback = null;
    this.selectedFeedbackDetail = null;
    this.conversationHistory = [];
    this.isLoadingDetails = false;
  }

  getSatisfactionColor(): string {
    if (!this.stats) return 'text-gray-600';
    if (this.stats.satisfactionRate >= 80) return 'text-green-600';
    if (this.stats.satisfactionRate >= 50) return 'text-yellow-600';
    return 'text-red-600';
  }

  formatScenario(code: string): string {
    if (!code) return 'Unknown';
    return code.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }


  formatDate(dateStr: string): string {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  truncateText(text: string, maxLength: number): string {
    if (!text) return '';
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
  }
}
