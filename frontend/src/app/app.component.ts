import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  template: `
    <div class="min-h-screen bg-gray-50">
      @if (auth.isAuthenticated()) {
        <nav class="bg-white shadow-sm border-b border-gray-200">
          <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div class="flex justify-between h-16">
              <div class="flex items-center space-x-8">
                <a routerLink="/dashboard" class="text-xl font-bold text-blue-600">AutoApplicant</a>
                <a routerLink="/jobs" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Jobs</a>
                <a routerLink="/applications" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Applications</a>
                <a routerLink="/ai/cv" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">CV</a>
                <a routerLink="/prompts" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Prompts</a>
              </div>
              <div class="flex items-center space-x-4">
                <a routerLink="/profile" class="text-gray-600 hover:text-gray-900 transition-colors">Profile</a>
                <button (click)="logout()" class="btn-secondary text-sm">Logout</button>
              </div>
            </div>
          </div>
        </nav>
      }
      <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <router-outlet />
      </main>
    </div>
  `
})
export class AppComponent {
  auth = inject(AuthService);
  router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
