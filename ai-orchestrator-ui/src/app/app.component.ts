import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, NavigationEnd, NavigationStart, NavigationCancel, NavigationError } from '@angular/router';
import { trigger, state, style, transition, animate, query, stagger } from '@angular/animations';
import { HeaderComponent } from './components/header/header.component';
import { AlertComponent } from './components/common/alert/alert.component';
import { ModalComponent } from './components/common/modal/modal.component';
import { AdminSidebarComponent } from './components/shared/admin-sidebar/admin-sidebar.component';
import { filter, fromEvent, Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, AlertComponent, ModalComponent, AdminSidebarComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  animations: [
    // Route transition animations
    trigger('routeAnimations', [
      transition('* <=> *', [
        query(':enter, :leave', [
          style({ position: 'absolute', width: '100%' })
        ], { optional: true }),
        query(':enter', [
          style({ opacity: 0, transform: 'translateY(10px)' })
        ], { optional: true }),
        query(':leave', animate('200ms ease-out', style({ opacity: 0 })), { optional: true }),
        query(':enter', animate('300ms ease-in', style({ opacity: 1, transform: 'translateY(0)' })), { optional: true })
      ])
    ]),

    // Fade animation for router content
    trigger('routeFadeAnimation', [
      transition('* <=> *', [
        style({ opacity: 0 }),
        animate('300ms ease-in', style({ opacity: 1 }))
      ])
    ]),

    // Slide down animation for header and banners
    trigger('slideDown', [
      transition(':enter', [
        style({ transform: 'translateY(-100%)', opacity: 0 }),
        animate('300ms ease-out', style({ transform: 'translateY(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ transform: 'translateY(-100%)', opacity: 0 }))
      ])
    ]),

    // Sidebar animation
    trigger('sidebarAnimation', [
      transition(':enter', [
        style({ transform: 'translateX(-100%)', opacity: 0 }),
        animate('250ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ transform: 'translateX(-100%)', opacity: 0 }))
      ])
    ]),

    // Fade in/out for overlays
    trigger('fadeInOut', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('200ms ease-in', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('150ms ease-out', style({ opacity: 0 }))
      ])
    ])
  ]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'AI Orchestrator';
  showHeader = false;
  showAdminSidebar = false;
  sidebarCollapsed = false;

  // Loading states
  isLoading = false;

  // Scroll handling
  showScrollTop = false;

  // Network status
  isOnline = true;

  // Session management
  showSessionWarning = false;
  sessionTimeRemaining = 0;
  private sessionWarningTimer?: any;

  // Subscriptions
  private subscriptions: Subscription[] = [];

  constructor(private router: Router) {
    // Handle route changes
    this.router.events.subscribe((event: any) => {
      // Show loading indicator
      if (event instanceof NavigationStart) {
        this.isLoading = true;
      }

      // Hide loading and update UI
      if (event instanceof NavigationEnd) {
        this.isLoading = false;
        this.showHeader = !event.url.includes('/login');
        this.showAdminSidebar = event.url.startsWith('/admin');

        // Scroll to top on navigation
        window.scrollTo(0, 0);
      }

      // Handle navigation errors
      if (event instanceof NavigationCancel || event instanceof NavigationError) {
        this.isLoading = false;
      }
    });
  }

  ngOnInit(): void {
    // Monitor online/offline status
    this.subscriptions.push(
      fromEvent(window, 'online').subscribe(() => {
        this.isOnline = true;
      })
    );

    this.subscriptions.push(
      fromEvent(window, 'offline').subscribe(() => {
        this.isOnline = false;
      })
    );

    // Initialize session warning (example: 5 minutes before expiry)
    // this.startSessionWarningTimer();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    if (this.sessionWarningTimer) {
      clearInterval(this.sessionWarningTimer);
    }
  }

  @HostListener('window:scroll', [])
  onWindowScroll(): void {
    // Show scroll-to-top button after scrolling 300px
    this.showScrollTop = window.pageYOffset > 300;
  }

  onSidebarToggle(collapsed: boolean): void {
    this.sidebarCollapsed = collapsed;
  }

  prepareRoute(outlet: RouterOutlet): any {
    return outlet?.activatedRouteData?.['animation'];
  }

  scrollToTop(): void {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  extendSession(): void {
    // Implement session extension logic
    this.showSessionWarning = false;
    console.log('Session extended');
    // Call your auth service to extend session
  }

  private startSessionWarningTimer(): void {
    // Example: Show warning 5 minutes before session expiry
    const warningTime = 5 * 60 * 1000; // 5 minutes
    const sessionDuration = 30 * 60 * 1000; // 30 minutes total

    setTimeout(() => {
      this.showSessionWarning = true;
      this.sessionTimeRemaining = 300; // 5 minutes in seconds

      // Countdown timer
      this.sessionWarningTimer = setInterval(() => {
        this.sessionTimeRemaining--;
        if (this.sessionTimeRemaining <= 0) {
          clearInterval(this.sessionWarningTimer);
          // Handle session timeout
          this.router.navigate(['/login']);
        }
      }, 1000);
    }, sessionDuration - warningTime);
  }
}
