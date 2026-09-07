import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToggleComponent } from '../../shared/components/jb-toggle/jb-toggle.component';
import { JbSegmentedComponent } from '../../shared/components/jb-segmented/jb-segmented.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { AiUsagePanelComponent } from './ai-usage-panel.component';
import { SkillTaxonomyPanelComponent } from './skill-taxonomy-panel.component';
import { AiKeyPanelComponent } from './ai-key-panel.component';
import { ThemeService } from '../../core/theme.service';
import { AuthService } from '../../core/auth/auth.service';
import { UserPreferences } from '../../core/models/user.model';
import { WritingProfileApiService, WritingProfile } from '../../core/api/writing-profile.api';
import { AiApiService } from '../../core/api/ai.api';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { StructuredDocumentTemplatesApiService } from '../../core/api/structured-document-templates.api';
import { DocumentTemplateOption } from '../../core/models/structured-document.model';

type Section = 'match' | 'gen' | 'style' | 'usage' | 'account' | 'privacy' | 'taxonomy';

/** Generation defaults have no backend home yet — kept client-side so the apply screen can read them. */
export interface GenDefaults {
  voice: string;
  lang: string;
  length: string;
  /** Default CV template id used by Quick apply (undefined = system default). */
  cvTemplate?: string;
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
  { key: 'SOFTWARE_IT', label: 'settings.industry.softwareIt' },
  { key: 'DATA_ANALYTICS', label: 'settings.industry.dataAnalytics' },
  { key: 'DESIGN_UX', label: 'settings.industry.designUx' },
  { key: 'ENGINEERING', label: 'settings.industry.engineering' },
  { key: 'FINANCE', label: 'settings.industry.finance' },
  { key: 'MARKETING', label: 'settings.industry.marketing' },
  { key: 'MANAGEMENT', label: 'settings.industry.management' },
  { key: 'CREATIVE_MEDIA', label: 'settings.industry.creativeMedia' },
];

const SENIORITY_MAP: Record<string, string[]> = {
  'Junior': ['JUNIOR'],
  'Mid': ['MID'],
  'Senior': ['SENIOR'],
  'Staff+': ['LEAD', 'PRINCIPAL', 'EXECUTIVE'],
};

/** Document types worth analyzing for voice — prose the user may have edited, not CVs. */
const ANALYZABLE_DOC_TYPES: Record<string, string> = {
  COVER_LETTER: 'settings.docType.coverLetter',
  APPLICATION_TEXT: 'settings.docType.application',
  UNSOLICITED_APPLICATION: 'settings.docType.unsolicited',
  RECRUITER_MESSAGE: 'settings.docType.recruiterMessage',
  FOLLOW_UP_MESSAGE: 'settings.docType.followUp',
};

