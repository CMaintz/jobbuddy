import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { WorkExperience, Project, Education, Certification } from '../../core/models/profile-section.model';

type Tab = 'overview' | 'experience' | 'projects' | 'education' | 'certifications';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="max-w-4xl mx-auto p-6">
      <h1 class="text-2xl font-bold text-gray-900 mb-6">Master Career Profile</h1>

      <!-- Tabs -->
      <div class="border-b border-gray-200 mb-6">
        <nav class="-mb-px flex space-x-8">
          <button *ngFor="let tab of tabs" (click)="activeTab = tab.key"
                  [class]="activeTab === tab.key
                    ? 'border-b-2 border-blue-600 text-blue-600 pb-2 font-medium text-sm'
                    : 'text-gray-500 hover:text-gray-700 pb-2 text-sm'">
            {{ tab.label }}
          </button>
        </nav>
      </div>

      <!-- Overview Tab -->
      <div *ngIf="activeTab === 'overview'">
        <form [formGroup]="profileForm" (ngSubmit)="saveProfile()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700">Full Name</label>
              <input formControlName="fullName" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">Headline</label>
              <input formControlName="headline" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">Location</label>
              <input formControlName="location" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">Years of Experience</label>
              <input formControlName="yearsExperience" type="number" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">LinkedIn URL</label>
              <input formControlName="linkedinUrl" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700">GitHub URL</label>
              <input formControlName="githubUrl" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Professional Summary</label>
            <textarea formControlName="summary" rows="4" class="mt-1 w-full border rounded px-3 py-2 text-sm"></textarea>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Technologies (comma-separated)</label>
            <input formControlName="technologiesRaw" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Skills (comma-separated)</label>
            <input formControlName="skillsRaw" class="mt-1 w-full border rounded px-3 py-2 text-sm"/>
          </div>
          <button type="submit" class="bg-blue-600 text-white px-4 py-2 rounded text-sm font-medium hover:bg-blue-700">
            Save Profile
          </button>
          <span *ngIf="saveSuccess" class="ml-3 text-green-600 text-sm">Saved!</span>
        </form>
      </div>

      <!-- Work Experience Tab -->
      <div *ngIf="activeTab === 'experience'">
        <div class="flex justify-between items-center mb-4">
          <h2 class="text-lg font-semibold">Work Experience</h2>
          <button (click)="showExpForm = !showExpForm" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">
            + Add Experience
          </button>
        </div>

        <form *ngIf="showExpForm" [formGroup]="expForm" (ngSubmit)="saveExperience()" class="bg-gray-50 rounded p-4 mb-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-700">Company</label>
              <input formControlName="companyName" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Title</label>
              <input formControlName="title" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Start Date</label>
              <input formControlName="startDate" type="date" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">End Date</label>
              <input formControlName="endDate" type="date" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
          </div>
          <label class="flex items-center gap-2 text-sm">
            <input formControlName="isCurrent" type="checkbox"/> Current position
          </label>
          <div>
            <label class="block text-xs font-medium text-gray-700">Description</label>
            <textarea formControlName="description" rows="3" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"></textarea>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700">Technologies (comma-separated)</label>
            <input formControlName="technologiesRaw" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">Save</button>
            <button type="button" (click)="showExpForm = false" class="border px-3 py-1.5 rounded text-sm">Cancel</button>
          </div>
        </form>

        <div *ngFor="let exp of experiences" class="border rounded p-4 mb-3">
          <div class="flex justify-between">
            <div>
              <p class="font-semibold">{{ exp.title }}</p>
              <p class="text-sm text-gray-600">{{ exp.companyName }} · {{ exp.startDate | slice:0:7 }} – {{ exp.isCurrent ? 'Present' : (exp.endDate | slice:0:7) }}</p>
              <p *ngIf="exp.description" class="text-sm text-gray-700 mt-1">{{ exp.description }}</p>
              <div *ngIf="exp.technologies?.length" class="mt-2 flex flex-wrap gap-1">
                <span *ngFor="let t of exp.technologies" class="bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded-full">{{ t }}</span>
              </div>
            </div>
            <button (click)="deleteExperience(exp.id!)" class="text-red-500 text-sm hover:text-red-700">Delete</button>
          </div>
        </div>
        <p *ngIf="!experiences.length" class="text-gray-500 text-sm">No experience added yet.</p>
      </div>

      <!-- Projects Tab -->
      <div *ngIf="activeTab === 'projects'">
        <div class="flex justify-between items-center mb-4">
          <h2 class="text-lg font-semibold">Projects</h2>
          <button (click)="showProjectForm = !showProjectForm" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">
            + Add Project
          </button>
        </div>

        <form *ngIf="showProjectForm" [formGroup]="projectForm" (ngSubmit)="saveProject()" class="bg-gray-50 rounded p-4 mb-4 space-y-3">
          <div>
            <label class="block text-xs font-medium text-gray-700">Project Name</label>
            <input formControlName="name" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700">Description</label>
            <textarea formControlName="description" rows="3" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"></textarea>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-700">GitHub URL</label>
              <input formControlName="githubUrl" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Live URL</label>
              <input formControlName="liveUrl" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700">Technologies (comma-separated)</label>
            <input formControlName="technologiesRaw" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700">Measurable Outcomes</label>
            <input formControlName="measurableOutcomes" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
          </div>
          <label class="flex items-center gap-2 text-sm">
            <input formControlName="isFeatured" type="checkbox"/> Featured project
          </label>
          <div class="flex gap-2">
            <button type="submit" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">Save</button>
            <button type="button" (click)="showProjectForm = false" class="border px-3 py-1.5 rounded text-sm">Cancel</button>
          </div>
        </form>

        <div *ngFor="let p of projects" class="border rounded p-4 mb-3">
          <div class="flex justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <p class="font-semibold">{{ p.name }}</p>
                <span *ngIf="p.isFeatured" class="bg-yellow-100 text-yellow-700 text-xs px-2 py-0.5 rounded-full">Featured</span>
              </div>
              <p *ngIf="p.description" class="text-sm text-gray-700 mt-1">{{ p.description }}</p>
              <div class="flex gap-3 mt-1">
                <a *ngIf="p.githubUrl" [href]="p.githubUrl" target="_blank" class="text-xs text-blue-600 hover:underline">GitHub</a>
                <a *ngIf="p.liveUrl" [href]="p.liveUrl" target="_blank" class="text-xs text-blue-600 hover:underline">Live Demo</a>
              </div>
              <div *ngIf="p.technologies?.length" class="mt-2 flex flex-wrap gap-1">
                <span *ngFor="let t of p.technologies" class="bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded-full">{{ t }}</span>
              </div>
            </div>
            <button (click)="deleteProject(p.id!)" class="text-red-500 text-sm hover:text-red-700">Delete</button>
          </div>
        </div>
        <p *ngIf="!projects.length" class="text-gray-500 text-sm">No projects added yet.</p>
      </div>

      <!-- Education Tab -->
      <div *ngIf="activeTab === 'education'">
        <div class="flex justify-between items-center mb-4">
          <h2 class="text-lg font-semibold">Education</h2>
          <button (click)="showEduForm = !showEduForm" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">
            + Add Education
          </button>
        </div>

        <form *ngIf="showEduForm" [formGroup]="eduForm" (ngSubmit)="saveEducation()" class="bg-gray-50 rounded p-4 mb-4 space-y-3">
          <div>
            <label class="block text-xs font-medium text-gray-700">Institution</label>
            <input formControlName="institution" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-700">Degree</label>
              <input formControlName="degree" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Field of Study</label>
              <input formControlName="fieldOfStudy" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Start Date</label>
              <input formControlName="startDate" type="date" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">End Date</label>
              <input formControlName="endDate" type="date" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">Save</button>
            <button type="button" (click)="showEduForm = false" class="border px-3 py-1.5 rounded text-sm">Cancel</button>
          </div>
        </form>

        <div *ngFor="let e of educations" class="border rounded p-4 mb-3">
          <div class="flex justify-between">
            <div>
              <p class="font-semibold">{{ e.degree }} in {{ e.fieldOfStudy }}</p>
              <p class="text-sm text-gray-600">{{ e.institution }} · {{ e.startDate | slice:0:4 }} – {{ e.endDate | slice:0:4 }}</p>
            </div>
            <button (click)="deleteEducation(e.id!)" class="text-red-500 text-sm hover:text-red-700">Delete</button>
          </div>
        </div>
        <p *ngIf="!educations.length" class="text-gray-500 text-sm">No education added yet.</p>
      </div>

      <!-- Certifications Tab -->
      <div *ngIf="activeTab === 'certifications'">
        <div class="flex justify-between items-center mb-4">
          <h2 class="text-lg font-semibold">Certifications</h2>
          <button (click)="showCertForm = !showCertForm" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">
            + Add Certification
          </button>
        </div>

        <form *ngIf="showCertForm" [formGroup]="certForm" (ngSubmit)="saveCertification()" class="bg-gray-50 rounded p-4 mb-4 space-y-3">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-gray-700">Certification Name</label>
              <input formControlName="name" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Issuer</label>
              <input formControlName="issuer" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Issued Date</label>
              <input formControlName="issuedAt" type="date" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-700">Credential URL</label>
              <input formControlName="credentialUrl" class="mt-1 w-full border rounded px-2 py-1.5 text-sm"/>
            </div>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm">Save</button>
            <button type="button" (click)="showCertForm = false" class="border px-3 py-1.5 rounded text-sm">Cancel</button>
          </div>
        </form>

        <div *ngFor="let c of certifications" class="border rounded p-4 mb-3 flex justify-between">
          <div>
            <p class="font-semibold">{{ c.name }}</p>
            <p class="text-sm text-gray-600">{{ c.issuer }} · {{ c.issuedAt | slice:0:7 }}</p>
            <a *ngIf="c.credentialUrl" [href]="c.credentialUrl" target="_blank" class="text-xs text-blue-600 hover:underline">View Credential</a>
          </div>
          <button (click)="deleteCertification(c.id!)" class="text-red-500 text-sm hover:text-red-700">Delete</button>
        </div>
        <p *ngIf="!certifications.length" class="text-gray-500 text-sm">No certifications added yet.</p>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private sectionsApi = inject(ProfileSectionsApiService);

  activeTab: Tab = 'overview';
  saveSuccess = false;
  showExpForm = false;
  showProjectForm = false;
  showEduForm = false;
  showCertForm = false;

  tabs = [
    { key: 'overview' as Tab, label: 'Overview' },
    { key: 'experience' as Tab, label: 'Experience' },
    { key: 'projects' as Tab, label: 'Projects' },
    { key: 'education' as Tab, label: 'Education' },
    { key: 'certifications' as Tab, label: 'Certifications' },
  ];

  experiences: WorkExperience[] = [];
  projects: Project[] = [];
  educations: Education[] = [];
  certifications: Certification[] = [];

  profileForm = this.fb.group({
    fullName: [''],
    headline: [''],
    summary: [''],
    location: [''],
    yearsExperience: [null as number | null],
    linkedinUrl: [''],
    githubUrl: [''],
    websiteUrl: [''],
    technologiesRaw: [''],
    skillsRaw: [''],
  });

  expForm = this.fb.group({
    companyName: ['', Validators.required],
    title: ['', Validators.required],
    startDate: ['', Validators.required],
    endDate: [''],
    isCurrent: [false],
    description: [''],
    location: [''],
    technologiesRaw: [''],
  });

  projectForm = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    githubUrl: [''],
    liveUrl: [''],
    technologiesRaw: [''],
    measurableOutcomes: [''],
    isFeatured: [false],
  });

  eduForm = this.fb.group({
    institution: ['', Validators.required],
    degree: [''],
    fieldOfStudy: [''],
    startDate: [''],
    endDate: [''],
  });

  certForm = this.fb.group({
    name: ['', Validators.required],
    issuer: [''],
    issuedAt: [''],
    credentialUrl: [''],
  });

  ngOnInit(): void {
    this.http.get<any>('/api/v1/users/me/profile').subscribe({
      next: profile => this.profileForm.patchValue({
        ...profile,
        technologiesRaw: (profile.technologies || []).join(', '),
        skillsRaw: (profile.skills || []).join(', '),
      }),
      error: () => {}
    });
    this.loadSections();
  }

  private loadSections(): void {
    this.sectionsApi.getExperience().subscribe(d => this.experiences = d);
    this.sectionsApi.getProjects().subscribe(d => this.projects = d);
    this.sectionsApi.getEducation().subscribe(d => this.educations = d);
    this.sectionsApi.getCertifications().subscribe(d => this.certifications = d);
  }

  saveProfile(): void {
    const v = this.profileForm.value;
    const payload = {
      ...v,
      technologies: (v.technologiesRaw || '').split(',').map((s: string) => s.trim()).filter(Boolean),
      skills: (v.skillsRaw || '').split(',').map((s: string) => s.trim()).filter(Boolean),
    };
    this.http.put('/api/v1/users/me/profile', payload).subscribe(() => {
      this.saveSuccess = true;
      setTimeout(() => this.saveSuccess = false, 2000);
    });
  }

  saveExperience(): void {
    if (this.expForm.invalid) return;
    const v = this.expForm.value;
    const payload: WorkExperience = {
      companyName: v.companyName!,
      title: v.title!,
      startDate: v.startDate!,
      endDate: v.isCurrent ? undefined : (v.endDate || undefined),
      isCurrent: v.isCurrent ?? false,
      description: v.description || undefined,
      location: v.location || undefined,
      technologies: (v.technologiesRaw || '').split(',').map((s: string) => s.trim()).filter(Boolean),
    };
    this.sectionsApi.addExperience(payload).subscribe(() => {
      this.sectionsApi.getExperience().subscribe(d => this.experiences = d);
      this.expForm.reset({ isCurrent: false });
      this.showExpForm = false;
    });
  }

  deleteExperience(id: string): void {
    this.sectionsApi.deleteExperience(id).subscribe(() =>
      this.sectionsApi.getExperience().subscribe(d => this.experiences = d));
  }

  saveProject(): void {
    if (this.projectForm.invalid) return;
    const v = this.projectForm.value;
    const payload: Project = {
      name: v.name!,
      description: v.description || undefined,
      githubUrl: v.githubUrl || undefined,
      liveUrl: v.liveUrl || undefined,
      measurableOutcomes: v.measurableOutcomes || undefined,
      isFeatured: v.isFeatured ?? false,
      technologies: (v.technologiesRaw || '').split(',').map((s: string) => s.trim()).filter(Boolean),
    };
    this.sectionsApi.addProject(payload).subscribe(() => {
      this.sectionsApi.getProjects().subscribe(d => this.projects = d);
      this.projectForm.reset({ isFeatured: false });
      this.showProjectForm = false;
    });
  }

  deleteProject(id: string): void {
    this.sectionsApi.deleteProject(id).subscribe(() =>
      this.sectionsApi.getProjects().subscribe(d => this.projects = d));
  }

  saveEducation(): void {
    if (this.eduForm.invalid) return;
    const v = this.eduForm.value;
    const payload: Education = {
      institution: v.institution!,
      degree: v.degree || undefined,
      fieldOfStudy: v.fieldOfStudy || undefined,
      startDate: v.startDate || undefined,
      endDate: v.endDate || undefined,
    };
    this.sectionsApi.addEducation(payload).subscribe(() => {
      this.sectionsApi.getEducation().subscribe(d => this.educations = d);
      this.eduForm.reset();
      this.showEduForm = false;
    });
  }

  deleteEducation(id: string): void {
    this.sectionsApi.deleteEducation(id).subscribe(() =>
      this.sectionsApi.getEducation().subscribe(d => this.educations = d));
  }

  saveCertification(): void {
    if (this.certForm.invalid) return;
    const v = this.certForm.value;
    const payload: Certification = {
      name: v.name!,
      issuer: v.issuer || undefined,
      issuedAt: v.issuedAt || undefined,
      credentialUrl: v.credentialUrl || undefined,
    };
    this.sectionsApi.addCertification(payload).subscribe(() => {
      this.sectionsApi.getCertifications().subscribe(d => this.certifications = d);
      this.certForm.reset();
      this.showCertForm = false;
    });
  }

  deleteCertification(id: string): void {
    this.sectionsApi.deleteCertification(id).subscribe(() =>
      this.sectionsApi.getCertifications().subscribe(d => this.certifications = d));
  }
}
