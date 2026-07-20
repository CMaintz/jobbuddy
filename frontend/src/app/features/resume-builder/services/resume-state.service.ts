import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, Subject, debounceTime, switchMap, tap } from 'rxjs';
import {
  INITIAL_RESUME_DATA,
  INITIAL_SETTINGS,
  PersonalInfo,
  ResumeData,
  ResumeEducation,
  ResumeExperience,
  ResumeLanguage,
  ResumeCertification,
  ResumeProject,
  ResumeSettings,
  ResumeSkill,
  ResumeStrength,
  ResumeSocial,
  ResumeCustomSection,
  SectionConfig,
} from '../models/resume-builder.models';
import { ResumeDraftApiService } from './resume-draft-api.service';
import { ResumeDraft } from '../models/resume-builder.models';
import { Profile, ProfilePrivateInfo } from '../../../core/models/user.model';
import { ProfileSocial, ProfileStrength, WorkExperience, Education, Project, Certification, SpokenLanguage } from '../../../core/models/profile-section.model';

export interface FullProfileData {
  authEmail?: string;
  profile?: Profile | null;
  privateInfo?: ProfilePrivateInfo;
  socials?: ProfileSocial[];
  strengths?: ProfileStrength[];
  experience?: WorkExperience[];
  education?: Education[];
  projects?: Project[];
  certifications?: Certification[];
  languages?: SpokenLanguage[];
}

@Injectable({ providedIn: 'root' })
export class ResumeStateService {
  private draftApi = inject(ResumeDraftApiService);

  private _resumeData = signal<ResumeData>({ ...INITIAL_RESUME_DATA });
  private _settings = signal<ResumeSettings>({ ...INITIAL_SETTINGS });
  private _draftId = signal<string | null>(null);
  private _draftJobId = signal<string | null>(null);
  private _isDirty = signal(false);
  private _isSaving = signal(false);

  readonly resumeData = this._resumeData.asReadonly();
  readonly settings = this._settings.asReadonly();
  readonly draftId = this._draftId.asReadonly();
  /** Job this draft targets (tailored CVs created via Apply), if any. */
  readonly draftJobId = this._draftJobId.asReadonly();
  /** Target job's description — set by the builder so AI refinements are job-aware. */
  readonly jobDescription = signal<string | null>(null);
  readonly isDirty = this._isDirty.asReadonly();
  readonly isSaving = this._isSaving.asReadonly();

  readonly personalInfo = computed(() => this._resumeData().personalInfo);

  /** What the preview/PDF renders: masked when the anonymise setting is on. */
  readonly displayPersonalInfo = computed(() => {
    const pi = this._resumeData().personalInfo;
    if (!this._settings().anonymise) return pi;
    return {
      ...pi,
      fullName: initialsOf(pi.fullName) || 'Candidate',
      email: '',
      phone: '',
      website: '',
      linkedin: '',
      github: '',
      twitter: '',
      photoUrl: undefined,
    };
  });

  /** Social links are identity — hidden entirely on anonymised output. */
  readonly displaySocials = computed(() =>
    this._settings().anonymise ? [] : this._resumeData().socials);

  readonly experience = computed(() => this._resumeData().experience);
  readonly education = computed(() => this._resumeData().education);
  readonly projects = computed(() => this._resumeData().projects);
  readonly skills = computed(() => this._resumeData().skills);
  readonly languages = computed(() => this._resumeData().languages);
  readonly certifications = computed(() => this._resumeData().certifications);
  readonly strengths = computed(() => this._resumeData().strengths);
  readonly socials = computed(() => this._resumeData().socials);
  readonly customSections = computed(() => this._resumeData().customSections ?? []);

  private saveSubject = new Subject<void>();

  constructor() {
    // Auto-save debounced — only when dirty and has a draft id
    this.saveSubject.pipe(
      debounceTime(1500),
      switchMap(() => {
        const id = this._draftId();
        if (!id || !this._isDirty()) return [];
        this._isSaving.set(true);
        return this.draftApi.saveDraft(id, {
          resumeData: this._resumeData(),
          settings: this._settings(),
        }).pipe(
          tap(() => {
            this._isDirty.set(false);
            this._isSaving.set(false);
          })
        );
      })
    ).subscribe();

    effect(() => {
      // Track changes and trigger save
      const _data = this._resumeData();
      const _settings = this._settings();
      if (this._isDirty()) {
        this.saveSubject.next();
      }
    });
  }

  loadDraft(draftId: string): Observable<void> {
    return new Observable(observer => {
      this.draftApi.getDraft(draftId).subscribe({
        next: draft => {
          if (draft.resumeData && Object.keys(draft.resumeData).length > 0) {
            this._resumeData.set(draft.resumeData as ResumeData);
          }
          if (draft.settings && Object.keys(draft.settings).length > 0) {
            this._settings.set(draft.settings as ResumeSettings);
          }
          this._draftId.set(draft.id ?? null);
          this._draftJobId.set(draft.jobId ?? null);
          this._isDirty.set(false);
          observer.next();
          observer.complete();
        },
        error: err => observer.error(err),
      });
    });
  }

