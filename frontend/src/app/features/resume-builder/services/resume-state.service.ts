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
    const experience = (data.experience ?? []).map((e, i) => ({
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
      resumeData: this._resumeData() as any,
      settings: this._settings() as any,
    }).subscribe(draft => {
      this._draftId.set(draft.id ?? null);
      this._isDirty.set(false);
    });
  }

  // ── Personal Info ─────────────────────────────────────────────────────────
  updatePersonalInfo(patch: Partial<PersonalInfo>): void {
    this._resumeData.update(d => ({ ...d, personalInfo: { ...d.personalInfo, ...patch } }));
    this._isDirty.set(true);
  }

  // ── Experience ────────────────────────────────────────────────────────────
  addExperience(item: Omit<ResumeExperience, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      experience: [...d.experience, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateExperience(id: string, patch: Partial<ResumeExperience>): void {
    this._resumeData.update(d => ({
      ...d,
      experience: d.experience.map(e => (e.id === id ? { ...e, ...patch } : e)),
    }));
    this._isDirty.set(true);
  }

  removeExperience(id: string): void {
    this._resumeData.update(d => ({ ...d, experience: d.experience.filter(e => e.id !== id) }));
    this._isDirty.set(true);
  }

  reorderExperience(from: number, to: number): void {
    this._resumeData.update(d => {
      const arr = [...d.experience];
      const [item] = arr.splice(from, 1);
      arr.splice(to, 0, item);
      return { ...d, experience: arr };
    });
    this._isDirty.set(true);
  }

  // ── Education ─────────────────────────────────────────────────────────────
  addEducation(item: Omit<ResumeEducation, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      education: [...d.education, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateEducation(id: string, patch: Partial<ResumeEducation>): void {
    this._resumeData.update(d => ({
      ...d,
      education: d.education.map(e => (e.id === id ? { ...e, ...patch } : e)),
    }));
    this._isDirty.set(true);
  }

  removeEducation(id: string): void {
    this._resumeData.update(d => ({ ...d, education: d.education.filter(e => e.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Projects ──────────────────────────────────────────────────────────────
  addProject(item: Omit<ResumeProject, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      projects: [...d.projects, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateProject(id: string, patch: Partial<ResumeProject>): void {
    this._resumeData.update(d => ({
      ...d,
      projects: d.projects.map(p => (p.id === id ? { ...p, ...patch } : p)),
    }));
    this._isDirty.set(true);
  }

  removeProject(id: string): void {
    this._resumeData.update(d => ({ ...d, projects: d.projects.filter(p => p.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Skills ────────────────────────────────────────────────────────────────
  addSkill(item: Omit<ResumeSkill, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      skills: [...d.skills, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateSkill(id: string, patch: Partial<ResumeSkill>): void {
    this._resumeData.update(d => ({
      ...d,
      skills: d.skills.map(s => (s.id === id ? { ...s, ...patch } : s)),
    }));
    this._isDirty.set(true);
  }

  removeSkill(id: string): void {
    this._resumeData.update(d => ({ ...d, skills: d.skills.filter(s => s.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Languages ─────────────────────────────────────────────────────────────
  addLanguage(item: Omit<ResumeLanguage, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      languages: [...d.languages, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateLanguage(id: string, patch: Partial<ResumeLanguage>): void {
    this._resumeData.update(d => ({
      ...d,
      languages: d.languages.map(l => (l.id === id ? { ...l, ...patch } : l)),
    }));
    this._isDirty.set(true);
  }

  removeLanguage(id: string): void {
    this._resumeData.update(d => ({ ...d, languages: d.languages.filter(l => l.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Certifications ────────────────────────────────────────────────────────
  addCertification(item: Omit<ResumeCertification, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      certifications: [...d.certifications, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateCertification(id: string, patch: Partial<ResumeCertification>): void {
    this._resumeData.update(d => ({
      ...d,
      certifications: d.certifications.map(c => (c.id === id ? { ...c, ...patch } : c)),
    }));
    this._isDirty.set(true);
  }

  removeCertification(id: string): void {
    this._resumeData.update(d => ({
      ...d,
      certifications: d.certifications.filter(c => c.id !== id),
    }));
    this._isDirty.set(true);
  }

  // ── Strengths ─────────────────────────────────────────────────────────────
  addStrength(item: Omit<ResumeStrength, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      strengths: [...d.strengths, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateStrength(id: string, patch: Partial<ResumeStrength>): void {
    this._resumeData.update(d => ({
      ...d,
      strengths: d.strengths.map(s => (s.id === id ? { ...s, ...patch } : s)),
    }));
    this._isDirty.set(true);
  }

  removeStrength(id: string): void {
    this._resumeData.update(d => ({ ...d, strengths: d.strengths.filter(s => s.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Socials ───────────────────────────────────────────────────────────────
  addSocial(item: Omit<ResumeSocial, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      socials: [...d.socials, { ...item, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateSocial(id: string, patch: Partial<ResumeSocial>): void {
    this._resumeData.update(d => ({
      ...d,
      socials: d.socials.map(s => (s.id === id ? { ...s, ...patch } : s)),
    }));
    this._isDirty.set(true);
  }

  removeSocial(id: string): void {
    this._resumeData.update(d => ({ ...d, socials: d.socials.filter(s => s.id !== id) }));
    this._isDirty.set(true);
  }

  // ── Custom Sections ────────────────────────────────────────────────────────
  addCustomSection(section: Omit<ResumeCustomSection, 'id'>): void {
    this._resumeData.update(d => ({
      ...d,
      customSections: [...(d.customSections ?? []), { ...section, id: crypto.randomUUID() }],
    }));
    this._isDirty.set(true);
  }

  updateCustomSection(id: string, patch: Partial<ResumeCustomSection>): void {
    this._resumeData.update(d => ({
      ...d,
      customSections: (d.customSections ?? []).map(s => (s.id === id ? { ...s, ...patch } : s)),
    }));
    this._isDirty.set(true);
  }

  removeCustomSection(id: string): void {
    this._resumeData.update(d => ({
      ...d,
      customSections: (d.customSections ?? []).filter(s => s.id !== id),
    }));
    this._isDirty.set(true);
  }

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

  publishDraft(): Observable<any> {
    const id = this._draftId();
    if (!id) throw new Error('No draft to publish');
    return this.draftApi.publishDraft(id);
  }
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
