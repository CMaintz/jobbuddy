import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';
import { ThemeService } from './core/theme.service';
import { JbIconComponent } from './shared/components/jb-icon/jb-icon.component';
import { Subscription, filter } from 'rxjs';

interface NavItem {
  id: string;
  label: string;
  icon: string;
  route: string;
  kbd?: string;
  badge?: string;
  count?: number;
  accent?: boolean;
  hot?: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule, JbIconComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  private router = inject(Router);
  private userSub?: Subscription;

  sidebarOpen = signal(false);
  oldFrontendOpen = signal(false);
  private currentUserEmail = '';
  private currentUserName = '';

  // ── Navigation definitions ──────────────────────────
  mainNav: NavItem[] = [
    { id: 'dashboard', label: 'Dashboard',       icon: 'dashboard', route: '/dashboard',  kbd: 'G D' },
    { id: 'jobfeed',   label: 'Job feed',        icon: 'compass',   route: '/jobs/feed',  kbd: 'G F', badge: 'new' },
    { id: 'pipeline',  label: 'Pipeline',        icon: 'pipeline',  route: '/pipeline',   kbd: 'G P', count: 14 },
    { id: 'addjob',    label: 'New application', icon: 'sparkle',   route: '/apply',      kbd: 'N', accent: true },
  ];

  trackNav: NavItem[] = [
    { id: 'tasks',      label: 'Tasks',      icon: 'bell',      route: '/tasks',      count: 3, hot: true },
    { id: 'interviews', label: 'Interviews', icon: 'briefcase', route: '/interviews', count: 3 },
    { id: 'contacts',   label: 'Contacts',   icon: 'user',      route: '/contacts',   count: 6 },
  ];

  libraryNav: NavItem[] = [
    { id: 'master',       label: 'Master CV',      icon: 'doc',       route: '/cv' },
    { id: 'applications', label: 'Applications',   icon: 'layers',    route: '/applications', count: 38 },
    { id: 'saved',        label: 'Saved roles',    icon: 'bookmark',  route: '/jobs/saved',   count: 7 },
    { id: 'prompts',      label: 'Prompts',        icon: 'lightbulb', route: '/prompts',  count: 9 },
    { id: 'resume',       label: 'Resume Builder', icon: 'edit',      route: '/resume-builder' },
  ];

  oldFrontendNav = [
    { id: 'old-profile',    label: 'Profile editor',    route: '/old/profile' },
    { id: 'old-writing',    label: 'Writing style',     route: '/old/profile/writing-style' },
    { id: 'old-generate',   label: 'AI generate',       route: '/old/ai/generate' },
    { id: 'old-docs',       label: 'AI documents',      route: '/old/ai/documents' },
    { id: 'old-cv-upload',  label: 'CV upload/parse',   route: '/old/ai/cv' },
    { id: 'old-cv-analyze', label: 'CV analysis',       route: '/old/ai/analyze' },
    { id: 'old-analytics',  label: 'Analytics',         route: '/old/analytics' },
    { id: 'old-companies',  label: 'Companies',         route: '/old/companies' },
    { id: 'old-templates',  label: 'PDF templates',     route: '/old/templates' },
    { id: 'old-jobs',       label: 'Jobs list',         route: '/old/jobs' },
    { id: 'old-apps',       label: 'Applications',      route: '/old/applications' },
    { id: 'old-pipeline',   label: 'Pipeline',          route: '/old/applications/pipeline' },
    { id: 'old-prompts',    label: 'Prompts',           route: '/old/prompts' },
  ];

  ngOnInit(): void {
    this.userSub = this.auth.currentUser$.subscribe(user => {
      this.currentUserEmail = user?.email || '';
      this.currentUserName = user?.email?.split('@')[0] || 'User';
      if (user && !user.onboardingComplete && !this.router.url.startsWith('/onboarding')) {
        this.router.navigate(['/onboarding']);
      }
    });
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.sidebarOpen.set(false);
    });
  }

  ngOnDestroy(): void {
    this.userSub?.unsubscribe();
  }

  isActive(route: string): boolean {
    return this.router.url === route || this.router.url.startsWith(route + '/');
  }

  isFullscreenRoute(): boolean {
    return this.router.url.startsWith('/onboarding');
  }

  userName(): string {
    return this.currentUserName || 'User';
  }

  userEmail(): string {
    return this.currentUserEmail;
  }

  userInitials(): string {
    const name = this.userName();
    return name.split(' ').filter(w => w.length > 0).map(w => w[0]).join('').substring(0, 2).toUpperCase() || 'U';
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