  prefillFromProfile(data: FullProfileData): void {
    const pi = data.privateInfo ?? {};
    const experience = (data.experience ?? []).map((e) => ({
      id: e.id ?? crypto.randomUUID(),
      title: e.title ?? '',
      company: e.companyName ?? '',
      location: e.location ?? '',
      startDate: e.startDate ?? '',
      endDate: e.endDate ?? '',
      current: e.isCurrent ?? false,
      description: e.description ?? '',
      skills: (e.skills ?? []).map(s => s.name ?? ''),
    }));
    const education = (data.education ?? []).map(e => ({
      id: e.id ?? crypto.randomUUID(),
      degree: e.degree ?? '',
      school: e.institution ?? '',
      location: '',
      startDate: e.startDate ?? '',
      endDate: e.endDate ?? '',
      current: false,
      skills: (e.skills ?? []).map(s => s.name ?? ''),
    }));
    const projects = (data.projects ?? []).map(p => ({
      id: p.id ?? crypto.randomUUID(),
      name: p.name ?? '',
      link: p.liveUrl ?? p.githubUrl ?? '',
      date: p.startDate ?? '',
      description: p.description ?? '',
      skills: (p.skills ?? []).map(s => s.name ?? ''),
    }));
    const certifications = (data.certifications ?? []).map(c => ({
      id: c.id ?? crypto.randomUUID(),
      name: c.name ?? '',
      issuer: c.issuer ?? '',
      date: c.issuedAt ?? '',
      link: c.credentialUrl,
    }));
    const languages = (data.languages ?? []).map(l => ({
      id: l.id ?? crypto.randomUUID(),
      name: l.language ?? '',
      proficiency: mapProficiency(l.proficiency),
    }));
    const strengths = (data.strengths ?? []).map(s => ({
      id: s.id ?? crypto.randomUUID(),
      title: s.title ?? '',
      description: s.description ?? '',
      iconKey: s.iconKey ?? 'star',
    }));
    const socials = (data.socials ?? []).map(s => ({
      id: s.id ?? crypto.randomUUID(),
      platform: s.platform ?? '',
      url: s.url ?? '',
      username: s.username,
      iconKey: s.iconKey ?? 'globe',
    }));

    this._resumeData.set({
      personalInfo: {
        fullName: pi.fullName ?? '',
        title: data.profile?.headline ?? '',
        email: pi.contactEmail ?? data.authEmail ?? '',
        phone: pi.phone ?? '',
        location: pi.location ?? '',
        website: '',
        linkedin: '',
        github: '',
        twitter: '',
        summary: data.profile?.summary ?? '',
        photoUrl: pi.photoUrl,
      },
      experience,
      education,
      projects,
      skills: [],
      languages,
      certifications,
      strengths,
      socials,
    });
    this._isDirty.set(true);

    // Create a new draft on the server
    this.draftApi.createDraft({
      name: 'My Resume',
      resumeData: this._resumeData(),
      settings: this._settings(),
    }).subscribe(draft => {
      this._draftId.set(draft.id ?? null);
      this._isDirty.set(false);
    });
  }

  /** Wholesale content swap (AI re-tailor) — layout/settings stay untouched. */
  replaceResumeData(data: ResumeData): void {
    this._resumeData.set(data);
    this._isDirty.set(true);
  }

  // ── Personal Info ─────────────────────────────────────────────────────────
  updatePersonalInfo(patch: Partial<PersonalInfo>): void {
    this._resumeData.update(d => ({ ...d, personalInfo: { ...d.personalInfo, ...patch } }));
    this._isDirty.set(true);
  }

  // ── Generic list CRUD ─────────────────────────────────────────────────────
  // Every resume section is a list of { id, ... } items with identical
  // add/update/remove semantics; the public per-section methods below are
  // thin aliases kept for readable call sites in the form components.

