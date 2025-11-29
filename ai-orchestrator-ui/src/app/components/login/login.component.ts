import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  username = '';
  role = 'USER';
  isLoading = false;
  errorMessage = '';

  roles = ['USER', 'ADMIN', 'OPERATOR'];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  login(): void {
    if (!this.username.trim()) {
      this.errorMessage = 'Please enter a username';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.username, this.role).subscribe({
      next: () => {
        this.router.navigate(['/chat']);
      },
      error: (error) => {
        console.error('Login error:', error);
        this.errorMessage = 'Login failed. Please try again.';
        this.isLoading = false;
      }
    });
  }
}
