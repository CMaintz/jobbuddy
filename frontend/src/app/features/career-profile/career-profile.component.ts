import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { CareerTargetApiService, CareerTarget, CareerStage } from '../../core/api/career-target.api';
import { RetractedClaimsApiService, RetractedClaim } from '../../core/api/retracted-claims.api';
import { StoryBankApiService, InterviewStory } from '../../core/api/story-bank.api';
import { SkillsApiService, EvidenceGap, SkillCandidate, SkillConfirmation } from '../../core/api/skills.api';

type Section = 'target' | 'skills' | 'stories' | 'retracted';

@Component({
  selector: 'app-career-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbTopbarComponent, JbButtonComponent,
    JbToastComponent, JbIconComponent, TagInputComponent],
  host: { class: 'flex flex-col h-full min-h-0' },
  templateUrl: './career-profile.component.html'
})
export class CareerProfileComponent implements OnInit {
  private targetApi = inject(CareerTargetApiService);
  private claimsApi = inject(RetractedClaimsApiService);
  private storyApi = inject(StoryBankApiService);
  private skillsApi = inject(SkillsApiService);
  private translate = inject(TranslateService);

  activeSection = signal<Section>('target');
  toast = signal('');

  sections: { key: Section; label: string; icon: string }[] = [
    { key: 'target', label: 'careerProfile.section.target', icon: 'target' },
    { key: 'skills', label: 'careerProfile.section.skills', icon: 'bolt' },
    { key: 'stories', label: 'careerProfile.section.stories', icon: 'edit' },
    { key: 'retracted', label: 'careerProfile.section.retracted', icon: 'key' },
  ];

  // ── Career target ──────────────────────────────────────────
  archetypes: string[] = [];
  northStar = '';
  narrative = '';
  culture: string[] = [];
  careerStage: CareerStage | '' = '';
  noticePeriod = '';
  earliestStartDate = '';
  savingTarget = signal(false);
  targetDirty = signal(false);

  readonly careerStages: { value: CareerStage; label: string }[] = [
    { value: 'STUDENT', label: 'careerProfile.stage.student' },
    { value: 'NEW_GRAD', label: 'careerProfile.stage.newGrad' },
    { value: 'EARLY_CAREER', label: 'careerProfile.stage.earlyCareer' },
    { value: 'MID_CAREER', label: 'careerProfile.stage.midCareer' },
    { value: 'SENIOR', label: 'careerProfile.stage.senior' },
    { value: 'LEAD', label: 'careerProfile.stage.lead' },
    { value: 'CAREER_CHANGER', label: 'careerProfile.stage.careerChanger' },
  ];

  // ── Story bank ─────────────────────────────────────────────
  stories = signal<InterviewStory[]>([]);
  storyForm: InterviewStory = this.emptyStory();
  editingStoryId = signal<string | null>(null);
  savingStory = signal(false);

  // ── Retracted claims ───────────────────────────────────────
  claims = signal<RetractedClaim[]>([]);
  newClaim = '';
  newReason = '';
  addingClaim = signal(false);

  ngOnInit(): void {
    this.targetApi.get().subscribe({ next: t => this.applyTarget(t), error: () => {} });
    this.reloadStories();
    this.reloadClaims();
    this.loadCandidates();
    this.loadGaps();
  }

  // ── Career target ──
  private applyTarget(t: CareerTarget): void {
    this.archetypes = t.targetArchetypes ?? [];
    this.northStar = t.northStar ?? '';
    this.narrative = t.narrative ?? '';
    this.culture = t.cultureRequirements ?? [];
    this.careerStage = t.careerStage ?? '';
    this.noticePeriod = t.noticePeriod ?? '';
    this.earliestStartDate = t.earliestStartDate ?? '';
  }

  // ── Skill candidates ───────────────────────────────────────
  candidates = signal<SkillCandidate[]>([]);
  candidatesLoading = signal(false);
  /** Names answered in this round, so a row can disappear the moment it is answered. */
  private answered = new Set<string>();

  loadCandidates(): void {
    this.candidatesLoading.set(true);
    this.answered.clear();
    this.skillsApi.getSkillCandidates(12).subscribe({
      next: rows => { this.candidates.set(rows); this.candidatesLoading.set(false); },
      error: () => this.candidatesLoading.set(false),
    });
  }

  /**
   * Answers one suggestion. Sent immediately rather than batched behind a save button: each row is
   * an independent decision, and a half-finished round should still keep what was decided.
   */
  answer(candidate: SkillCandidate, decision: SkillConfirmation['decision'],
         usedInProduction = false): void {
    if (this.answered.has(candidate.name)) return;
    this.answered.add(candidate.name);
    this.candidates.set(this.candidates().filter(c => c.name !== candidate.name));

    const confirmation: SkillConfirmation = { name: candidate.name, decision, usedInProduction };
    this.skillsApi.confirmSkillCandidates([confirmation]).subscribe({
      next: added => {
        if (added.length) {
          this.toast.set(this.translate.instant('careerProfile.skills.added', { name: candidate.name }));
        }
      },
      // Put the row back rather than silently losing the answer.
      error: () => {
        this.answered.delete(candidate.name);
        this.candidates.set([candidate, ...this.candidates()]);
        this.toast.set(this.translate.instant('careerProfile.skills.failed'));
      },
    });
  }

