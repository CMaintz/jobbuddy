import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { PhotoCropDialogComponent } from '../resume-builder/shared/photo-crop-dialog.component';
import { AiRefineMenuComponent } from '../resume-builder/shared/ai-refine-menu.component';
import { StructuredDocumentRendererComponent } from '../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { JbDropdownComponent } from '../../shared/components/jb-dropdown/jb-dropdown.component';
import { PdfExportService } from '../resume-builder/services/pdf-export.service';
import { AiApiService } from '../../core/api/ai.api';
import { StructuredDocument } from '../../core/models/structured-document.model';
import { ProfileSectionsApiService, FullProfileResponse } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { ProfileStrengthApiService } from '../../core/api/profile-strength.api';
import { ProfileSocialApiService } from '../../core/api/profile-social.api';
import { Profile, ProfilePrivateInfo } from '../../core/models/user.model';
import {
  WorkExperience, Education, Project, Certification,
  SpokenLanguage, LanguageProficiency, ProfileSocial, ProfileStrength
} from '../../core/models/profile-section.model';

interface CvSection {
  key: string;
  label: string;
  count: string;
  empty?: boolean;
}

const SOCIAL_PLATFORMS = ['GitHub', 'LinkedIn', 'Website', 'X', 'Mastodon', 'Other'];
const PROFICIENCIES: LanguageProficiency[] = ['NATIVE', 'FLUENT', 'PROFESSIONAL', 'CONVERSATIONAL', 'ELEMENTARY'];

@Component({
  selector: 'app-master-cv-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent, JbToastComponent, TagInputComponent, PhotoCropDialogComponent, AiRefineMenuComponent, StructuredDocumentRendererComponent, JbDropdownComponent],
  templateUrl: './master-cv-builder.component.html'
})
export class MasterCvBuilderComponent implements OnInit {
  @ViewChild('exportEl') exportEl!: ElementRef<HTMLElement>;

  private http = inject(HttpClient);
  private profileApi = inject(ProfileSectionsApiService);
  private privateApi = inject(ProfilePrivateApiService);
  private strengthApi = inject(ProfileStrengthApiService);
  private socialApi = inject(ProfileSocialApiService);
  private aiApi = inject(AiApiService);
  private pdfExport = inject(PdfExportService);

  loading = true;
  saving = signal(false);
  dirty = signal(false);
  toast = signal('');
  activeSec = signal('Header');

  profile: Profile = {};
  privateInfo: ProfilePrivateInfo = {};
  experienceList: WorkExperience[] = [];
  educationList: Education[] = [];
  projectsList: Project[] = [];
  certificationsList: Certification[] = [];
  languagesList: SpokenLanguage[] = [];
  socialsList: ProfileSocial[] = [];
  strengthsList: ProfileStrength[] = [];

  socialPlatforms = SOCIAL_PLATFORMS;
  proficiencies = PROFICIENCIES;

  newCert: Certification = { name: '' };
  showCertForm = false;
  cropSrc = signal<string | null>(null);
  uploadingPhoto = signal(false);

  // PDF export (full or anonymised) via an off-screen structured render
  exportingPdf = signal(false);
  exportDoc = signal<StructuredDocument | null>(null);

  get skillsList(): string[] { return this.profile.skills ?? []; }
  get technologiesList(): string[] { return this.profile.technologies ?? []; }

  get coverage(): number {
    const checks = [
      (this.profile.summary?.length ?? 0) > 60,
      this.strengthsList.length >= 3,
      this.experienceList.length >= 2,
      this.skillsList.length >= 6,
      this.educationList.length >= 1,
      this.languagesList.length >= 1,
      this.experienceList.some(j => (j.description?.length ?? 0) > 50),
      this.projectsList.length >= 1,
    ];
    return Math.round((checks.filter(Boolean).length / 8) * 100);
  }

  sections(): CvSection[] {
    return [
      { key: 'Header', label: 'Header', count: '5 fields' },
      { key: 'Profile', label: 'Profile', count: `${(this.profile.summary ?? '').trim().split(/\s+/).filter(Boolean).length} words` },
      { key: 'Strengths', label: 'Strengths', count: `${this.strengthsList.length}` },
      { key: 'Experience', label: 'Experience', count: `${this.experienceList.length} roles` },
      { key: 'Skills', label: 'Skills', count: `${this.skillsList.length + this.technologiesList.length}` },
      { key: 'Education', label: 'Education', count: `${this.educationList.length}` },
      { key: 'Projects', label: 'Projects', count: `${this.projectsList.length}`, empty: this.projectsList.length === 0 },
      { key: 'Certifications', label: 'Certifications', count: `${this.certificationsList.length}`, empty: this.certificationsList.length === 0 },
      { key: 'Languages', label: 'Languages', count: `${this.languagesList.length}` },
      { key: 'Socials', label: 'Socials', count: `${this.socialsList.length}`, empty: this.socialsList.length === 0 },
    ];
  }