  private addTo<K extends ResumeListKey>(key: K, item: Omit<ResumeListItem<K>, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      [key]: [...(d[key] ?? []), { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  private updateIn<K extends ResumeListKey>(key: K, id: string, patch: Partial<ResumeListItem<K>>): void {
    this._resumeData.update(d => ({
      ...d,
      [key]: (d[key] ?? []).map(item => (item.id === id ? { ...item, ...patch } : item)),
    }));
    this._isDirty.set(true);
  }

  private removeFrom(key: ResumeListKey, id: string): void {
    this._resumeData.update(d => ({
      ...d,
      [key]: ((d[key] ?? []) as { id: string }[]).filter(item => item.id !== id),
    }));
    this._isDirty.set(true);
  }

  addExperience(item: Omit<ResumeExperience, 'id'>): void { this.addTo('experience', item); }
  updateExperience(id: string, patch: Partial<ResumeExperience>): void { this.updateIn('experience', id, patch); }
  removeExperience(id: string): void { this.removeFrom('experience', id); }

  reorderExperience(from: number, to: number): void {
    this._resumeData.update(d => {
      const arr = [...d.experience];
      const [item] = arr.splice(from, 1);
      arr.splice(to, 0, item);
      return { ...d, experience: arr };
    });
    this._isDirty.set(true);
  }

  addEducation(item: Omit<ResumeEducation, 'id'>): void { this.addTo('education', item); }
  updateEducation(id: string, patch: Partial<ResumeEducation>): void { this.updateIn('education', id, patch); }
  removeEducation(id: string): void { this.removeFrom('education', id); }

  addProject(item: Omit<ResumeProject, 'id'>): void { this.addTo('projects', item); }
  updateProject(id: string, patch: Partial<ResumeProject>): void { this.updateIn('projects', id, patch); }
  removeProject(id: string): void { this.removeFrom('projects', id); }

  addSkill(item: Omit<ResumeSkill, 'id'>): void { this.addTo('skills', item); }
  updateSkill(id: string, patch: Partial<ResumeSkill>): void { this.updateIn('skills', id, patch); }
  removeSkill(id: string): void { this.removeFrom('skills', id); }

  addLanguage(item: Omit<ResumeLanguage, 'id'>): void { this.addTo('languages', item); }
  updateLanguage(id: string, patch: Partial<ResumeLanguage>): void { this.updateIn('languages', id, patch); }
  removeLanguage(id: string): void { this.removeFrom('languages', id); }

  addCertification(item: Omit<ResumeCertification, 'id'>): void { this.addTo('certifications', item); }
  updateCertification(id: string, patch: Partial<ResumeCertification>): void { this.updateIn('certifications', id, patch); }
  removeCertification(id: string): void { this.removeFrom('certifications', id); }

  addStrength(item: Omit<ResumeStrength, 'id'>): void { this.addTo('strengths', item); }
  updateStrength(id: string, patch: Partial<ResumeStrength>): void { this.updateIn('strengths', id, patch); }
  removeStrength(id: string): void { this.removeFrom('strengths', id); }

  addSocial(item: Omit<ResumeSocial, 'id'>): void { this.addTo('socials', item); }
  updateSocial(id: string, patch: Partial<ResumeSocial>): void { this.updateIn('socials', id, patch); }
  removeSocial(id: string): void { this.removeFrom('socials', id); }

  addCustomSection(section: Omit<ResumeCustomSection, 'id'>): void { this.addTo('customSections', section); }
  updateCustomSection(id: string, patch: Partial<ResumeCustomSection>): void { this.updateIn('customSections', id, patch); }
  removeCustomSection(id: string): void { this.removeFrom('customSections', id); }

  // ── Settings ──────────────────────────────────────────────────────────────
  updateSettings(patch: Partial<ResumeSettings>): void {
    this._settings.update(s => ({ ...s, ...patch }));
    this._isDirty.set(true);
  }

  updateLayoutColumn(column: 'leftColumn' | 'rightColumn', sections: SectionConfig[]): void {
    this._settings.update(s => ({ ...s, [column]: sections }));
    this._isDirty.set(true);
  }

  resetToEmpty(): void {
    this._resumeData.set({ ...INITIAL_RESUME_DATA });
    this._settings.set({ ...INITIAL_SETTINGS });
    this._isDirty.set(false);
  }

  publishDraft(): Observable<ResumeDraft> {
    const id = this._draftId();
    if (!id) throw new Error('No draft to publish');
    return this.draftApi.publishDraft(id);
  }
}

/** Keys of ResumeData that hold { id, ... } item lists. */
type ResumeListKey = {
  [K in keyof ResumeData]-?: NonNullable<ResumeData[K]> extends { id: string }[] ? K : never;
}[keyof ResumeData];

type ResumeListItem<K extends ResumeListKey> = NonNullable<ResumeData[K]>[number];

/** "Ada Lovelace" → "A. L." */
function initialsOf(name: string): string {
  return (name ?? '').trim().split(/\s+/).filter(Boolean)
    .map(part => part[0].toUpperCase() + '.').join(' ');
}

function mapProficiency(p: string): 'Native' | 'Fluent' | 'Proficient' | 'Intermediate' | 'Basic' {
  const map: Record<string, 'Native' | 'Fluent' | 'Proficient' | 'Intermediate' | 'Basic'> = {
    NATIVE: 'Native',
    FLUENT: 'Fluent',
    PROFESSIONAL: 'Proficient',
    CONVERSATIONAL: 'Intermediate',
    ELEMENTARY: 'Basic',
  };
  return map[p?.toUpperCase()] ?? 'Intermediate';
}
