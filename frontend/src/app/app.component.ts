import { Component, DestroyRef, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';
import { ThemeService } from './core/theme.service';
import { LanguageService } from './core/language.service';
import { JbIconComponent } from './shared/components/jb-icon/jb-icon.component';
import { StreakBadgeComponent } from './shared/components/streak-badge/streak-badge.component';
import { JbToastComponent } from './shared/components/jb-toast/jb-toast.component';
import { NotifyService } from './shared/services/notify.service';
import { ActivityStreakService } from './core/activity-streak.service';
import { filter } from 'rxjs';

/** Routes rendered without the app shell. An unverified user on /verify-email cannot use the app yet. */
const FULLSCREEN_ROUTES = ['/onboarding', '/verify-email'];

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
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, TranslateModule, JbIconComponent, StreakBadgeComponent,
    JbToastComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  language = inject(LanguageService);
  notify = inject(NotifyService);
  private router = inject(Router);
  private streak = inject(ActivityStreakService);
  private destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);

  sidebarOpen = signal(false);
  oldFrontendOpen = signal(false);
  private currentUserEmail = '';
  private currentUserName = '';
  private signedIn = false;

  // ── Navigation definitions ──────────────────────────
  mainNav: NavItem[] = [
    { id: 'dashboard', label: 'nav.dashboard',      icon: 'dashboard', route: '/dashboard',  kbd: 'G D' },
    { id: 'jobfeed',   label: 'nav.jobFeed',        icon: 'compass',   route: '/jobs/feed',  kbd: 'G F' },
    { id: 'pipeline',  label: 'nav.pipeline',       icon: 'pipeline',  route: '/pipeline',   kbd: 'G P' },
    { id: 'addjob',    label: 'nav.newApplication', icon: 'sparkle',   route: '/apply',      kbd: 'N', accent: true },
  ];

  trackNav: NavItem[] = [
    { id: 'tasks',      label: 'nav.tasks',      icon: 'bell',      route: '/tasks' },
    { id: 'interviews', label: 'nav.interviews', icon: 'briefcase', route: '/interviews' },
  ];

  libraryNav: NavItem[] = [
    { id: 'master',       label: 'nav.masterCv',    icon: 'doc',       route: '/cv' },
    { id: 'applications', label: 'nav.applications', icon: 'layers',   route: '/applications' },
    { id: 'documents',    label: 'nav.documents',   icon: 'clipboard', route: '/documents' },
    { id: 'saved',        label: 'nav.savedRoles',  icon: 'bookmark',  route: '/jobs/saved' },
    { id: 'prompts',      label: 'nav.prompts',     icon: 'lightbulb', route: '/prompts' },
    { id: 'resume',       label: 'nav.cvBuilder',   icon: 'edit',      route: '/resume-builder' },
    { id: 'analysis',     label: 'nav.cvAnalysis',  icon: 'target',    route: '/analysis' },
    { id: 'analytics',    label: 'nav.analytics',   icon: 'chart-up',  route: '/analytics' },
    { id: 'companies',    label: 'nav.companies',   icon: 'building',  route: '/companies' },
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
    this.auth.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      this.currentUserEmail = user?.email || '';
      this.currentUserName = user?.email?.split('@')[0] ?? '';
      this.signedIn = !!user;
      if (user) this.streak.refresh();
      else this.streak.clear();
      if (user && !user.onboardingComplete && !this.router.url.startsWith('/onboarding')) {
        this.router.navigate(['/onboarding']);
      }
    });
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.sidebarOpen.set(false);
      if (this.signedIn) this.streak.refresh();
    });
  }

  isActive(route: string): boolean {
    return this.router.url === route || this.router.url.startsWith(route + '/');
  }

  isFullscreenRoute(): boolean {
    return FULLSCREEN_ROUTES.some(route => this.router.url.startsWith(route));
  }

  userName(): string {
    return this.currentUserName || this.translate.instant('nav.userFallback');
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
