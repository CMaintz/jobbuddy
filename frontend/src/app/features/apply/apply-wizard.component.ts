import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { catchError, of } from 'rxjs';
import { ResumeDraftApiService } from '../resume-builder/services/resume-draft-api.service';
import { ApplyWizardStateService, WizardState } from './apply-wizard-state.service';
import { StructuredDocument } from '../../core/models/structured-document.model';
import { ResumeData, INITIAL_SETTINGS } from '../resume-builder/models/resume-builder.models';

@Component({
  selector: 'app-apply-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50">
      <!-- Header -->
      <header class="bg-white border-b border-gray-200 px-6 py-4">
        <div class="max-w-3xl mx-auto flex items-center justify-between">
          <div>
            <p class="text-xs text-gray-500 uppercase tracking-wider font-medium">Apply with Resume Builder</p>
            <h1 class="text-lg font-semibold text-gray-900">{{ wiz().jobTitle || 'Preparing application...' }}</h1>
          </div>
          <a routerLink="/jobs" class="text-sm text-gray-500 hover:text-gray-700">Exit</a>
        </div>
      </header>

      <!-- Stepper -->
      <div class="max-w-3xl mx-auto px-6 pt-6">
        <ol class="flex items-center mb-8">
          @for (s of steps; track s.n) {
            <li class="flex items-center" [class.flex-1]="!$last">
              <div class="flex items-center gap-2">
                <span class="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold border-2 transition-colors"
                  [class.bg-blue-600]="wiz().step > s.n"
                  [class.border-blue-600]="wiz().step >= s.n"
                  [class.text-white]="wiz().step > s.n"
                  [class.text-blue-600]="wiz().step === s.n"
                  [class.border-gray-300]="wiz().step < s.n"
                  [class.text-gray-400]="wiz().step < s.n"
                >{{ wiz().step > s.n ? '✓' : s.n }}</span>
                <span class="text-sm font-medium hidden sm:block"
                  [class.text-blue-700]="wiz().step === s.n"
                  [class.text-gray-400]="wiz().step < s.n"
                  [class.text-gray-700]="wiz().step > s.n"
                >{{ s.label }}</span>
              </div>
              @if (!$last) {
                <div class="flex-1 h-0.5 mx-3"
                  [class.bg-blue-600]="wiz().step > s.n"
                  [class.bg-gray-200]="wiz().step <= s.n"></div>
              }
            </li>
          }
        </ol>

        <!-- Step content card -->
        <div class="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">

          <!-- Step 1: Generate -->
          @if (wiz().step === 1) {
            <div class="text-center py-10">
              @if (generating()) {
                <div class="flex flex-col items-center gap-6">
                  <!-- Pulsing ring animation -->
                  <div class="relative w-20 h-20">
                    <div class="absolute inset-0 rounded-full border-4 border-blue-100"></div>
                    <div class="absolute inset-0 rounded-full border-4 border-t-blue-600 animate-spin"></div>
                    <div class="absolute inset-0 flex items-center justify-center text-2xl">🤖</div>
                  </div>
                  <div>
                    <p class="text-gray-800 font-semibold text-lg">{{ loadingMessages[loadingMsgIdx()] }}</p>
                    <p class="text-sm text-gray-400 mt-1">This takes about 30 seconds</p>
                  </div>
                  <!-- Progress dots -->
                  <div class="flex gap-2">
                    @for (step of loadingMessages; track $index) {
                      <div class="w-2 h-2 rounded-full transition-colors duration-500"
                           [class.bg-blue-600]="$index <= loadingMsgIdx()"
                           [class.bg-gray-200]="$index > loadingMsgIdx()"></div>
                    }
                  </div>
                </div>
              } @else if (generateError()) {
                <div class="flex flex-col items-center gap-4">
                  <div class="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center text-red-600 text-2xl">⚠</div>
                  <p class="text-red-700 font-semibold">Generation failed</p>
                  <p class="text-sm text-gray-500">{{ generateError() }}</p>
                  <button class="btn-primary mt-2" (click)="startGenerate()">Try again</button>
                </div>
              }
            </div>
          }

          <!-- Step 2: Edit CV -->
          @if (wiz().step === 2) {
            <div class="flex flex-col gap-5">
              <div class="flex items-start gap-3">
                <div class="w-10 h-10 rounded-full bg-green-100 flex items-center justify-center text-green-600 text-lg flex-shrink-0">✓</div>
                <div>
                  <h2 class="font-semibold text-gray-900">Your tailored CV is ready</h2>
                  <p class="text-sm text-gray-500 mt-0.5">The AI has customised your profile for this role. Open the builder to review and edit it, then come back here to continue.</p>
                </div>
              </div>
              <div class="flex gap-3 flex-wrap">
                <a
                  [routerLink]="['/resume-builder', wiz().draftId]"
                  [queryParams]="{ wizardJobId: wiz().jobId }"
                  class="btn-primary flex-1 text-center"
                >Open in Resume Builder</a>
                <button class="btn-secondary flex-1" (click)="advanceTo(3)">
                  Continue without editing &rarr;
                </button>
              </div>
              <p class="text-xs text-gray-400 text-center">
                In the builder, use the "Continue to Cover Letter" button to return to this wizard.
              </p>
            </div>
          }

          <!-- Step 3: Application Document -->
          @if (wiz().step === 3) {
            <div class="flex flex-col gap-4">
              <div>
                <h2 class="font-semibold text-gray-900">Application Document</h2>
                <p class="text-sm text-gray-500 mt-0.5">Choose the type of document you need for this application.</p>
              </div>

              <!-- Doc type picker (shown before generation) -->
              @if (!coverLetterText() && !generatingCl()) {
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  @for (opt of docTypeOptions; track opt.type) {
                    <button
                      class="text-left p-4 rounded-xl border-2 transition-all"
                      [class.border-blue-500]="selectedDocType === opt.type"
                      [class.bg-blue-50]="selectedDocType === opt.type"
                      [class.border-gray-200]="selectedDocType !== opt.type"
                      [class.hover:border-gray-300]="selectedDocType !== opt.type"
                      (click)="selectedDocType = opt.type"
                    >
                      <div class="flex items-start gap-3">
                        <span class="text-2xl">{{ opt.icon }}</span>
                        <div>
                          <p class="font-medium text-gray-900 text-sm">{{ opt.label }}</p>
                          <p class="text-xs text-gray-500 mt-0.5">{{ opt.description }}</p>
                        </div>
                      </div>
                    </button>
                  }
                </div>

                <!-- Motivation toggle -->
                <div class="border border-gray-200 rounded-xl overflow-hidden">
                  <button
                    class="w-full flex items-center justify-between px-4 py-3 text-left text-sm hover:bg-gray-50 transition-colors"
                    (click)="motivationOpen.set(!motivationOpen())"
                  >
                    <span class="font-medium text-gray-700">Add personal motivation <span class="text-gray-400 font-normal">(optional)</span></span>
                    <svg class="w-4 h-4 text-gray-400 transition-transform" [class.rotate-180]="motivationOpen()"
                         fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                    </svg>
                  </button>
                  @if (motivationOpen()) {
                    <div class="px-4 pb-4 flex flex-col gap-2 border-t border-gray-100">
                      <p class="text-xs text-gray-500 mt-3">Tell the AI why you want this role. This is sent to the AI only and not included verbatim.</p>
                      <textarea
                        class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                        rows="4"
                        placeholder="e.g. I've admired this company's approach to sustainability for years and see this role as a chance to apply my background in product development to a mission I genuinely care about."
                        [(ngModel)]="motivationText"
                      ></textarea>
                      <button
                        class="btn-secondary text-xs self-start"
                        [disabled]="generatingMotivation()"
                        (click)="generateMotivationText()"
                      >{{ generatingMotivation() ? 'Generating...' : 'Generate for me' }}</button>
                    </div>
                  }
                </div>

                <button class="btn-primary" (click)="generateAppDocument()">
                  Generate {{ docTypeLabel }}
                </button>
              }

              @if (generatingCl()) {
                <div class="flex items-center gap-3 py-4">
                  <div class="w-5 h-5 border-2 border-blue-200 border-t-blue-600 rounded-full animate-spin"></div>
                  <span class="text-sm text-gray-600">Writing your {{ docTypeLabel | lowercase }}...</span>
                </div>
              }

              @if (coverLetterText()) {
                <div class="flex items-center justify-between">
                  <span class="text-sm font-medium text-gray-700">{{ docTypeLabel }}</span>
                  <button class="text-xs text-blue-600 hover:underline" (click)="coverLetterText.set(null)">
                    Change type / regenerate
                  </button>
                </div>
                <textarea
                  class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm leading-relaxed focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                  rows="16"
                  [(ngModel)]="coverLetterEditable"
                ></textarea>
                <div class="flex gap-3">
                  <button class="btn-secondary flex-1" (click)="generateAppDocument()">Regenerate</button>
                  <button class="btn-primary flex-1" [disabled]="savingCl()" (click)="saveCoverLetterAndContinue()">
                    {{ savingCl() ? 'Saving...' : 'Save & Continue →' }}
                  </button>
                </div>
              }

              <button class="text-sm text-gray-400 hover:text-gray-600 text-center mt-2" (click)="advanceTo(4)">
                Skip this step
              </button>
            </div>
          }

          <!-- Step 4: Done -->
          @if (wiz().step === 4) {
            <div class="flex flex-col items-center gap-5 py-6">
              <div class="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center text-green-600 text-3xl">✓</div>
              <div class="text-center">
                <h2 class="text-xl font-semibold text-gray-900">Application prepared!</h2>
                <p class="text-sm text-gray-500 mt-1">
                  Your tailored CV{{ wiz().coverLetterContent ? ' and cover letter are' : ' is' }} ready.
                </p>
              </div>
              <div class="flex flex-col gap-3 w-full max-w-sm">
                <a
                  [routerLink]="['/resume-builder', wiz().draftId]"
                  class="btn-primary text-center"
                >Open Resume Builder &amp; Download PDF</a>
                @if (wiz().applicationId) {
                  <button
                    class="btn-secondary"
                    [disabled]="markingApplied()"
                    (click)="markApplied()"
                  >{{ markingApplied() ? 'Updating...' : 'Mark as Applied' }}</button>
                  <a [routerLink]="['/applications', wiz().applicationId]"
                     class="text-sm text-center text-blue-600 hover:underline">
                    View Application
                  </a>
                }
                <a routerLink="/jobs" class="text-sm text-center text-gray-500 hover:text-gray-700">
                  Back to jobs
                </a>
              </div>
            </div>
          }

        </div>
      </div>
    </div>
  `,
  styles: [`
    .btn-primary  { @apply bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed; }
    .btn-secondary { @apply bg-white text-gray-700 px-4 py-2 rounded-lg text-sm font-medium border border-gray-300 hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed; }
  `],
})
export class ApplyWizardComponent implements OnInit, OnDestroy {
  private route        = inject(ActivatedRoute);
  private router       = inject(Router);
  private aiApi        = inject(AiApiService);
  private appsApi      = inject(ApplicationsApiService);
  private draftApi     = inject(ResumeDraftApiService);
  private wizardState  = inject(ApplyWizardStateService);

  readonly steps = [
    { n: 1 as const, label: 'Generate CV' },
    { n: 2 as const, label: 'Edit CV' },
    { n: 3 as const, label: 'Application Doc' },
    { n: 4 as const, label: 'Done' },
  ];

  readonly loadingMessages = [
    'Analysing job requirements...',
    'Matching your experience...',
    'Tailoring your profile...',
    'Drafting content...',
    'Putting it all together...',
  ];

  readonly docTypeOptions = [
    {
      type: 'COVER_LETTER',
      icon: '📝',
      label: 'Cover Letter',
      description: 'Formal letter to the hiring manager. 3–5 paragraphs, signed with your name.',
    },
    {
      type: 'APPLICATION_TEXT',
      icon: '📋',
      label: 'Application Text',
      description: 'Structured text for portal submission fields. Direct and concise.',
    },
  ] as const;

  wiz            = signal<WizardState>({ step: 1, jobId: '', jobTitle: '', draftId: null, applicationId: null, coverLetterContent: null, coverLetterDocId: null });
  generating     = signal(false);
  generateError  = signal<string | null>(null);
  generatingCl   = signal(false);
  savingCl       = signal(false);
  markingApplied = signal(false);
  coverLetterText = signal<string | null>(null);
  coverLetterEditable = '';
  loadingMsgIdx  = signal(0);
  selectedDocType: 'COVER_LETTER' | 'APPLICATION_TEXT' = 'COVER_LETTER';
  motivationOpen = signal(false);
  motivationText = '';
  generatingMotivation = signal(false);

  // Recruiter reply loaded from an existing SAVED application (cold outreach)
  private recruiterReply = '';
  private existingAppId: string | null = null;

  get docTypeLabel(): string {
    return this.docTypeOptions.find(o => o.type === this.selectedDocType)?.label ?? 'Document';
  }

  private loadingTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    const jobId = this.route.snapshot.paramMap.get('jobId') ?? '';
    const stepParam = this.route.snapshot.queryParamMap.get('step');

    const state = this.wizardState.load(jobId);
    if (stepParam) {
      state.step = (parseInt(stepParam, 10) as 1 | 2 | 3 | 4);
      this.wizardState.save(state);
    }
    this.wiz.set(state);

    if (state.step === 3 && state.coverLetterContent) {
      this.coverLetterText.set(state.coverLetterContent);
      this.coverLetterEditable = state.coverLetterContent;
    }

    // Check for an existing application for this job (may have recruiter reply from cold outreach)
    this.appsApi.getAll().subscribe(apps => {
      const existing = apps.find(a => a.jobId === jobId);
      if (existing) {
        this.recruiterReply = existing.recruiterReply ?? '';
        this.existingAppId = existing.id;
      }
      if (state.step === 1 && !state.draftId) {
        this.startGenerate();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.loadingTimer) clearInterval(this.loadingTimer);
  }

  startGenerate(): void {
    const jobId = this.wiz().jobId;
    this.generating.set(true);
    this.generateError.set(null);
    this.loadingMsgIdx.set(0);
    this.loadingTimer = setInterval(() => {
      this.loadingMsgIdx.update(i => Math.min(i + 1, this.loadingMessages.length - 1));
    }, 6000);

    const cvRequest = this.recruiterReply
      ? { jobId, recruiterContext: this.recruiterReply }
      : { jobId };

    this.aiApi.generateStructuredCv(cvRequest as any).subscribe({
      next: doc => {
        const resumeData = structuredDocToResumeData(doc);
        this.draftApi.createDraft({
          name: 'Tailored CV',
          jobId,
          resumeData: resumeData as any,
          settings: INITIAL_SETTINGS as any,
        }).subscribe({
          next: draft => {
            // Reuse existing SAVED application (from cold outreach) or create a new PREPARING one.
            const appId = this.existingAppId;
            const app$ = appId
              ? this.appsApi.updateStatus(appId, 'PREPARING').pipe(
                  // If the transition fails (e.g. already PREPARING), fall back to the existing ID
                  catchError(() => this.appsApi.getById(appId))
                )
              : this.appsApi.create({ jobId, status: 'PREPARING' });
            app$.subscribe({
              next: app => this.finaliseStep1(draft.id ?? null, app.id),
              error:     () => this.finaliseStep1(draft.id ?? null, null),
            });
          },
          error: err => {
            this.generating.set(false);
            this.generateError.set(err?.error?.message ?? 'Failed to save draft.');
          },
        });
      },
      error: err => {
        this.generating.set(false);
        this.generateError.set(err?.error?.message ?? 'Failed to generate CV. Please try again.');
      },
    });
  }

  private finaliseStep1(draftId: string | null, applicationId: string | null): void {
    const updated: WizardState = { ...this.wiz(), step: 2, draftId, applicationId };
    this.wizardState.save(updated);
    this.wiz.set(updated);
    this.generating.set(false);
  }

  advanceTo(step: 1 | 2 | 3 | 4): void {
    const updated = { ...this.wiz(), step };
    this.wizardState.save(updated);
    this.wiz.set(updated);
    if (step === 4) this.publishDraftSilently();
  }

  generateMotivationText(): void {
    this.generatingMotivation.set(true);
    this.aiApi.refine({
      currentContent: '',
      userMessage: 'Draft a short personal motivation statement (2-3 sentences) for why I want this role based on the job description.',
      jobDescription: this.wiz().jobTitle ? `Job: ${this.wiz().jobTitle}` : undefined,
    }).subscribe({
      next: r => {
        this.motivationText = r.refinedContent;
        this.generatingMotivation.set(false);
      },
      error: () => this.generatingMotivation.set(false),
    });
  }

  generateAppDocument(): void {
    this.generatingCl.set(true);
    this.aiApi.generateDocument({
      jobId: this.wiz().jobId,
      documentType: this.selectedDocType,
      recruiterContext: this.recruiterReply || undefined,
      motivationText: this.motivationText || undefined,
    }).subscribe({
      next: doc => {
        const text = doc.bodyContent ?? '';
        this.coverLetterText.set(text);
        this.coverLetterEditable = text;
        this.generatingCl.set(false);
      },
      error: () => this.generatingCl.set(false),
    });
  }

  saveCoverLetterAndContinue(): void {
    this.savingCl.set(true);
    const updated: WizardState = { ...this.wiz(), step: 4, coverLetterContent: this.coverLetterEditable };
    this.wizardState.save(updated);
    this.wiz.set(updated);
    this.savingCl.set(false);
    this.publishDraftSilently();
  }

  private publishDraftSilently(): void {
    const draftId = this.wiz().draftId;
    if (!draftId) return;
    this.draftApi.publishDraft(draftId).subscribe({ error: () => { /* silent — publish is best-effort */ } });
  }

  markApplied(): void {
    const appId = this.wiz().applicationId;
    if (!appId) return;
    this.markingApplied.set(true);
    this.appsApi.updateStatus(appId, 'APPLIED').subscribe({
      next: () => {
        this.markingApplied.set(false);
        this.wizardState.clear();
        this.router.navigate(['/applications', appId]);
      },
      error: () => this.markingApplied.set(false),
    });
  }
}

// ── StructuredDocument → ResumeData mapping ───────────────────────────────────

function structuredDocToResumeData(doc: StructuredDocument): ResumeData {
  const id = doc.identity ?? {};
  const sections = doc.sections ?? [];
  const find = (type: string) => sections.find(s => s.type === type);

  return {
    personalInfo: {
      fullName: id.name      ?? '',
      title:    id.headline  ?? '',
      email:    id.email     ?? '',
      phone:    id.phone     ?? '',
      location: id.location  ?? '',
      website:  id.websiteUrl  ?? '',
      linkedin: id.linkedinUrl ?? '',
      github:   id.githubUrl   ?? '',
      twitter:  '',
      summary:  find('profile')?.body ?? '',
      photoUrl: id.profileImageUrl,
    },
    experience: (find('experience')?.items ?? []).map(item => ({
      id:          item.sourceId ?? crypto.randomUUID(),
      title:       item.title    ?? '',
      company:     item.subtitle ?? '',
      location:    item.location ?? '',
      startDate:   '',
      endDate:     item.dateRange ?? '',
      current:     false,
      description: item.description ?? (item.bullets ?? []).join('\n'),
      skills:      item.skills ?? item.technologies ?? [],
    })),
    education: (find('education')?.items ?? []).map(item => ({
      id:        item.sourceId ?? crypto.randomUUID(),
      degree:    item.title    ?? '',
      school:    item.subtitle ?? '',
      location:  item.location ?? '',
      startDate: '',
      endDate:   item.dateRange ?? '',
      current:   false,
      skills:    item.skills ?? [],
    })),
    projects: (find('projects')?.items ?? []).map(item => ({
      id:          item.sourceId ?? crypto.randomUUID(),
      name:        item.title    ?? '',
      link:        (item.links ?? [])[0] ?? '',
      date:        item.dateRange ?? '',
      description: item.description ?? (item.bullets ?? []).join('\n'),
      skills:      item.skills ?? item.technologies ?? [],
    })),
    skills: (find('skills')?.items ?? []).map(item => ({
      id:    crypto.randomUUID(),
      name:  item.title ?? '',
      level: 3,
    })),
    languages: (find('languages')?.items ?? []).map(item => ({
      id:          crypto.randomUUID(),
      name:        item.title ?? '',
      proficiency: 'Intermediate' as const,
    })),
    certifications: (find('certifications')?.items ?? []).map(item => ({
      id:     item.sourceId ?? crypto.randomUUID(),
      name:   item.title    ?? '',
      issuer: item.subtitle ?? '',
      date:   item.dateRange ?? '',
      link:   undefined,
    })),
    strengths: [],
    socials:   [],
  };
}