  sectionHint(): string {
    const hints: Record<string, string> = {
      Header: 'Your name and contact details',
      Profile: 'The opening summary',
      Strengths: 'Punchy strengths as pills',
      Experience: 'Roles & achievements',
      Skills: 'Searchable skill & technology tags',
      Education: 'Degrees & schools',
      Projects: 'Side projects & open source',
      Certifications: 'Certificates & credentials',
      Languages: 'Languages & levels',
      Socials: 'GitHub, LinkedIn & other links',
    };
    return hints[this.activeSec()] || '';
  }

  ngOnInit(): void {
    forkJoin({
      full: this.profileApi.getFullProfile(),
      priv: this.privateApi.getPrivateInfo(),
    }).subscribe({
      next: ({ full, priv }) => {
        this.applyFullProfile(full);
        this.privateInfo = priv ?? {};
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.toast.set('Failed to load profile');
      }
    });
  }

  private applyFullProfile(resp: FullProfileResponse): void {
    this.profile = resp.profile ?? {};
    this.experienceList = resp.experience || [];
    this.educationList = resp.education || [];
    this.projectsList = resp.projects || [];
    this.certificationsList = resp.certifications || [];
    this.languagesList = resp.languages || [];
    this.socialsList = resp.socials || [];
    this.strengthsList = resp.strengths || [];
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  // ── PDF export ────────────────────────────────────────────────

  /** Renders the master CV off-screen and downloads it — optionally anonymised. */
  exportPdf(anonymised: boolean): void {
    if (this.exportingPdf()) return;
    this.exportingPdf.set(true);
    this.aiApi.getCvRenderModel().subscribe({
      next: doc => {
        this.exportDoc.set(anonymised ? this.anonymiseDoc(doc) : doc);
        // Let Angular render the off-screen sheet before capturing it
        setTimeout(async () => {
          try {
            const filename = anonymised
              ? 'master-cv-anonymised'
              : `${this.privateInfo.fullName || 'master-cv'} - CV`;
            await this.pdfExport.download(this.exportEl.nativeElement, filename);
          } catch {
            this.toast.set('PDF export failed — try again');
          } finally {
            this.exportDoc.set(null);
            this.exportingPdf.set(false);
          }
        }, 200);
      },
      error: () => {
        this.exportingPdf.set(false);
        this.toast.set('Could not build the CV render — check your master CV content');
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
        name: initials || 'Candidate',
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
    if (this.privateInfo.photoUrl) this.cropSrc.set(this.privateInfo.photoUrl);
  }

  onPhotoCropped(dataUrl: string): void {
    this.cropSrc.set(null);
    this.uploadingPhoto.set(true);
    const formData = new FormData();
    formData.append('file', dataUrlToBlob(dataUrl), 'photo.jpg');
    this.http.post<{ url: string }>('/api/v1/users/me/profile/photo', formData).subscribe({
      next: res => {
        this.privateInfo.photoUrl = res.url;
        this.uploadingPhoto.set(false);
        this.toast.set('Photo updated');
      },
      error: () => {
        this.uploadingPhoto.set(false);
        this.toast.set('Photo upload failed');
      }
    });
  }

  removePhoto(): void {
    this.privateInfo.photoUrl = '';
    this.privateApi.updatePrivateInfo({ photoUrl: '' }).subscribe({
      error: () => this.toast.set('Could not remove the photo')
    });
  }

  onSkillsChange(value: string[]): void {
    this.profile.skills = value;
    this.markDirty();
  }

  onTechnologiesChange(value: string[]): void {
    this.profile.technologies = value;
    this.markDirty();
  }

  // ── Add / remove list entries (persisted on Save) ─────────────

  addExperience(): void {
    this.experienceList.unshift({ companyName: '', title: '', startDate: '', isCurrent: false, description: '' });
    this.markDirty();
  }

  addEducation(): void {
    this.educationList.unshift({ institution: '', degree: '' });
    this.markDirty();
  }

  addProject(): void {
    this.projectsList.unshift({ name: '', description: '' });
    this.markDirty();
  }

  addLanguage(): void {
    this.languagesList.push({ language: '', proficiency: 'FLUENT', displayOrder: this.languagesList.length });
    this.markDirty();
  }

  addSocial(): void {
    this.socialsList.push({ platform: 'GitHub', url: '', iconKey: 'github', displayOrder: this.socialsList.length });
    this.markDirty();
  }

  addStrength(): void {
    this.strengthsList.push({ title: '', description: '', iconKey: 'star', displayOrder: this.strengthsList.length });
    this.markDirty();
  }

  onSocialPlatformChange(social: ProfileSocial): void {
    social.iconKey = social.platform.toLowerCase();
    this.markDirty();
  }

  removeExperience(i: number): void { this.removeEntry(this.experienceList, i, id => this.profileApi.deleteExperience(id)); }
  removeEducation(i: number): void { this.removeEntry(this.educationList, i, id => this.profileApi.deleteEducation(id)); }
  removeProject(i: number): void { this.removeEntry(this.projectsList, i, id => this.profileApi.deleteProject(id)); }
  removeLanguage(i: number): void { this.removeEntry(this.languagesList, i, id => this.profileApi.deleteLanguage(id)); }
  removeSocial(i: number): void { this.removeEntry(this.socialsList, i, id => this.socialApi.deleteSocial(id)); }
  removeStrength(i: number): void { this.removeEntry(this.strengthsList, i, id => this.strengthApi.deleteStrength(id)); }
  removeCertification(i: number): void { this.removeEntry(this.certificationsList, i, id => this.profileApi.deleteCertification(id)); }

  private removeEntry<T extends { id?: string }>(list: T[], index: number, deleteFn: (id: string) => Observable<void>): void {
    const entry = list[index];
    list.splice(index, 1);
    if (entry?.id) {
      deleteFn(entry.id).subscribe({
        error: () => {
          list.splice(index, 0, entry);
          this.toast.set('Delete failed');
        }
      });
    }
  }

  saveCertification(): void {
    if (!this.newCert.name.trim()) return;
    this.profileApi.addCertification(this.newCert).subscribe({
      next: cert => {
        this.certificationsList.push(cert);
        this.newCert = { name: '' };
        this.showCertForm = false;
      },
      error: () => this.toast.set('Failed to add certification')
    });
  }

  // ── Save everything ───────────────────────────────────────────

  saveAll(): void {
    if (this.saving()) return;
    this.saving.set(true);

    const ops: Observable<unknown>[] = [
      this.http.patch('/api/v1/users/me/profile', {
        headline: this.profile.headline,
        summary: this.profile.summary,
        yearsExperience: this.profile.yearsExperience,
        technologies: this.profile.technologies ?? [],
        skills: this.profile.skills ?? [],
      }),
      this.privateApi.updatePrivateInfo({
        fullName: this.privateInfo.fullName,
        phone: this.privateInfo.phone,
        location: this.privateInfo.location,
        contactEmail: this.privateInfo.contactEmail,
      }),
      ...this.upsertOps(this.experienceList, e => !!(e.companyName?.trim() && e.title?.trim() && e.startDate?.trim()),
        e => this.profileApi.addExperience(e), (id, e) => this.profileApi.updateExperience(id, e)),
      ...this.upsertOps(this.educationList, e => !!e.institution?.trim(),
        e => this.profileApi.addEducation(e), (id, e) => this.profileApi.updateEducation(id, e)),
      ...this.upsertOps(this.projectsList, p => !!p.name?.trim(),
        p => this.profileApi.addProject(p), (id, p) => this.profileApi.updateProject(id, p)),
      ...this.upsertOps(this.languagesList, l => !!l.language?.trim(),
        l => this.profileApi.addLanguage(l), (id, l) => this.profileApi.updateLanguage(id, l)),
      ...this.upsertOps(this.socialsList, s => !!(s.platform?.trim() && s.url?.trim()),
        s => this.socialApi.createSocial(s), (id, s) => this.socialApi.updateSocial(id, s)),
      ...this.upsertOps(this.strengthsList, s => !!s.title?.trim(),
        s => this.strengthApi.createStrength(s), (id, s) => this.strengthApi.updateStrength(id, s)),
    ];

    forkJoin(ops.length ? ops : [of(null)]).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.toast.set('Master CV saved');
        // Refresh lists so newly created entries get their server ids
        this.profileApi.getFullProfile().subscribe(full => this.applyFullProfile(full));
      },
      error: () => {
        this.saving.set(false);
        this.toast.set('Save failed — check your entries and try again');
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