  // ── Evidence gaps ──────────────────────────────────────────
  gaps = signal<EvidenceGap[]>([]);
  /** The gap currently being answered; only one form is open at a time. */
  openGap = signal<string | null>(null);
  evidenceSituation = '';
  evidenceAction = '';
  evidenceResult = '';
  savingEvidence = signal(false);

  loadGaps(): void {
    this.skillsApi.getEvidenceGaps(5).subscribe({
      next: rows => this.gaps.set(rows),
      error: () => this.gaps.set([]),
    });
  }

  openEvidenceForm(gap: EvidenceGap): void {
    this.openGap.set(gap.skillName);
    this.evidenceSituation = '';
    this.evidenceAction = '';
    this.evidenceResult = '';
  }

  /** Evidence needs substance: context alone proves nothing a letter could cite. */
  get canSaveEvidence(): boolean {
    return this.evidenceAction.trim().length > 0 || this.evidenceResult.trim().length > 0;
  }

  saveEvidence(gap: EvidenceGap): void {
    if (!this.canSaveEvidence || this.savingEvidence()) return;
    this.savingEvidence.set(true);
    this.skillsApi.recordEvidence(gap.skillName, this.evidenceSituation,
      this.evidenceAction, this.evidenceResult).subscribe({
      next: () => {
        this.savingEvidence.set(false);
        this.openGap.set(null);
        this.gaps.set(this.gaps().filter(g => g.skillName !== gap.skillName));
        this.reloadStories();   // it lands in the story bank too
        this.toast.set(this.translate.instant('careerProfile.evidence.saved', { name: gap.skillName }));
      },
      error: () => {
        this.savingEvidence.set(false);
        this.toast.set(this.translate.instant('careerProfile.skills.failed'));
      },
    });
  }

  markTargetDirty(): void { this.targetDirty.set(true); }

  saveTarget(): void {
    if (this.savingTarget()) return;
    this.savingTarget.set(true);
    this.targetApi.update({
      targetArchetypes: this.archetypes,
      northStar: this.northStar.trim() || undefined,
      narrative: this.narrative.trim() || undefined,
      cultureRequirements: this.culture,
      careerStage: this.careerStage || undefined,
      noticePeriod: this.noticePeriod.trim() || undefined,
      earliestStartDate: this.earliestStartDate || undefined,
    }).subscribe({
      next: t => {
        this.applyTarget(t);
        this.savingTarget.set(false);
        this.targetDirty.set(false);
        this.toast.set(this.translate.instant('careerProfile.toast.saved'));
      },
      error: () => {
        this.savingTarget.set(false);
        this.toast.set(this.translate.instant('careerProfile.toast.saveFailed'));
      }
    });
  }

  // ── Story bank ──
  emptyStory(): InterviewStory {
    return { title: '', situation: '', task: '', action: '', result: '', reflection: '', tags: [] };
  }

  newStory(): void { this.storyForm = this.emptyStory(); this.editingStoryId.set(null); }

  editStory(s: InterviewStory): void {
    this.storyForm = { ...s, tags: [...(s.tags ?? [])] };
    this.editingStoryId.set(s.id ?? null);
  }

  saveStory(): void {
    if (this.savingStory() || !this.storyForm.title.trim()) return;
    this.savingStory.set(true);
    this.storyApi.upsert({ ...this.storyForm, id: this.editingStoryId() ?? undefined }).subscribe({
      next: () => {
        this.savingStory.set(false);
        this.newStory();
        this.reloadStories();
        this.toast.set(this.translate.instant('careerProfile.toast.storySaved'));
      },
      error: () => {
        this.savingStory.set(false);
        this.toast.set(this.translate.instant('careerProfile.toast.saveFailed'));
      }
    });
  }

  deleteStory(id?: string): void {
    if (!id) return;
    this.storyApi.remove(id).subscribe({
      next: () => { if (this.editingStoryId() === id) this.newStory(); this.reloadStories(); },
      error: () => {}
    });
  }

  private reloadStories(): void {
    this.storyApi.list().subscribe({ next: s => this.stories.set(s), error: () => {} });
  }

  // ── Retracted claims ──
  addClaim(): void {
    if (this.addingClaim() || !this.newClaim.trim()) return;
    this.addingClaim.set(true);
    this.claimsApi.add(this.newClaim.trim(), this.newReason.trim() || undefined).subscribe({
      next: () => { this.addingClaim.set(false); this.newClaim = ''; this.newReason = ''; this.reloadClaims(); },
      error: () => { this.addingClaim.set(false); this.toast.set(this.translate.instant('careerProfile.toast.saveFailed')); }
    });
  }

  removeClaim(id: string): void {
    this.claimsApi.remove(id).subscribe({ next: () => this.reloadClaims(), error: () => {} });
  }

  private reloadClaims(): void {
    this.claimsApi.list().subscribe({ next: c => this.claims.set(c), error: () => {} });
  }
}
