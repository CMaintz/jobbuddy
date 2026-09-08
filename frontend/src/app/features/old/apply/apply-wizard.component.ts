import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AiApiService } from '../../../core/api/ai.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { catchError, of } from 'rxjs';
import { ResumeDraftApiService } from '../../resume-builder/services/resume-draft-api.service';
import { ApplyWizardStateService, WizardState } from './apply-wizard-state.service';
import { StructuredDocument } from '../../../core/models/structured-document.model';
import { ResumeData, INITIAL_SETTINGS } from '../../resume-builder/models/resume-builder.models';

@Component({
  selector: 'app-apply-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './apply-wizard.component.html',
  styleUrls: ['./apply-wizard.component.css'],
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
