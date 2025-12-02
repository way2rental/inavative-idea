import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/auth.model';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent implements OnInit {
  currentUser: User | null = null;
  isOnAdminPage = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    // Track if we're on an admin page
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      this.isOnAdminPage = event.url.startsWith('/admin');
    });
  }

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  isAdmin(): boolean {
    return this.currentUser?.role === 'ADMIN';
  }

  isOperator(): boolean {
    return this.currentUser?.role === 'OPERATOR';
  }

  canAccessAdmin(): boolean {
    return this.currentUser?.role === 'ADMIN';
  }

  canAccessAuditLogs(): boolean {
    return this.currentUser?.role === 'ADMIN' || this.currentUser?.role === 'OPERATOR';
  }
}