/** Form values captured before an analysis proposal is applied, so it can be reviewed/undone. */
interface StyleSnapshot {
  tone: string;
  vocabulary: string;
  phrases: string[];
  dos: string[];
  donts: string[];
  structure: string;
  excerpts?: string[];
  lastAnalyzedAt?: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbTopbarComponent, JbButtonComponent, JbToggleComponent, JbSegmentedComponent, JbToastComponent, TagInputComponent, JbModalComponent, DiffViewerComponent, AiUsagePanelComponent, AiKeyPanelComponent, SkillTaxonomyPanelComponent],
  templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
  theme = inject(ThemeService);
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private writingApi = inject(WritingProfileApiService);
  private aiApi = inject(AiApiService);
  private templatesApi = inject(StructuredDocumentTemplatesApiService);
  private translate = inject(TranslateService);

  /** CV templates available for the Quick-apply default picker. */
  cvTemplates = signal<DocumentTemplateOption[]>([]);
  Math = Math;

  activeSection = signal<Section>('match');
  loading = signal(true);
  saving = signal(false);
  dirty = signal(false);
  toast = signal('');
  userEmail = '';
  emailVerified = false;
  provider = 'password';
  sendingVerification = signal(false);
  sendingReset = signal(false);

  sections: { key: Section; label: string; icon: string }[] = [
    { key: 'match', label: 'settings.section.match', icon: 'target' },
    { key: 'gen', label: 'settings.section.gen', icon: 'wand' },
    { key: 'style', label: 'settings.section.style', icon: 'edit' },
    { key: 'usage', label: 'settings.section.usage', icon: 'chart-up' },
    { key: 'account', label: 'settings.section.account', icon: 'user' },
    { key: 'privacy', label: 'settings.section.privacy', icon: 'key' },
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
  digestEmail = false;
  minMatch = +(localStorage.getItem(MIN_MATCH_KEY) ?? 60);
  private prefs: UserPreferences | null = null;

  // Generation defaults (localStorage)
  gen = loadGenDefaults();

  // Writing style (persisted as WritingProfile)
  styleTone = '';
  styleVocabulary = '';
  stylePhrases: string[] = [];
  styleDos: string[] = [];
  styleDonts: string[] = [];
  styleStructure = '';
  private writingProfile: WritingProfile | null = null;
  private styleDirty = false;

  // Writing-style analysis ("learn my voice from samples")
  showAnalyze = signal(false);
  analyzing = signal(false);
  analyzeSamples: string[] = [''];
  pickerDocs = signal<GeneratedDocument[]>([]);
  selectedDocIds = new Set<string>();
  analyzedAt = signal<string | undefined>(undefined);
  /** Non-null while an applied analysis awaits review; drives the changed-field markers. */
  analysisBaseline: StyleSnapshot | null = null;

  ngOnInit(): void {
    const section = this.route.snapshot.queryParamMap.get('section') as Section | null;
    if (section && this.sections.some(s => s.key === section)) this.activeSection.set(section);

    this.auth.currentUser$.subscribe(u => {
      this.userEmail = u?.email ?? '';
      // The taxonomy is shared reference data, so its review queue is an admin section —
      // added to the nav rather than rendered and refused, which would just be a dead tab.
      const isAdmin = u?.role === 'ADMIN';
      const listed = this.sections.some(s => s.key === 'taxonomy');
      if (isAdmin && !listed) {
        this.sections = [...this.sections, { key: 'taxonomy', label: 'settings.section.taxonomy', icon: 'sparkle' }];
      }
    });
    this.emailVerified = this.auth.isEmailVerified();
    this.provider = this.auth.signInProvider();

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
        this.digestEmail = prefs.notificationEnabled && prefs.notificationFrequency === 'WEEKLY';
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set(this.translate.instant('settings.toast.loadFailed'));
      }
    });

    this.writingApi.get().subscribe({
      next: wp => {
        this.writingProfile = wp;
        this.styleTone = wp.tone ?? '';
        this.styleVocabulary = wp.vocabularyNotes ?? '';
        this.stylePhrases = wp.phrasingPatterns ?? [];
        this.styleDos = wp.dos ?? [];
        this.styleDonts = wp.donts ?? [];
        this.styleStructure = wp.structureNotes ?? '';
        this.analyzedAt.set(wp.lastAnalyzedAt);
      },
      error: () => {}
    });

    this.templatesApi.getActive().subscribe({
      next: templates => this.cvTemplates.set(templates.filter(t =>
        !t.documentTypes || t.documentTypes.length === 0 || t.documentTypes.some(d => String(d) === 'CV'))),
      error: () => {}
    });
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  markStyleDirty(): void {
    this.styleDirty = true;
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
      notificationEnabled: this.digestEmail,
      notificationFrequency: this.digestEmail ? 'WEEKLY' : this.prefs?.notificationFrequency ?? null,
    };

    this.http.put<UserPreferences>('/api/v1/users/me/preferences', payload).subscribe({
      next: prefs => {
        this.prefs = prefs;
        this.saving.set(false);
        this.dirty.set(false);
        this.toast.set(this.translate.instant('settings.toast.saved'));
      },
      error: () => {
        this.saving.set(false);
        this.toast.set(this.translate.instant('settings.toast.saveFailed'));
      }
    });

    if (this.styleDirty) this.saveWritingStyle();
  }

  private saveWritingStyle(): void {
    const wp: WritingProfile = {
      ...this.writingProfile,
      tone: this.styleTone.trim() || undefined,
      vocabularyNotes: this.styleVocabulary.trim() || undefined,
      phrasingPatterns: this.stylePhrases,
      dos: this.styleDos,
      donts: this.styleDonts,
      structureNotes: this.styleStructure.trim() || undefined,
    };
    this.writingApi.update(wp).subscribe({
      next: saved => {
        this.writingProfile = saved;
        this.styleDirty = false;
        this.analyzedAt.set(saved.lastAnalyzedAt);
        this.analysisBaseline = null;
      },
      error: () => this.toast.set(this.translate.instant('settings.toast.styleSaveFailed'))
    });
  }

  openAnalyze(): void {
    this.showAnalyze.set(true);
    if (this.pickerDocs().length) return;
    this.aiApi.getDocuments().subscribe({
      next: docs => this.pickerDocs.set(
        docs.filter(d => ANALYZABLE_DOC_TYPES[d.documentType] && d.content?.trim())
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
            .slice(0, 20)),
      error: () => {}
    });
  }

  addSample(): void {
    if (this.analyzeSamples.length < 3) this.analyzeSamples.push('');
  }

  removeSample(index: number): void {
    this.analyzeSamples.splice(index, 1);
  }

  toggleDoc(id: string): void {
    if (this.selectedDocIds.has(id)) this.selectedDocIds.delete(id);
    else this.selectedDocIds.add(id);
  }

  docTypeLabel(type: string): string {
    return ANALYZABLE_DOC_TYPES[type] ?? type;
  }

  canAnalyze(): boolean {
    return this.analyzeSamples.join('').trim().length >= 120 || this.selectedDocIds.size > 0;
  }

  runAnalysis(): void {
    if (this.analyzing() || !this.canAnalyze()) return;
    this.analyzing.set(true);
    this.writingApi.analyze({
      samples: this.analyzeSamples.map(s => s.trim()).filter(Boolean),
      documentIds: [...this.selectedDocIds],
    }).subscribe({
      next: proposal => {
        this.analyzing.set(false);
        this.showAnalyze.set(false);
        this.applyProposal(proposal);
      },
      error: () => {
        this.analyzing.set(false);
        this.toast.set(this.translate.instant('settings.toast.analysisFailed'));
      }
    });
  }

  /** Prefills the form with the proposal; the user reviews the marked changes and saves as usual. */
  private applyProposal(p: WritingProfile): void {
    this.analysisBaseline = {
      tone: this.styleTone,
      vocabulary: this.styleVocabulary,
      phrases: [...this.stylePhrases],
      dos: [...this.styleDos],
      donts: [...this.styleDonts],
      structure: this.styleStructure,
      excerpts: this.writingProfile?.exampleExcerpts,
      lastAnalyzedAt: this.writingProfile?.lastAnalyzedAt,
    };
    if (p.tone?.trim()) this.styleTone = p.tone.trim();
    if (p.vocabularyNotes?.trim()) this.styleVocabulary = p.vocabularyNotes.trim();
    if (p.structureNotes?.trim()) this.styleStructure = p.structureNotes.trim();
    this.stylePhrases = this.mergeTags(this.stylePhrases, p.phrasingPatterns);
    this.styleDos = this.mergeTags(this.styleDos, p.dos);
    this.styleDonts = this.mergeTags(this.styleDonts, p.donts);
    this.writingProfile = {
      ...this.writingProfile,
      exampleExcerpts: p.exampleExcerpts?.length ? p.exampleExcerpts : this.writingProfile?.exampleExcerpts,
      lastAnalyzedAt: p.lastAnalyzedAt,
    };
    this.markStyleDirty();
  }

  /** Hand-tuned entries are kept; the proposal's new ones are appended at the end. */
  private mergeTags(current: string[], proposed?: string[]): string[] {
    if (!proposed?.length) return current;
    const seen = new Set(current.map(t => t.trim().toLowerCase()));
    return [...current, ...proposed.filter(t => t.trim() && !seen.has(t.trim().toLowerCase()))];
  }

  undoAnalysis(): void {
    const b = this.analysisBaseline;
    if (!b) return;
    this.styleTone = b.tone;
    this.styleVocabulary = b.vocabulary;
    this.styleStructure = b.structure;
    this.stylePhrases = [...b.phrases];
    this.styleDos = [...b.dos];
    this.styleDonts = [...b.donts];
    this.writingProfile = { ...this.writingProfile, exampleExcerpts: b.excerpts, lastAnalyzedAt: b.lastAnalyzedAt };
    this.analysisBaseline = null;
  }

  changed(field: keyof StyleSnapshot): boolean {
    const b = this.analysisBaseline;
    if (!b) return false;
    switch (field) {
      case 'tone': return b.tone !== this.styleTone;
      case 'vocabulary': return b.vocabulary !== this.styleVocabulary;
      case 'structure': return b.structure !== this.styleStructure;
      case 'phrases': return b.phrases.join('') !== this.stylePhrases.join('');
      case 'dos': return b.dos.join('') !== this.styleDos.join('');
      case 'donts': return b.donts.join('') !== this.styleDonts.join('');
      default: return false;
    }
  }

  get providerLabel(): string {
    if (this.provider === 'google.com') return 'settings.provider.google';
    if (this.provider === 'password') return 'settings.provider.password';
    return 'settings.provider.linkedin';
  }

  resendVerification(): void {
    if (this.sendingVerification()) return;
    this.sendingVerification.set(true);
    this.auth.resendVerification()
      .then(() => this.toast.set(this.translate.instant('settings.toast.verificationSent')))
      .catch(() => this.toast.set(this.translate.instant('settings.toast.verificationFailed')))
      .finally(() => this.sendingVerification.set(false));
  }

  sendPasswordReset(): void {
    if (this.sendingReset()) return;
    this.sendingReset.set(true);
    this.auth.sendPasswordReset()
      .then(() => this.toast.set(this.translate.instant('settings.toast.resetSent', { email: this.userEmail })))
      .catch(() => this.toast.set(this.translate.instant('settings.toast.resetFailed')))
      .finally(() => this.sendingReset.set(false));
  }

  signOut(): void {
    this.auth.logout().then(() => window.location.href = '/login');
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
      error: () => this.toast.set(this.translate.instant('settings.toast.exportFailed'))
    });
  }

  deleteAccount(): void {
    const confirmed = window.confirm(this.translate.instant('settings.deleteConfirm'));
    if (!confirmed) return;
    this.http.delete('/api/v1/users/me').subscribe({
      next: () => this.auth.logout(),
      error: () => this.toast.set(this.translate.instant('settings.toast.deleteFailed'))
    });
  }
}
