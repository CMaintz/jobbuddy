import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToggleComponent } from '../../shared/components/jb-toggle/jb-toggle.component';
import { JbSegmentedComponent } from '../../shared/components/jb-segmented/jb-segmented.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { ThemeService } from '../../core/theme.service';
import { AuthService } from '../../core/auth/auth.service';
import { UserPreferences } from '../../core/models/user.model';

type Section = 'match' | 'gen' | 'account' | 'privacy';

/** Generation defaults have no backend home yet — kept client-side so the apply screen can read them. */
export interface GenDefaults {
  voice: string;
  lang: string;
  length: string;
}

const GEN_DEFAULTS_KEY = 'jb-gen-defaults';
const MIN_MATCH_KEY = 'jb-min-match';

export function loadGenDefaults(): GenDefaults {
  try {
    return { voice: 'Warm', lang: 'English', length: 'Standard', ...JSON.parse(localStorage.getItem(GEN_DEFAULTS_KEY) ?? '{}') };
  } catch {
    return { voice: 'Warm', lang: 'English', length: 'Standard' };
  }
}

const INDUSTRY_OPTIONS: { key: string; label: string }[] = [
  { key: 'SOFTWARE_IT', label: 'Software & IT' },
  { key: 'DATA_ANALYTICS', label: 'Data & Analytics' },
  { key: 'DESIGN_UX', label: 'Design & UX' },
  { key: 'ENGINEERING', label: 'Engineering' },
  { key: 'FINANCE', label: 'Finance' },
  { key: 'MARKETING', label: 'Marketing' },
  { key: 'MANAGEMENT', label: 'Management' },
  { key: 'CREATIVE_MEDIA', label: 'Creative & Media' },
];

const SENIORITY_MAP: Record<string, string[]> = {
  'Junior': ['JUNIOR'],
  'Mid': ['MID'],
  'Senior': ['SENIOR'],
  'Staff+': ['LEAD', 'PRINCIPAL', 'EXECUTIVE'],
};

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbToggleComponent, JbSegmentedComponent, JbToastComponent, TagInputComponent],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  theme = inject(ThemeService);
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  Math = Math;

  activeSection = signal<Section>('match');
  loading = signal(true);
  saving = signal(false);
  dirty = signal(false);
  toast = signal('');
  userEmail = '';

  sections: { key: Section; label: string; icon: string }[] = [
    { key: 'match', label: 'Match preferences', icon: 'target' },
    { key: 'gen', label: 'Generation defaults', icon: 'wand' },
    { key: 'account', label: 'Account', icon: 'user' },
    { key: 'privacy', label: 'Privacy & data', icon: 'key' },
  ];

  // Match settings (persisted as UserPreferences)
  boostKeywords: string[] = [];
  avoidKeywords: string[] = [];
  industryOptions = INDUSTRY_OPTIONS;
  industries = new Set<string>();
  seniorityOptions = Object.keys(SENIORITY_MAP);
  seniority = 'Senior';
  remote = true;
  weeklyGoal = 5;
  minMatch = +(localStorage.getItem(MIN_MATCH_KEY) ?? 60);
  private prefs: UserPreferences | null = null;

  // Generation defaults (localStorage)
  gen = loadGenDefaults();

  ngOnInit(): void {
    const section = this.route.snapshot.queryParamMap.get('section') as Section | null;
    if (section && this.sections.some(s => s.key === section)) this.activeSection.set(section);

    this.auth.currentUser$.subscribe(u => this.userEmail = u?.email ?? '');

    this.http.get<UserPreferences>('/api/v1/users/me/preferences').subscribe({
      next: prefs => {
        this.prefs = prefs;
        this.boostKeywords = prefs.positiveSignals ?? [];
        this.avoidKeywords = prefs.negativeSignals ?? [];
        this.industries = new Set(prefs.preferredIndustries ?? []);
        this.seniority = Object.entries(SENIORITY_MAP)
          .find(([, values]) => values.some(v => (prefs.preferredSeniority ?? []).includes(v)))?.[0] ?? 'Senior';
        this.remote = (prefs.preferredRemoteTypes ?? []).length === 0
          || prefs.preferredRemoteTypes.includes('REMOTE');
        this.weeklyGoal = prefs.weeklyApplicationGoal ?? 5;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Could not load preferences');
      }
    });
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  toggleIndustry(key: string): void {
    if (this.industries.has(key)) this.industries.delete(key);
    else this.industries.add(key);
    this.industries = new Set(this.industries);
    this.markDirty();
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);

    localStorage.setItem(GEN_DEFAULTS_KEY, JSON.stringify(this.gen));
    localStorage.setItem(MIN_MATCH_KEY, `${this.minMatch}`);

    const payload: Partial<UserPreferences> = {
      ...this.prefs,
      positiveSignals: this.boostKeywords,
      negativeSignals: this.avoidKeywords,
      preferredIndustries: [...this.industries],
      preferredSeniority: SENIORITY_MAP[this.seniority] ?? [],
      preferredRemoteTypes: this.remote ? [] : ['ON_SITE', 'HYBRID'],
      weeklyApplicationGoal: this.weeklyGoal,
    };

    this.http.put<UserPreferences>('/api/v1/users/me/preferences', payload).subscribe({
      next: prefs => {
        this.prefs = prefs;
        this.saving.set(false);
        this.dirty.set(false);
        this.toast.set('Settings saved');
      },
      error: () => {
        this.saving.set(false);
        this.toast.set('Could not save settings');
      }
    });
  }

  exportData(): void {
    this.http.get('/api/v1/users/me/export').subscribe({
      next: data => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'jobbuddy-export.json';
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => this.toast.set('Export failed')
    });
  }

  deleteAccount(): void {
    const confirmed = window.confirm(
      'This permanently deletes your account and ALL data (profile, CV, applications, documents). This cannot be undone. Continue?');
    if (!confirmed) return;
    this.http.delete('/api/v1/users/me').subscribe({
      next: () => this.auth.logout(),
      error: () => this.toast.set('Account deletion failed')
    });
  }
}
