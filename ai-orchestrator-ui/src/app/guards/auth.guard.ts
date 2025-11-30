import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Guard to check if user is authenticated
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

// Guard to check if user has admin role
export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.getCurrentUser();
  if (user && user.role === 'ADMIN') {
    return true;
  }

  // If logged in but not admin, redirect to chat
  if (user) {
    router.navigate(['/chat']);
  } else {
    router.navigate(['/login']);
  }
  return false;
};

// Guard to check if user has operator or admin role
export const operatorGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.getCurrentUser();
  if (user && (user.role === 'ADMIN' || user.role === 'OPERATOR')) {
    return true;
  }

  // If logged in but not operator/admin, redirect to chat
  if (user) {
    router.navigate(['/chat']);
  } else {
    router.navigate(['/login']);
  }
  return false;
};

// Guard to check if user is logged in (any role)
export const userGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
