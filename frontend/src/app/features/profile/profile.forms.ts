import { FormBuilder, Validators } from '@angular/forms';

export function createProfileForm(fb: FormBuilder) {
  return fb.group({
    fullName: [''],
    headline: [''],
    summary: [''],
    location: [''],
    yearsExperience: [null as number | null],
    linkedinUrl: [''],
    githubUrl: [''],
    websiteUrl: [''],
    phone: [''],
    technologiesRaw: [''],
    skillsRaw: [''],
  });
}

export function createExperienceForm(fb: FormBuilder) {
  return fb.group({
    companyName: ['', Validators.required],
    title: ['', Validators.required],
    startDate: ['', Validators.required],
    endDate: [''],
    isCurrent: [false],
    description: [''],
    location: [''],
    technologiesRaw: [''],
  });
}

export function createProjectForm(fb: FormBuilder) {
  return fb.group({
    name: ['', Validators.required],
    description: [''],
    githubUrl: [''],
    liveUrl: [''],
    technologiesRaw: [''],
    measurableOutcomes: [''],
    isFeatured: [false],
  });
}

export function createEducationForm(fb: FormBuilder) {
  return fb.group({
    institution: ['', Validators.required],
    degree: [''],
    fieldOfStudy: [''],
    startDate: [''],
    endDate: [''],
  });
}

export function createCertificationForm(fb: FormBuilder) {
  return fb.group({
    name: ['', Validators.required],
    issuer: [''],
    issuedAt: [''],
    credentialUrl: [''],
  });
}
