import { HttpClient } from '@angular/common/http';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { SkillsApiService } from '../../core/api/skills.api';
import { Certification, Education, LanguageProficiency, ProfileLanguage, Project, WorkExperience } from '../../core/models/profile-section.model';
import { resetFlagAfter, runAction } from '../../shared/utils/async-ui';
import { splitCsv } from './profile.constants';
import type { ProfileComponent } from './profile.component';

export function loadProfile(vm: ProfileComponent, http: HttpClient): void {
  http.get<any>('/api/v1/users/me/profile').subscribe({
    next: profile => {
      vm.profileForm.patchValue({
        ...profile,
        technologiesRaw: (profile.technologies || []).join(', '),
        skillsRaw: (profile.skills || []).join(', '),
      });
      if (profile.photoUrl) vm.photoUrl = profile.photoUrl;
    },
    error: () => {}
  });
}

export function loadSections(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService, skillsApi: SkillsApiService): void {
  sectionsApi.getExperience().subscribe(d => vm.experiences = d);
  sectionsApi.getProjects().subscribe(d => vm.projects = d);
  sectionsApi.getEducation().subscribe(d => vm.educations = d);
  sectionsApi.getCertifications().subscribe(d => vm.certifications = d);
  skillsApi.getProfileSkills().subscribe(d => vm.profileSkills = d);
  sectionsApi.getLanguages().subscribe(d => vm.spokenLanguages = d);
}

export function uploadPhoto(vm: ProfileComponent, http: HttpClient, file: File): void {
  vm.photoError = '';
  const formData = new FormData();
  formData.append('file', file);
  runAction({
    action$: http.post<{ url: string }>('/api/v1/users/me/profile/photo', formData),
    setLoading: value => vm.uploadingPhoto = value,
    setError: message => vm.photoError = message,
    errorMessage: 'Failed to upload photo. Please try again.',
    next: res => vm.photoUrl = res.url,
  });
}

export function importLinkedInPdf(vm: ProfileComponent, http: HttpClient, file: File): void {
  vm.linkedInImportError = '';
  const formData = new FormData();
  formData.append('file', file);
  runAction({
    action$: http.post<any>('/api/v1/users/me/import/linkedin-pdf', formData),
    setLoading: value => vm.importingLinkedIn = value,
    setError: message => vm.linkedInImportError = message,
    errorMessage: 'Failed to parse PDF. Please try again.',
    next: result => vm.linkedInPreview = result,
  });
}

export function importCvPdf(vm: ProfileComponent, http: HttpClient, file: File): void {
  vm.cvPdfImportError = '';
  const formData = new FormData();
  formData.append('file', file);
  runAction({
    action$: http.post<any>('/api/v1/profile/import/cv-pdf', formData),
    setLoading: value => vm.importingCvPdf = value,
    setError: message => vm.cvPdfImportError = message,
    errorMessage: 'Failed to parse PDF. Please try again.',
    next: result => vm.cvPdfPreview = result,
  });
}

export function applyCvPdfPreview(vm: ProfileComponent): void {
  if (!vm.cvPdfPreview) return;
  const p = vm.cvPdfPreview;
  vm.profileForm.patchValue({
    fullName: p.fullName || vm.profileForm.value.fullName,
    headline: p.headline || vm.profileForm.value.headline,
    summary: p.summary || vm.profileForm.value.summary,
    location: p.location || vm.profileForm.value.location,
  });
  vm.cvPdfPreview = null;
  vm.saveProfile();
}

export function applyLinkedInPreview(vm: ProfileComponent): void {
  if (!vm.linkedInPreview) return;
  const p = vm.linkedInPreview;
  vm.profileForm.patchValue({
    fullName: p.fullName || vm.profileForm.value.fullName,
    headline: p.headline || vm.profileForm.value.headline,
    summary: p.summary || vm.profileForm.value.summary,
    location: p.location || vm.profileForm.value.location,
    skillsRaw: p.skills?.join(', ') || vm.profileForm.value.skillsRaw,
  });
  vm.linkedInPreview = null;
  vm.saveProfile();
}

