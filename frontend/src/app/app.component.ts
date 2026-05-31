import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';
import { ThemeService } from './core/theme.service';
import { JbIconComponent } from './shared/components/jb-icon/jb-icon.component';

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
  template: `
    <!-- Auth screens: no shell -->
    @if (!auth.isAuthenticated()) {
      <router-outlet />
    } @else if (isFullscreenRoute()) {
      <router-outlet />
    } @else {
      <!-- Main app shell -->
      <div class="h-screen grid overflow-hidden"
           style="grid-template-columns: 212px 1fr; background: var(--jb-bg); color: var(--jb-text);">

        <!-- ═══ Sidebar ═══ -->
        <aside class="flex flex-col border-r overflow-y-auto jb-scroll"
               style="background: var(--jb-surface); border-color: var(--jb-border); padding: 14px 10px; gap: 14px;">

          <!-- Brand -->
          <div class="flex items-center gap-2.5 px-1.5 pb-2 pt-1">
            <div class="w-[22px] h-[22px] rounded-md flex items-center justify-center font-mono text-[13px] font-bold"
                 style="background: var(--jb-accent-gradient); color: #1c1004;
                        box-shadow: 0 1px 0 rgba(255,255,255,0.18) inset, 0 4px 10px rgba(245,166,35,0.12);">
              jb
            </div>
            <span class="font-semibold text-lg tracking-snug">Jobbuddy</span>
            <span class="ml-auto font-mono text-2xs" style="color: var(--jb-text-dim);">v0.4</span>
          </div>

          <!-- Main nav -->
          <nav class="flex flex-col gap-0.5">
            @for (item of mainNav; track item.id) {
              <a [routerLink]="item.route"
                 routerLinkActive="jb-nav-active"
                 class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none"
                 style="padding: 6px 8px; font-size: 12.5px;"
                 [style.color]="isActive(item.route) ? 'var(--jb-text)' : 'var(--jb-text-mid)'"
                 [style.background]="isActive(item.route) ? 'var(--jb-surface-3)' : 'transparent'"
                 [style.font-weight]="isActive(item.route) ? '500' : '400'">
                <jb-icon [name]="item.icon" [size]="14"
                         [style.color]="item.accent ? 'var(--jb-accent)' : 'inherit'" />
                <span>{{ item.label }}</span>
                @if (item.badge) {
                  <span class="ml-auto px-1.5 rounded-full text-[9.5px] font-semibold tracking-wider uppercase"
                        style="padding: 1px 6px;
                               background: var(--jb-accent-soft); border: 1px solid var(--jb-accent-border);
                               color: var(--jb-accent-2);">
                    {{ item.badge }}
                  </span>
                }
                @if (!item.badge && item.count != null) {
                  <span class="ml-auto font-mono text-2xs" style="color: var(--jb-text-dim);">{{ item.count }}</span>
                }
                @if (!item.badge && item.count == null && item.kbd) {
                  <span class="ml-auto" style="opacity: 0.7;"><span class="kbd">{{ item.kbd }}</span></span>
                }
              </a>
            }
          </nav>

          <!-- Track group -->
          <nav class="flex flex-col gap-0.5">
            <div class="px-2 pt-1.5 pb-0.5 text-2xs font-medium uppercase tracking-widest"
                 style="color: var(--jb-text-dim);">Track</div>
            @for (item of trackNav; track item.id) {
              <a [routerLink]="item.route"
                 routerLinkActive="jb-nav-active"
                 class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none"
                 style="padding: 6px 8px; font-size: 12.5px;"
                 [style.color]="isActive(item.route) ? 'var(--jb-text)' : 'var(--jb-text-mid)'"
                 [style.background]="isActive(item.route) ? 'var(--jb-surface-3)' : 'transparent'"
                 [style.font-weight]="isActive(item.route) ? '500' : '400'">
                <jb-icon [name]="item.icon" [size]="14" />
                <span>{{ item.label }}</span>
                @if (item.count != null) {
                  <span class="ml-auto font-mono text-2xs"
                        [style.color]="item.hot ? 'var(--jb-accent-2)' : 'var(--jb-text-dim)'">{{ item.count }}</span>
                }
              </a>
            }
          </nav>

          <!-- Library group -->
          <nav class="flex flex-col gap-0.5">
            <div class="px-2 pt-1.5 pb-0.5 text-2xs font-medium uppercase tracking-widest"
                 style="color: var(--jb-text-dim);">Library</div>
            @for (item of libraryNav; track item.id) {
              <a [routerLink]="item.route"
                 routerLinkActive="jb-nav-active"
                 class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none"
                 style="padding: 6px 8px; font-size: 12.5px;"
                 [style.color]="isActive(item.route) ? 'var(--jb-text)' : 'var(--jb-text-mid)'"
                 [style.background]="isActive(item.route) ? 'var(--jb-surface-3)' : 'transparent'"
                 [style.font-weight]="isActive(item.route) ? '500' : '400'">
                <jb-icon [name]="item.icon" [size]="14" />
                <span>{{ item.label }}</span>
                @if (item.count != null) {
                  <span class="ml-auto font-mono text-2xs" style="color: var(--jb-text-dim);">{{ item.count }}</span>
                }
              </a>
            }
          </nav>

          <!-- Old Frontend (collapsible) -->
          <nav class="flex flex-col gap-0.5">
            <button (click)="oldFrontendOpen.set(!oldFrontendOpen())"
                    class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none w-full text-left"
                    style="padding: 6px 8px; font-size: 12.5px; color: var(--jb-text-dim); background: transparent; border: none;">
              <jb-icon name="layers" [size]="14" />
              <span>Old frontend</span>
              <jb-icon [name]="oldFrontendOpen() ? 'chev-down' : 'chev-right'" [size]="11" class="ml-auto" />
            </button>
            @if (oldFrontendOpen()) {
              @for (item of oldFrontendNav; track item.id) {
                <a [routerLink]="item.route"
                   class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none"
                   style="padding: 5px 8px 5px 26px; font-size: 11.5px; color: var(--jb-text-dim);">
                  <span>{{ item.label }}</span>
                </a>
              }
            }
          </nav>

          <!-- Footer -->
          <div class="mt-auto flex flex-col gap-0.5">
            <!-- Streak -->
            <div class="flex items-center gap-2.5 rounded-md"
                 style="padding: 6px 8px; font-size: 12.5px; color: var(--jb-text-mid);">
              <jb-icon name="flame" [size]="14" style="color: var(--jb-accent);" />
              <span>12-day streak</span>
            </div>

            <!-- Theme toggle -->
            <button (click)="theme.toggle()"
                    class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none w-full text-left"
                    style="padding: 6px 8px; font-size: 12.5px; color: var(--jb-text-mid); background: transparent; border: none;">
              <jb-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="14" />
              <span>{{ theme.theme() === 'dark' ? 'Light mode' : 'Dark mode' }}</span>
            </button>

            <!-- Settings -->
            <a routerLink="/settings"
               routerLinkActive="jb-nav-active"
               class="jb-nav flex items-center gap-2.5 rounded-md cursor-pointer select-none"
               style="padding: 6px 8px; font-size: 12.5px;"
               [style.color]="isActive('/settings') ? 'var(--jb-text)' : 'var(--jb-text-mid)'"
               [style.background]="isActive('/settings') ? 'var(--jb-surface-3)' : 'transparent'">
              <jb-icon name="settings" [size]="14" />
              <span>Settings</span>
            </a>

            <!-- User -->
            <div class="flex items-center gap-2 mt-1" style="padding: 6px 8px;">
              <div class="w-[26px] h-[26px] rounded-md flex items-center justify-center text-xs font-semibold"
                   style="background: var(--jb-surface-3); border: 1px solid var(--jb-border-strong);">
                {{ userInitials() }}
              </div>
              <div class="flex flex-col min-w-0">
                <div class="text-sm font-medium truncate">{{ userName() }}</div>
                <div class="font-mono text-2xs" style="color: var(--jb-text-dim);">{{ userEmail() }}</div>
              </div>
              <button (click)="logout()"
                      class="ml-auto p-1 rounded cursor-pointer"
                      style="background: transparent; border: none; color: var(--jb-text-dim);"
                      title="Logout">
                <jb-icon name="arrow-right" [size]="12" />
              </button>
            </div>
          </div>
        </aside>

        <!-- ═══ Main area ═══ -->
        <div class="flex flex-col min-w-0 min-h-0">
          <router-outlet />
        </div>
      </div>
    }
  `
})
export class AppComponent {
  auth = inject(AuthService);
  theme = inject(ThemeService);
  private router = inject(Router);

  oldFrontendOpen = signal(false);

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
    { id: 'prompts',      label: 'Prompts',        icon: 'lightbulb', route: '/prompts-new',  count: 9 },
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

  isActive(route: string): boolean {
    return this.router.url === route || this.router.url.startsWith(route + '/');
  }

  isFullscreenRoute(): boolean {
    return this.router.url.startsWith('/onboarding');
  }

  userName(): string {
    // Will be populated from profile API later
    return 'User';
  }

  userEmail(): string {
    return this.auth.currentUser()?.email || '';
  }

  userInitials(): string {
    const name = this.userName();
    return name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase() || 'U';
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
