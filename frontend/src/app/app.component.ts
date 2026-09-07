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
          <div class="w-full px-4 sm:px-6 lg:px-8">
            <div class="flex justify-between h-16">
              <div class="flex items-center space-x-8">
                <a routerLink="/dashboard" class="text-xl font-bold text-blue-600">AutoApplicant</a>
                <a routerLink="/jobs/search" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Jobs</a>
                <a routerLink="/jobs/saved" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Saved</a>
                <a routerLink="/jobs/add" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Paste &amp; Apply</a>
                <a routerLink="/applications" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Applications</a>
                <a routerLink="/analytics" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Analytics</a>
                <a routerLink="/companies" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Companies</a>
                <a routerLink="/ai/generate" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Generate</a>
                <a routerLink="/ai/documents" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Docs</a>
                <a routerLink="/ai/cv" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">CV</a>
                <a routerLink="/prompts" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Prompts</a>
              </div>
              <div class="flex items-center space-x-4">
                <a routerLink="/profile" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors">Profile</a>
                <a routerLink="/profile/writing-style" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors text-sm">Writing Style</a>
                <a routerLink="/templates" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors text-sm">Templates</a>
                <a routerLink="/jobs/ignored" routerLinkActive="text-blue-600 font-medium"
                   class="text-gray-600 hover:text-gray-900 transition-colors text-sm">Hidden</a>
                <button (click)="logout()" class="btn-secondary text-sm">Logout</button>
              </div>
            </div>
          </div>
        </nav>
      }
      <main class="w-full px-4 sm:px-6 lg:px-8 py-8">
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