export function saveProfile(vm: ProfileComponent, http: HttpClient): void {
  const v = vm.profileForm.value;
  http.put('/api/v1/users/me/profile', {
    ...v,
    technologies: splitCsv(v.technologiesRaw),
    skills: splitCsv(v.skillsRaw),
  }).subscribe(() => {
    vm.saveSuccess = true;
    resetFlagAfter(value => vm.saveSuccess = value);
  });
}

export function saveExperience(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService): void {
  if (vm.expForm.invalid) return;
  const v = vm.expForm.value;
  const payload: WorkExperience = {
    companyName: v.companyName!,
    title: v.title!,
    startDate: v.startDate!,
    endDate: v.isCurrent ? undefined : (v.endDate || undefined),
    isCurrent: v.isCurrent ?? false,
    description: v.description || undefined,
    location: v.location || undefined,
    technologies: splitCsv(v.technologiesRaw),
    skills: [...vm.expEditSkills],
  };
  sectionsApi.addExperience(payload).subscribe(() => {
    sectionsApi.getExperience().subscribe(d => vm.experiences = d);
    vm.expForm.reset({ isCurrent: false });
    vm.expEditSkills = [];
    vm.showExpForm = false;
  });
}

export function saveProject(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService): void {
  if (vm.projectForm.invalid) return;
  const v = vm.projectForm.value;
  const payload: Project = {
    name: v.name!,
    description: v.description || undefined,
    githubUrl: v.githubUrl || undefined,
    liveUrl: v.liveUrl || undefined,
    measurableOutcomes: v.measurableOutcomes || undefined,
    isFeatured: v.isFeatured ?? false,
    technologies: splitCsv(v.technologiesRaw),
    skills: [...vm.projectEditSkills],
  };
  sectionsApi.addProject(payload).subscribe(() => {
    sectionsApi.getProjects().subscribe(d => vm.projects = d);
    vm.projectForm.reset({ isFeatured: false });
    vm.projectEditSkills = [];
    vm.showProjectForm = false;
  });
}

export function saveEducation(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService): void {
  if (vm.eduForm.invalid) return;
  const v = vm.eduForm.value;
  const payload: Education = {
    institution: v.institution!,
    degree: v.degree || undefined,
    fieldOfStudy: v.fieldOfStudy || undefined,
    startDate: v.startDate || undefined,
    endDate: v.endDate || undefined,
    skills: [...vm.educationEditSkills],
  };
  sectionsApi.addEducation(payload).subscribe(() => {
    sectionsApi.getEducation().subscribe(d => vm.educations = d);
    vm.eduForm.reset();
    vm.educationEditSkills = [];
    vm.showEduForm = false;
  });
}

export function saveCertification(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService): void {
  if (vm.certForm.invalid) return;
  const v = vm.certForm.value;
  const payload: Certification = {
    name: v.name!,
    issuer: v.issuer || undefined,
    issuedAt: v.issuedAt || undefined,
    credentialUrl: v.credentialUrl || undefined,
  };
  sectionsApi.addCertification(payload).subscribe(() => {
    sectionsApi.getCertifications().subscribe(d => vm.certifications = d);
    vm.certForm.reset();
    vm.showCertForm = false;
  });
}

export function saveLanguage(vm: ProfileComponent, sectionsApi: ProfileSectionsApiService): void {
  if (!vm.newLang.language?.trim()) return;
  const payload: ProfileLanguage = {
    language: vm.newLang.language.trim(),
    proficiency: vm.newLang.proficiency as LanguageProficiency ?? 'FLUENT',
    displayOrder: vm.spokenLanguages.length,
  };
  sectionsApi.addLanguage(payload).subscribe(() => {
    sectionsApi.getLanguages().subscribe(d => vm.spokenLanguages = d);
    vm.newLang = { language: '', proficiency: 'FLUENT', displayOrder: 0 };
    vm.showLangForm = false;
  });
}
