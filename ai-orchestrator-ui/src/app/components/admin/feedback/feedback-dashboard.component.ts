import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService, FeedbackStats, MessageFeedback } from '../../../services/api.service';

@Component({
  selector: 'app-feedback-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-gray-50">
      <!-- Header -->
      <div class="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div class="max-w-7xl mx-auto px-6 py-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-4">
              <a routerLink="/admin" class="p-2 hover:bg-gray-100 rounded-lg transition-colors">
                <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                </svg>
              </a>
              <div>
                <h1 class="text-2xl font-bold text-gray-900">User Feedback</h1>
                <p class="text-sm text-gray-500">Monitor AI response quality and user satisfaction</p>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <button (click)="loadStats()" class="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Refresh
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="max-w-7xl mx-auto px-6 py-8">
        <!-- Stats Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <!-- Total Feedback -->
          <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm font-medium text-gray-500">Total Feedback</p>
                <p class="text-3xl font-bold text-gray-900">{{ stats?.totalFeedback || 0 }}</p>
              </div>
              <div class="w-12 h-12 bg-axis-burgundy/10 rounded-xl flex items-center justify-center">
                <svg class="w-6 h-6 text-axis-burgundy" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                </svg>
              </div>
            </div>
          </div>

          <!-- Likes -->
          <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm font-medium text-gray-500">Total Likes 👍</p>
                <p class="text-3xl font-bold text-green-600">{{ stats?.totalLikes || 0 }}</p>
              </div>
              <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center">
                <svg class="w-6 h-6 text-green-500" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"/>
                </svg>
              </div>
            </div>
          </div>

          <!-- Dislikes -->
          <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm font-medium text-gray-500">Total Dislikes 👎</p>
                <p class="text-3xl font-bold text-red-600">{{ stats?.totalDislikes || 0 }}</p>
              </div>
              <div class="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center">
                <svg class="w-6 h-6 text-red-500" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5"/>
                </svg>
              </div>
            </div>
          </div>

          <!-- Satisfaction Rate -->
          <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm font-medium text-gray-500">Satisfaction Rate</p>
                <p class="text-3xl font-bold" [class]="getSatisfactionColor()">
                  {{ stats?.satisfactionRate?.toFixed(1) || 0 }}%
                </p>
              </div>
              <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center">
                <svg class="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                </svg>
              </div>
            </div>
            <div class="mt-3 w-full bg-gray-200 rounded-full h-2">
              <div class="h-2 rounded-full transition-all duration-500"
                   [class]="getSatisfactionBarColor()"
                   [style.width.%]="stats?.satisfactionRate || 0"></div>
            </div>
          </div>
        </div>

        <!-- Tabs -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-200 mb-8">
          <div class="border-b border-gray-200">
            <nav class="flex -mb-px">
              <button (click)="activeTab = 'all'" 
                      [class]="activeTab === 'all' ? 'border-axis-burgundy text-axis-burgundy' : 'border-transparent text-gray-500 hover:text-gray-700'"
                      class="px-6 py-4 text-sm font-medium border-b-2 transition-colors">
                All Feedback
              </button>
              <button (click)="activeTab = 'negative'; loadNegativeFeedback()" 
                      [class]="activeTab === 'negative' ? 'border-axis-burgundy text-axis-burgundy' : 'border-transparent text-gray-500 hover:text-gray-700'"
                      class="px-6 py-4 text-sm font-medium border-b-2 transition-colors flex items-center gap-2">
                Negative Feedback
                <span class="px-2 py-0.5 text-xs bg-red-100 text-red-700 rounded-full">{{ stats?.totalDislikes || 0 }}</span>
              </button>
              <button (click)="activeTab = 'scenarios'" 
                      [class]="activeTab === 'scenarios' ? 'border-axis-burgundy text-axis-burgundy' : 'border-transparent text-gray-500 hover:text-gray-700'"
                      class="px-6 py-4 text-sm font-medium border-b-2 transition-colors">
                By Scenario
              </button>
            </nav>
          </div>

          <!-- Tab Content -->
          <div class="p-6">
            <!-- All Feedback Tab -->
            @if (activeTab === 'all') {
              <div class="space-y-4">
                @for (fb of allFeedback; track fb.id) {
                  <div class="border border-gray-200 rounded-xl p-4 hover:shadow-md transition-shadow cursor-pointer"
                       (click)="openDetailModal(fb)">
                    <div class="flex items-start gap-4">
                      <!-- Feedback Icon -->
                      <div class="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
                           [class]="fb.feedbackType === 'like' ? 'bg-green-100' : 'bg-red-100'">
                        <svg class="w-6 h-6" [class]="fb.feedbackType === 'like' ? 'text-green-600' : 'text-red-600'" 
                             fill="currentColor" viewBox="0 0 24 24">
                          @if (fb.feedbackType === 'like') {
                            <path d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"/>
                          } @else {
                            <path d="M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.736 3h4.018a2 2 0 01.485.06l3.76.94m-7 10v5a2 2 0 002 2h.096c.5 0 .905-.405.905-.904 0-.715.211-1.413.608-2.008L17 13V4m-7 10h2m5-10h2a2 2 0 012 2v6a2 2 0 01-2 2h-2.5"/>
                          }
                        </svg>
                      </div>

                      <!-- Content -->
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-3 mb-2">
                          <span class="font-medium text-gray-900">{{ fb.scenarioCode || 'Unknown' }}</span>
                          <span class="text-xs text-gray-400">•</span>
                          <span class="text-sm text-gray-500">{{ fb.userId }}</span>
                          <span class="text-xs text-gray-400">•</span>
                          <span class="text-sm text-gray-500">{{ formatDate(fb.createdAt) }}</span>
                        </div>
                        
                        <div class="mb-2">
                          <p class="text-sm text-gray-600 truncate">
                            <span class="font-medium">Query:</span> {{ fb.userQuery || 'N/A' }}
                          </p>
                        </div>

                        <div class="bg-gray-50 rounded-lg p-3">
                          <p class="text-sm text-gray-700 line-clamp-2">{{ truncateText(fb.aiResponse, 200) }}</p>
                        </div>

                        <div class="flex items-center gap-3 mt-3">
                          <span class="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded-full">
                            Session: {{ fb.sessionId?.substring(0, 8) || 'N/A' }}...
                          </span>
                          <span class="text-xs text-axis-burgundy hover:underline">View Details →</span>
                        </div>
                      </div>
                    </div>
                  </div>
                }
                @if (allFeedback.length === 0) {
                  <div class="text-center py-12 text-gray-500">
                    <svg class="w-16 h-16 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p class="text-lg font-medium">No feedback yet</p>
                    <p class="text-sm">User reactions will appear here</p>
                  </div>
                }
              </div>
            }

            <!-- Negative Feedback Tab -->
            @if (activeTab === 'negative') {
              <div class="space-y-4">
                @for (fb of negativeFeedback; track fb.id) {
                  <div class="border border-red-200 bg-red-50/50 rounded-xl p-4 hover:shadow-md transition-shadow cursor-pointer"
                       (click)="openDetailModal(fb)">
                    <div class="flex items-start gap-4">
                      <div class="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center flex-shrink-0">
                        <span class="text-2xl">👎</span>
                      </div>
                      <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-3 mb-2">
                          <span class="font-medium text-gray-900">{{ fb.scenarioCode || 'Unknown Scenario' }}</span>
                          <span class="text-sm text-gray-500">{{ formatDate(fb.createdAt) }}</span>
                        </div>
                        <p class="text-sm text-gray-600 mb-2">
                          <span class="font-medium">User Query:</span> {{ fb.userQuery }}
                        </p>
                        <div class="bg-white rounded-lg p-3 border border-gray-200">
                          <p class="text-sm text-gray-700">{{ truncateText(fb.aiResponse, 250) }}</p>
                        </div>
                        <div class="mt-2 text-xs text-axis-burgundy hover:underline">Click to view full details →</div>
                      </div>
                    </div>
                  </div>
                }
                @if (negativeFeedback.length === 0) {
                  <div class="text-center py-12 text-gray-500">
                    <span class="text-5xl mb-4 block">🎉</span>
                    <p class="text-lg font-medium">No negative feedback!</p>
                    <p class="text-sm">Great job - users are happy with the AI responses</p>
                  </div>
                }
              </div>
            }

            <!-- By Scenario Tab -->
            @if (activeTab === 'scenarios') {
              <div class="overflow-x-auto">
                <table class="min-w-full divide-y divide-gray-200">
                  <thead class="bg-gray-50">
                    <tr>
                      <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Scenario</th>
                      <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Likes</th>
                      <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Dislikes</th>
                      <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Satisfaction</th>
                    </tr>
                  </thead>
                  <tbody class="bg-white divide-y divide-gray-200">
                    @for (scenario of getScenarioList(); track scenario) {
                      <tr class="hover:bg-gray-50">
                        <td class="px-6 py-4 whitespace-nowrap">
                          <span class="text-sm font-medium text-gray-900">{{ formatScenario(scenario) }}</span>
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap">
                          <span class="text-sm text-green-600 font-semibold flex items-center gap-1">
                            👍 {{ getScenarioLikes(scenario) }}
                          </span>
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap">
                          <span class="text-sm text-red-600 font-semibold flex items-center gap-1">
                            👎 {{ getScenarioDislikes(scenario) }}
                          </span>
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap">
                          <div class="flex items-center gap-2">
                            <div class="w-24 bg-gray-200 rounded-full h-2">
                              <div class="h-2 rounded-full"
                                   [class]="getScenarioSatisfaction(scenario) >= 80 ? 'bg-green-500' : getScenarioSatisfaction(scenario) >= 50 ? 'bg-yellow-500' : 'bg-red-500'"
                                   [style.width.%]="getScenarioSatisfaction(scenario)"></div>
                            </div>
                            <span class="text-sm text-gray-600">{{ getScenarioSatisfaction(scenario).toFixed(0) }}%</span>
                          </div>
                        </td>
                      </tr>
                    }
                    @if (getScenarioList().length === 0) {
                      <tr>
                        <td colspan="4" class="px-6 py-12 text-center text-gray-500">
                          No scenario data available
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Detail Modal -->
      @if (selectedFeedback) {
        <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" (click)="closeDetailModal()">
          <div class="bg-white rounded-2xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden" (click)="$event.stopPropagation()">
            <!-- Modal Header -->
            <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between bg-gradient-to-r from-axis-burgundy to-axis-maroon">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 bg-white/20 rounded-lg flex items-center justify-center">
                  <span class="text-xl">{{ selectedFeedback.feedbackType === 'like' ? '👍' : '👎' }}</span>
                </div>
                <div class="text-white">
                  <h3 class="font-bold">Feedback Details</h3>
                  <p class="text-sm text-white/80">{{ selectedFeedback.scenarioCode || 'Unknown Scenario' }}</p>
                </div>
              </div>
              <button (click)="closeDetailModal()" class="p-2 hover:bg-white/20 rounded-lg transition-colors">
                <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <!-- Modal Content -->
            <div class="p-6 overflow-y-auto max-h-[calc(90vh-200px)]">
              <!-- Metadata Grid -->
              <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                <div class="bg-gray-50 rounded-lg p-3">
                  <p class="text-xs text-gray-500 mb-1">User ID</p>
                  <p class="text-sm font-medium text-gray-900">{{ selectedFeedback.userId }}</p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3">
                  <p class="text-xs text-gray-500 mb-1">Session ID</p>
                  <p class="text-sm font-medium text-gray-900 truncate" [title]="selectedFeedback.sessionId">
                    {{ selectedFeedback.sessionId?.substring(0, 12) }}...
                  </p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3">
                  <p class="text-xs text-gray-500 mb-1">Message ID</p>
                  <p class="text-sm font-medium text-gray-900">{{ selectedFeedback.messageId || 'N/A' }}</p>
                </div>
                <div class="bg-gray-50 rounded-lg p-3">
                  <p class="text-xs text-gray-500 mb-1">Timestamp</p>
                  <p class="text-sm font-medium text-gray-900">{{ formatDate(selectedFeedback.createdAt) }}</p>
                </div>
              </div>

              <!-- User Query Section -->
              <div class="mb-6">
                <h4 class="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-2">
                  <svg class="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                  </svg>
                  User Query
                </h4>
                <div class="bg-blue-50 rounded-lg p-4 border border-blue-200">
                  <p class="text-sm text-gray-800">{{ selectedFeedback.userQuery || 'No query recorded' }}</p>
                </div>
              </div>

              <!-- AI Response Section -->
              <div class="mb-6">
                <h4 class="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-2">
                  <svg class="w-4 h-4 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  AI Response
                </h4>
                <div class="bg-purple-50 rounded-lg p-4 border border-purple-200">
                  <p class="text-sm text-gray-800 whitespace-pre-wrap">{{ selectedFeedback.aiResponse || 'No response recorded' }}</p>
                </div>
              </div>

              <!-- User Comment (if any) -->
              @if (selectedFeedback.comment) {
                <div class="mb-6">
                  <h4 class="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-2">
                    <svg class="w-4 h-4 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                    </svg>
                    User Comment
                  </h4>
                  <div class="bg-yellow-50 rounded-lg p-4 border border-yellow-200">
                    <p class="text-sm text-gray-800 italic">"{{ selectedFeedback.comment }}"</p>
                  </div>
                </div>
              }

              <!-- Feedback Badge -->
              <div class="flex items-center gap-4">
                <span class="text-sm text-gray-500">Feedback Type:</span>
                <span class="px-4 py-2 rounded-full font-medium text-sm"
                      [class]="selectedFeedback.feedbackType === 'like' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
                  {{ selectedFeedback.feedbackType === 'like' ? '👍 Positive' : '👎 Negative' }}
                </span>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="px-6 py-4 border-t border-gray-200 bg-gray-50 flex justify-end">
              <button (click)="closeDetailModal()" 
                      class="px-4 py-2 bg-axis-burgundy text-white rounded-lg hover:bg-axis-maroon transition-colors">
                Close
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class FeedbackDashboardComponent implements OnInit {
  stats: FeedbackStats | null = null;
  allFeedback: MessageFeedback[] = [];
  negativeFeedback: MessageFeedback[] = [];
  activeTab: 'all' | 'negative' | 'scenarios' = 'all';
  selectedFeedback: MessageFeedback | null = null;

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
    this.apiService.getAllFeedback().subscribe({
      next: (feedback) => this.allFeedback = feedback,
      error: (err) => console.error('Failed to load feedback:', err)
    });
  }

  loadNegativeFeedback(): void {
    this.apiService.getNegativeFeedback(50).subscribe({
      next: (feedback) => this.negativeFeedback = feedback,
      error: (err) => console.error('Failed to load negative feedback:', err)
    });
  }

  openDetailModal(fb: MessageFeedback): void {
    this.selectedFeedback = fb;
  }

  closeDetailModal(): void {
    this.selectedFeedback = null;
  }

  getSatisfactionColor(): string {
    if (!this.stats) return 'text-gray-600';
    if (this.stats.satisfactionRate >= 80) return 'text-green-600';
    if (this.stats.satisfactionRate >= 50) return 'text-yellow-600';
    return 'text-red-600';
  }

  getSatisfactionBarColor(): string {
    if (!this.stats) return 'bg-gray-400';
    if (this.stats.satisfactionRate >= 80) return 'bg-green-500';
    if (this.stats.satisfactionRate >= 50) return 'bg-yellow-500';
    return 'bg-red-500';
  }

  getScenarioList(): string[] {
    if (!this.stats?.scenarioBreakdown) return [];
    return Object.keys(this.stats.scenarioBreakdown);
  }

  formatScenario(code: string): string {
    if (!code) return 'Unknown';
    return code.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  getScenarioLikes(scenario: string): number {
    return this.stats?.scenarioBreakdown?.[scenario]?.['like'] || 0;
  }

  getScenarioDislikes(scenario: string): number {
    return this.stats?.scenarioBreakdown?.[scenario]?.['dislike'] || 0;
  }

  getScenarioSatisfaction(scenario: string): number {
    const likes = this.getScenarioLikes(scenario);
    const dislikes = this.getScenarioDislikes(scenario);
    const total = likes + dislikes;
    return total > 0 ? (likes / total) * 100 : 0;
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
