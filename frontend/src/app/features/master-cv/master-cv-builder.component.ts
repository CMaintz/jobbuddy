import { Component, OnInit, ViewChild, WritableSignal, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, forkJoin, of } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { PhotoCropDialogComponent } from '../resume-builder/shared/photo-crop-dialog.component';
import { AiRefineMenuComponent } from '../resume-builder/shared/ai-refine-menu.component';
import { JbDropdownComponent } from '../../shared/components/jb-dropdown/jb-dropdown.component';
import { MasterCvPreviewComponent } from './master-cv-preview.component';
import { McvStrengthsSectionComponent } from './sections/mcv-strengths-section.component';
import { McvExperienceSectionComponent } from './sections/mcv-experience-section.component';
import { McvEducationSectionComponent } from './sections/mcv-education-section.component';
import { McvProjectsSectionComponent } from './sections/mcv-projects-section.component';
import { McvCertificationsSectionComponent } from './sections/mcv-certifications-section.component';
import { McvLanguagesSectionComponent } from './sections/mcv-languages-section.component';
import { McvSocialsSectionComponent } from './sections/mcv-socials-section.component';
import { McvCustomSectionsSectionComponent } from './sections/mcv-custom-sections-section.component';
import { AtsPdfService, structuredDocToAts } from '../../shared/services/ats-pdf.service';
import { translatedAtsHeadings } from '../../shared/services/ats-headings';
import { AiApiService } from '../../core/api/ai.api';
import { StructuredDocument } from '../../core/models/structured-document.model';
import { ProfileSectionsApiService, FullProfileResponse } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { ProfileStrengthApiService } from '../../core/api/profile-strength.api';
import { ProfileSocialApiService } from '../../core/api/profile-social.api';
import { SkillsApiService } from '../../core/api/skills.api';
import { ProfileSkill, TECH_CATEGORIES } from '../../core/models/skill-taxonomy.model';
import { Profile, ProfilePrivateInfo } from '../../core/models/user.model';
import {
  WorkExperience, Education, Project, Certification,
  SpokenLanguage, ProfileSocial, ProfileStrength, CustomSection
} from '../../core/models/profile-section.model';

import { NotifyService } from '../../shared/services/notify.service';
interface CvSection {
  key: string;
  label: string;
  count: string;
  empty?: boolean;
}

