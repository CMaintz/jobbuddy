import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Profile } from '../../core/models/user.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">Profile</h1>

      @if (success) {
        <div class="bg-green-50 text-green-700 rounded-md p-3 text-sm">Profile saved!</div>
      }
      @if (error) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ error }}</div>
      }

      <div class="card">
        <h2 class="text-base font-semibold text-gray-900 mb-4">Personal Information</h2>
        <form [formGroup]="form" (ngSubmit)="save()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Full Name</label>
              <input type="text" formControlName="fullName" class="input" />
            </div>
            <div>
              <label class="label">Headline</label>
              <input type="text" formControlName="headline" class="input"
                     placeholder="Senior Software Engineer" />
            </div>
          </div>

          <div>
            <label class="label">Summary</label>
            <textarea formControlName="summary" class="input min-h-20"
                      placeholder="Brief professional summary..."></textarea>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Location</label>
              <input type="text" formControlName="location" class="input" placeholder="Copenhagen, DK" />
            </div>
            <div>
              <label class="label">Years of Experience</label>
              <input type="number" formControlName="yearsExperience" class="input" min="0" />
            </div>
          </div>

          <div>
            <label class="label">Skills (comma-separated)</label>
            <input type="text" formControlName="skillsStr" class="input"
                   placeholder="Java, Spring Boot, PostgreSQL" />
          </div>

          <div>
            <label class="label">Technologies (comma-separated)</label>
            <input type="text" formControlName="technologiesStr" class="input"
                   placeholder="Docker, Kubernetes, AWS" />
          </div>

          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">Min Salary</label>
              <input type="number" formControlName="desiredSalaryMin" class="input" />
            </div>
            <div>
              <label class="label">Max Salary</label>
              <input type="number" formControlName="desiredSalaryMax" class="input" />
            </div>
            <div>
              <label class="label">Currency</label>
              <input type="text" formControlName="desiredCurrency" class="input" placeholder="DKK" />
            </div>
          </div>

          <div>
            <label class="label">Remote Preference</label>
            <select formControlName="remotePreference" class="input">
              <option value="">Any</option>
              <option value="ON_SITE">On-site only</option>
              <option value="HYBRID">Hybrid</option>
              <option value="FULLY_REMOTE">Fully remote</option>
            </select>
          </div>

          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">LinkedIn</label>
              <input type="url" formControlName="linkedinUrl" class="input" />
            </div>
            <div>
              <label class="label">GitHub</label>
              <input type="url" formControlName="githubUrl" class="input" />
            </div>
            <div>
              <label class="label">Website</label>
              <input type="url" formControlName="websiteUrl" class="input" />
            </div>
          </div>

          <button type="submit" [disabled]="loading" class="btn-primary">
            {{ loading ? 'Saving...' : 'Save Profile' }}
          </button>
        </form>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

  loading = false;
  success = false;
  error = '';

  form = this.fb.group({
    fullName: [''],
    headline: [''],
    summary: [''],
    location: [''],
    yearsExperience: [null as number | null],
    skillsStr: [''],
    technologiesStr: [''],
    desiredSalaryMin: [null as number | null],
    desiredSalaryMax: [null as number | null],
    desiredCurrency: ['DKK'],
    remotePreference: [''],
    linkedinUrl: [''],
    githubUrl: [''],
    websiteUrl: ['']
  });

  ngOnInit(): void {
    this.http.get<Profile>('/api/v1/users/me/profile').subscribe({
      next: p => this.form.patchValue({
        ...p,
        skillsStr: p.skills?.join(', ') ?? '',
        technologiesStr: p.technologies?.join(', ') ?? ''
      } as any)
    });
  }

  save(): void {
    this.loading = true;
    this.success = false;
    this.error = '';
    const v = this.form.value;
    const payload: Profile = {
      fullName: v.fullName || undefined,
      headline: v.headline || undefined,
      summary: v.summary || undefined,
      location: v.location || undefined,
      yearsExperience: v.yearsExperience ?? undefined,
      skills: v.skillsStr ? v.skillsStr.split(',').map(s => s.trim()).filter(Boolean) : [],
      technologies: v.technologiesStr ? v.technologiesStr.split(',').map(s => s.trim()).filter(Boolean) : [],
      desiredSalaryMin: v.desiredSalaryMin ?? undefined,
      desiredSalaryMax: v.desiredSalaryMax ?? undefined,
      desiredCurrency: v.desiredCurrency || undefined,
      remotePreference: v.remotePreference || undefined,
      linkedinUrl: v.linkedinUrl || undefined,
      githubUrl: v.githubUrl || undefined,
      websiteUrl: v.websiteUrl || undefined
    };
    this.http.put('/api/v1/users/me/profile', payload).subscribe({
      next: () => { this.success = true; this.loading = false; },
      error: e => {
        this.error = e.error?.message || 'Save failed';
        this.loading = false;
      }
    });
  }
}
