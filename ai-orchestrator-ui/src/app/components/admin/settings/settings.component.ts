import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminService } from '../../../services/admin.service';
import { UrlWhitelist, OllamaStatus } from '../../../models/admin.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  ollamaStatus: OllamaStatus | null = null;
  urlWhitelist: UrlWhitelist[] = [];
  isLoading = true;

  showAddUrlModal = false;
  newUrl = {
    urlPattern: '',
    description: '',
    allowedMethods: 'GET'
  };

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadSettings();
  }

  loadSettings(): void {
    this.isLoading = true;
    
    this.adminService.getOllamaStatus().subscribe(status => {
      this.ollamaStatus = status;
    });

    this.adminService.getUrlWhitelist().subscribe(whitelist => {
      this.urlWhitelist = whitelist;
      this.isLoading = false;
    });
  }

  refreshScenarioCache(): void {
    this.adminService.refreshScenarioCache().subscribe({
      next: () => alert('Scenario cache refreshed successfully!'),
      error: () => alert('Failed to refresh cache')
    });
  }

  clearAllCache(): void {
    if (confirm('Are you sure you want to clear all caches? This may temporarily slow down the system.')) {
      this.adminService.clearAllCache().subscribe({
        next: () => alert('All caches cleared successfully!'),
        error: () => alert('Failed to clear caches')
      });
    }
  }

  openAddUrlModal(): void {
    this.newUrl = { urlPattern: '', description: '', allowedMethods: 'GET' };
    this.showAddUrlModal = true;
  }

  closeAddUrlModal(): void {
    this.showAddUrlModal = false;
  }

  addUrlToWhitelist(): void {
    if (this.newUrl.urlPattern && this.newUrl.description) {
      this.adminService.addUrlToWhitelist(this.newUrl).subscribe({
        next: () => {
          this.closeAddUrlModal();
          this.loadSettings();
        },
        error: () => alert('Failed to add URL to whitelist')
      });
    }
  }

  removeUrl(url: UrlWhitelist): void {
    if (confirm(`Remove "${url.urlPattern}" from whitelist?`)) {
      this.adminService.removeUrlFromWhitelist(url.id).subscribe({
        next: () => this.loadSettings(),
        error: () => alert('Failed to remove URL')
      });
    }
  }
}