@Component({
  selector: 'app-master-cv-builder',
  imports: [
    FormsModule, RouterLink,
    JbTopbarComponent, JbIconComponent, JbButtonComponent,
    TagInputComponent, PhotoCropDialogComponent, AiRefineMenuComponent,
    JbDropdownComponent, MasterCvPreviewComponent,
    McvStrengthsSectionComponent, McvExperienceSectionComponent, McvEducationSectionComponent,
    McvProjectsSectionComponent, McvCertificationsSectionComponent,
    McvLanguagesSectionComponent, McvSocialsSectionComponent, McvCustomSectionsSectionComponent,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './master-cv-builder.component.html'
})
export class MasterCvBuilderComponent implements OnInit {
  private http = inject(HttpClient);
  protected notify = inject(NotifyService);
  private skillsApi = inject(SkillsApiService);
  private translate = inject(TranslateService);
  private profileApi = inject(ProfileSectionsApiService);
  private privateApi = inject(ProfilePrivateApiService);
  private strengthApi = inject(ProfileStrengthApiService);
  private socialApi = inject(ProfileSocialApiService);
  private aiApi = inject(AiApiService);
  private atsPdf = inject(AtsPdfService);

  loading = signal(true);
  saving = signal(false);
  dirty = signal(false);
  activeSec = signal('Header');

  // Every edit replaces these values rather than mutating them, which is what lets the OnPush
  // preview and section editors see a change.
  profile = signal<Profile>({});
  privateInfo = signal<ProfilePrivateInfo>({});
  experienceList = signal<WorkExperience[]>([]);
  educationList = signal<Education[]>([]);
  projectsList = signal<Project[]>([]);
  certificationsList = signal<Certification[]>([]);
  languagesList = signal<SpokenLanguage[]>([]);
  socialsList = signal<ProfileSocial[]>([]);
  strengthsList = signal<ProfileStrength[]>([]);
  customSectionsList = signal<CustomSection[]>([]);

  @ViewChild(McvCertificationsSectionComponent) certSection?: McvCertificationsSectionComponent;

  cropSrc = signal<string | null>(null);
  uploadingPhoto = signal(false);

  // ATS-safe PDF export (full or anonymised)
  exportingPdf = signal(false);

  /**
   * The user's skills, one list.
   *
   * <p>There used to be two free-text editors here, "Skills" and "Technologies", backed by two
   * columns on the profile. Skills are one thing now — a row each, carrying its taxonomy category
   * — and whether one counts as a technology is read off that category. Two editors could no
   * longer honestly control which bucket a typed skill landed in, so there is one.
   */
  profileSkills = signal<ProfileSkill[]>([]);

  skillsList = computed(() => this.profileSkills().map(s => s.skillName));

  /** The technical slice, for the preview. Derived from category, exactly as the backend does it. */
  technologiesList = computed(() => this.profileSkills()
    .filter(s => s.category && TECH_CATEGORIES.has(s.category))
    .map(s => s.skillName));

  interestsList = computed(() => this.profile().interests ?? []);

  coverage = computed(() => {
    const experience = this.experienceList();
    const checks = [
      (this.profile().summary?.length ?? 0) > 60,
      this.strengthsList().length >= 3,
      experience.length >= 2,
      this.skillsList().length >= 6,
      this.educationList().length >= 1,
      this.languagesList().length >= 1,
      experience.some(j => (j.description?.length ?? 0) > 50),
      this.projectsList().length >= 1,
    ];
    return Math.round((checks.filter(Boolean).length / 8) * 100);
  });

  sections(): CvSection[] {
    const words = (this.profile().summary ?? '').trim().split(/\s+/).filter(Boolean).length;
    const count = (list: unknown[]) => ({ count: `${list.length}`, empty: list.length === 0 });
    return [
      { key: 'Header', label: 'masterCv.sec.header', count: this.translate.instant('masterCv.count.fields', { n: 5 }) },
      { key: 'Profile', label: 'masterCv.sec.profile', count: this.translate.instant('masterCv.count.words', { n: words }) },
      { key: 'Strengths', label: 'masterCv.sec.strengths', count: `${this.strengthsList().length}` },
      { key: 'Experience', label: 'masterCv.sec.experience', count: this.translate.instant('masterCv.count.roles', { n: this.experienceList().length }) },
      { key: 'Skills', label: 'masterCv.sec.skills', count: `${this.skillsList().length + this.technologiesList().length}` },
      { key: 'Education', label: 'masterCv.sec.education', count: `${this.educationList().length}` },
      { key: 'Projects', label: 'masterCv.sec.projects', ...count(this.projectsList()) },
      { key: 'Certifications', label: 'masterCv.sec.certifications', ...count(this.certificationsList()) },
      { key: 'Languages', label: 'masterCv.sec.languages', count: `${this.languagesList().length}` },
      { key: 'Socials', label: 'masterCv.sec.socials', ...count(this.socialsList()) },
      { key: 'Custom', label: 'masterCv.sec.custom', ...count(this.customSectionsList()) },
    ];
  }

  sectionHint(): string {
    const hints: Record<string, string> = {
      Header: 'masterCv.hint.header',
      Profile: 'masterCv.hint.profile',
      Strengths: 'masterCv.hint.strengths',
      Experience: 'masterCv.hint.experience',
      Skills: 'masterCv.hint.skills',
      Education: 'masterCv.hint.education',
      Projects: 'masterCv.hint.projects',
      Certifications: 'masterCv.hint.certifications',
      Languages: 'masterCv.hint.languages',
      Socials: 'masterCv.hint.socials',
      Custom: 'masterCv.hint.custom',
    };
    const key = hints[this.activeSec()];
    return key ? this.translate.instant(key) : '';
  }

  ngOnInit(): void {
    forkJoin({
      full: this.profileApi.getFullProfile(),
      priv: this.privateApi.getPrivateInfo(),
      skills: this.skillsApi.getProfileSkills(),
    }).subscribe({
      next: ({ full, priv, skills }) => {
        this.applyFullProfile(full);
        this.privateInfo.set(priv ?? {});
        this.profileSkills.set(skills);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notify.error('masterCv.toast.loadFailed');
      }
    });
  }

  private applyFullProfile(resp: FullProfileResponse): void {
    this.profile.set(resp.profile ?? {});
    this.experienceList.set(resp.experience || []);
    this.educationList.set(resp.education || []);
    this.projectsList.set(resp.projects || []);
    this.certificationsList.set(resp.certifications || []);
    this.languagesList.set(resp.languages || []);
    this.socialsList.set(resp.socials || []);
    this.strengthsList.set(resp.strengths || []);
    this.customSectionsList.set(resp.customSections || []);
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  patchProfile(patch: Partial<Profile>): void {
    this.profile.update(p => ({ ...p, ...patch }));
    this.markDirty();
  }

  patchPrivateInfo(patch: Partial<ProfilePrivateInfo>): void {
    this.privateInfo.update(p => ({ ...p, ...patch }));
    this.markDirty();
  }

  // ── PDF export ────────────────────────────────────────────────

  /**
   * Text-based ATS-safe PDF of the master CV, optionally anonymised.
   * Styled/designed exports live in the CV Builder — the master CV is
   * plain by design, so its export is always parseable text.
   */
  exportPdf(anonymised: boolean): void {
    if (this.exportingPdf()) return;
    this.exportingPdf.set(true);
    this.aiApi.getCvRenderModel().subscribe({
      next: async doc => {
        try {
          const model = structuredDocToAts(anonymised ? this.anonymiseDoc(doc) : doc,
            translatedAtsHeadings(key => this.translate.instant(key)));
          const filename = anonymised
            ? 'master-cv-anonymised'
            : `${this.privateInfo().fullName || 'master-cv'} - CV`;
          await this.atsPdf.downloadResume(model, filename);
        } catch {
          this.notify.error('masterCv.toast.pdfFailed');
        } finally {
          this.exportingPdf.set(false);
        }
      },
      error: () => {
        this.exportingPdf.set(false);
        this.notify.error('masterCv.toast.renderFailed');
      }
    });
  }

  /** Masks identity: initials only, no contact details, links, or photo. */
  private anonymiseDoc(doc: StructuredDocument): StructuredDocument {
    const initials = (doc.identity?.name ?? '').trim().split(/\s+/).filter(Boolean)
      .map(p => p[0].toUpperCase() + '.').join(' ');
    return {
      ...doc,
      identity: {
        name: initials || this.translate.instant('masterCv.candidate'),
        headline: doc.identity?.headline,
        location: doc.identity?.location,
      },
      options: { ...(doc.options ?? { showProfileImage: false }), showProfileImage: false },
    };
  }

  // ── Profile photo ─────────────────────────────────────────────

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => this.cropSrc.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  recropPhoto(): void {
    const photoUrl = this.privateInfo().photoUrl;
    if (photoUrl) this.cropSrc.set(photoUrl);
  }

  onPhotoCropped(dataUrl: string): void {
    this.cropSrc.set(null);
    this.uploadingPhoto.set(true);
    const formData = new FormData();
    formData.append('file', dataUrlToBlob(dataUrl), 'photo.jpg');
    this.http.post<{ url: string }>('/api/v1/users/me/profile/photo', formData).subscribe({
      next: res => {
        this.privateInfo.update(p => ({ ...p, photoUrl: res.url }));
        this.uploadingPhoto.set(false);
        this.notify.success('masterCv.toast.photoUpdated');
      },
      error: () => {
        this.uploadingPhoto.set(false);
        this.notify.error('masterCv.toast.photoFailed');
      }
    });
  }

  removePhoto(): void {
    this.privateInfo.update(p => ({ ...p, photoUrl: '' }));
    this.privateApi.updatePrivateInfo({ photoUrl: '' }).subscribe({
      error: () => this.notify.error('masterCv.toast.photoRemoveFailed')
    });
  }

  /**
   * Reconciles the edited list against the stored skills.
   *
   * <p>Saved immediately rather than on Save All: a skill is its own row, so adding one is a
   * create and removing one is a delete — there is no profile field to fold them into. The
   * backend resolves each new name against the taxonomy, so a skill typed here arrives with the
   * same category and link as one picked from the career-profile autocomplete.
   */
  onSkillsChange(value: string[]): void {
    const wanted = value.map(v => v.trim()).filter(v => v.length > 0);
    const wantedKeys = new Set(wanted.map(v => v.toLowerCase()));
    const heldKeys = new Set(this.profileSkills().map(s => s.skillName.toLowerCase()));

    const added = wanted.filter(v => !heldKeys.has(v.toLowerCase()));
    const removed = this.profileSkills().filter(s => !wantedKeys.has(s.skillName.toLowerCase()));

    const ops: Observable<unknown>[] = [
      ...added.map((name, i) => this.skillsApi.addProfileSkill({
        skillName: name,
        proficiencyLevel: 'INTERMEDIATE',
        usedInProduction: false,
        displayOrder: this.profileSkills().length + i,
      })),
      ...removed.filter(s => s.id).map(s => this.skillsApi.deleteProfileSkill(s.id!)),
    ];
    if (ops.length === 0) {
      this.resetSkillsInput();
      return;
    }

    forkJoin(ops).subscribe({
      next: () => this.reloadSkills(),
      error: () => {
        this.resetSkillsInput();
        this.notify.error('masterCv.toast.saveFailed');
      }
    });
  }

  /**
   * The tag input keeps its own copy of the chips, so one that was not saved (a failed call, or a
   * case-only duplicate of a held skill) stays visible until it is handed a new list.
   */
  private resetSkillsInput(): void {
    this.profileSkills.update(skills => [...skills]);
  }

  private reloadSkills(): void {
    this.skillsApi.getProfileSkills().subscribe(skills => this.profileSkills.set(skills));
  }

  onInterestsChange(value: string[]): void {
    this.patchProfile({ interests: value });
  }

  // ── Add / remove list entries (persisted on Save) ─────────────

  addExperience(): void {
    this.experienceList.update(l => [{ companyName: '', title: '', startDate: '', isCurrent: false, description: '' }, ...l]);
    this.markDirty();
  }

  addEducation(): void {
    this.educationList.update(l => [{ institution: '', degree: '' }, ...l]);
    this.markDirty();
  }

  addProject(): void {
    this.projectsList.update(l => [{ name: '', description: '' }, ...l]);
    this.markDirty();
  }

  addLanguage(): void {
    this.languagesList.update(l => [...l, { language: '', proficiency: 'FLUENT', displayOrder: l.length }]);
    this.markDirty();
  }

  addSocial(): void {
    this.socialsList.update(l => [...l, { platform: 'GitHub', url: '', iconKey: 'github', displayOrder: l.length }]);
    this.markDirty();
  }

  addStrength(): void {
    this.strengthsList.update(l => [...l, { title: '', description: '', iconKey: 'star', displayOrder: l.length }]);
    this.markDirty();
  }

  addCustomSection(): void {
    this.customSectionsList.update(l => [...l, { heading: '', items: [{ text: '' }] }]);
    this.markDirty();
  }

  /** Whole-list replace on save, so removal is local only: no per-entry delete call. */
  removeCustomSection(i: number): void {
    this.customSectionsList.update(l => l.filter((_, j) => j !== i));
    this.markDirty();
  }

  removeExperience(i: number): void {
    this.removeEntry(this.experienceList, i, id => this.profileApi.deleteExperience(id));
  }
  removeEducation(i: number): void {
    this.removeEntry(this.educationList, i, id => this.profileApi.deleteEducation(id));
  }
  removeProject(i: number): void { this.removeEntry(this.projectsList, i, id => this.profileApi.deleteProject(id)); }
  removeLanguage(i: number): void { this.removeEntry(this.languagesList, i, id => this.profileApi.deleteLanguage(id)); }
  removeSocial(i: number): void { this.removeEntry(this.socialsList, i, id => this.socialApi.deleteSocial(id)); }
  removeStrength(i: number): void {
    this.removeEntry(this.strengthsList, i, id => this.strengthApi.deleteStrength(id));
  }
  removeCertification(i: number): void {
    this.removeEntry(this.certificationsList, i, id => this.profileApi.deleteCertification(id));
  }

  private removeEntry<T extends { id?: string }>(
    list: WritableSignal<T[]>, index: number, deleteFn: (id: string) => Observable<void>,
  ): void {
    const entry = list()[index];
    list.update(l => l.filter((_, i) => i !== index));
    if (entry?.id) {
      deleteFn(entry.id).subscribe({
        error: () => {
          list.update(l => [...l.slice(0, index), entry, ...l.slice(index)]);
          this.notify.error('masterCv.toast.deleteFailed');
        }
      });
    }
  }

  /** Certifications persist immediately; the child form resets once the API call succeeds. */
  saveCertification(cert: Certification): void {
    this.profileApi.addCertification(cert).subscribe({
      next: saved => {
        this.certificationsList.update(l => [...l, saved]);
        this.certSection?.reset();
      },
      error: () => this.notify.error('masterCv.toast.certFailed')
    });
  }

  // ── Save everything ───────────────────────────────────────────

  saveAll(): void {
    if (this.saving()) return;
    this.saving.set(true);

    const profile = this.profile();
    const privateInfo = this.privateInfo();
    const ops: Observable<unknown>[] = [
      this.http.patch('/api/v1/users/me/profile', {
        headline: profile.headline,
        summary: profile.summary,
        yearsExperience: profile.yearsExperience,
      }),
      this.privateApi.updatePrivateInfo({
        fullName: privateInfo.fullName,
        phone: privateInfo.phone,
        location: privateInfo.location,
        contactEmail: privateInfo.contactEmail,
      }),
      ...this.upsertOps(this.experienceList(), e => !!(e.companyName?.trim() && e.title?.trim() && e.startDate?.trim()),
        e => this.profileApi.addExperience(e), (id, e) => this.profileApi.updateExperience(id, e)),
      ...this.upsertOps(this.educationList(), e => !!e.institution?.trim(),
        e => this.profileApi.addEducation(e), (id, e) => this.profileApi.updateEducation(id, e)),
      ...this.upsertOps(this.projectsList(), p => !!p.name?.trim(),
        p => this.profileApi.addProject(p), (id, p) => this.profileApi.updateProject(id, p)),
      ...this.upsertOps(this.languagesList(), l => !!l.language?.trim(),
        l => this.profileApi.addLanguage(l), (id, l) => this.profileApi.updateLanguage(id, l)),
      ...this.upsertOps(this.socialsList(), s => !!(s.platform?.trim() && s.url?.trim()),
        s => this.socialApi.createSocial(s), (id, s) => this.socialApi.updateSocial(id, s)),
      ...this.upsertOps(this.strengthsList(), s => !!s.title?.trim(),
        s => this.strengthApi.createStrength(s), (id, s) => this.strengthApi.updateStrength(id, s)),
      // Custom sections are a whole-list replace (server assigns ids, drops blanks).
      this.profileApi.saveCustomSections(this.customSectionsList().filter(s => s.heading?.trim())),
    ];

    forkJoin(ops.length ? ops : [of(null)]).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.notify.success('masterCv.toast.saved');
        // Refresh lists so newly created entries get their server ids
        this.profileApi.getFullProfile().subscribe(full => this.applyFullProfile(full));
      },
      error: () => {
        this.saving.set(false);
        this.notify.error('masterCv.toast.saveFailed');
      }
    });
  }

  /** For each valid entry: PUT when it has an id, POST when it doesn't. Invalid entries stay local. */
  private upsertOps<T extends { id?: string }>(
    list: T[],
    isValid: (entry: T) => boolean,
    create: (entry: T) => Observable<T>,
    update: (id: string, entry: T) => Observable<T>,
  ): Observable<unknown>[] {
    return list.filter(isValid).map(entry => entry.id ? update(entry.id, entry) : create(entry));
  }
}

function dataUrlToBlob(dataUrl: string): Blob {
  const [meta, base64] = dataUrl.split(',');
  const mime = meta.match(/data:(.*?);/)?.[1] ?? 'image/jpeg';
  const bytes = atob(base64);
  const arr = new Uint8Array(bytes.length);
  for (let i = 0; i < bytes.length; i++) arr[i] = bytes.charCodeAt(i);
  return new Blob([arr], { type: mime });
}
