import { Component, OnInit, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { SkillsApiService } from '../../core/api/skills.api';
import { WorkExperience, Project, Education, Certification } from '../../core/models/profile-section.model';
import { ProfileSkill, SkillTaxonomy, TECH_CATEGORIES } from '../../core/models/skill-taxonomy.model';

type Tab = 'overview' | 'experience' | 'projects' | 'education' | 'certifications' | 'skills';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
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

          <!-- Photo upload -->
          <div class="flex items-center gap-4 pb-2 border-b border-gray-100">
            <div class="w-16 h-16 rounded-full overflow-hidden bg-gray-100 flex items-center justify-center shrink-0 border border-gray-200">
              @if (photoUrl) {
                <img [src]="photoUrl" alt="Profile photo" class="w-full h-full object-cover" />
              } @else {
                <span class="text-gray-400 text-2xl leading-none">&#128100;</span>
              }
            </div>
            <div>
              <input #photoInput type="file" accept="image/*" class="hidden" (change)="onPhotoSelected($event)" />
              <button type="button" (click)="photoInput.click()" [disabled]="uploadingPhoto"
                      class="border border-gray-300 text-gray-700 hover:bg-gray-50 px-3 py-1.5 rounded text-sm font-medium transition-colors disabled:opacity-50">
                {{ uploadingPhoto ? 'Uploading...' : (photoUrl ? 'Change Photo' : 'Upload Photo') }}
              </button>
              @if (photoError) {
                <span class="block text-red-600 text-xs mt-1">{{ photoError }}</span>
              }
            </div>
          </div>

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
            <div>
              <label class="block text-sm font-medium text-gray-700">Phone</label>
              <input formControlName="phone" class="mt-1 w-full border rounded px-3 py-2 text-sm" placeholder="+45 12 34 56 78"/>
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">Professional Summary</label>
            <textarea formControlName="summary" rows="4" class="mt-1 w-full border rounded px-3 py-2 text-sm"></textarea>
          </div>
          <!-- CV Import Section -->
          <div class="border-t border-gray-100 pt-4 mt-4">
            <h3 class="text-sm font-semibold text-gray-700 mb-3">Import CV</h3>
            <div class="flex flex-wrap gap-3">
              <!-- Generic CV PDF -->
              <div>
                <input #cvPdfFileInput type="file" accept=".pdf" class="hidden"
                       (change)="onCvPdfSelected($event)" />
                <button type="button" (click)="cvPdfFileInput.click()"
                        [disabled]="importingCvPdf"
                        class="border border-green-300 text-green-700 hover:bg-green-50 px-3 py-1.5 rounded text-sm font-medium transition-colors disabled:opacity-50">
                  {{ importingCvPdf ? 'Parsing...' : 'Upload CV (PDF)' }}
                </button>
              </div>
              <!-- LinkedIn PDF -->
              <div>
                <input #linkedinFileInput type="file" accept=".pdf" class="hidden"
                       (change)="onLinkedInPdfSelected($event)" />
                <button type="button" (click)="linkedinFileInput.click()"
                        [disabled]="importingLinkedIn"
                        class="border border-blue-300 text-blue-700 hover:bg-blue-50 px-3 py-1.5 rounded text-sm font-medium transition-colors disabled:opacity-50">
                  {{ importingLinkedIn ? 'Parsing...' : 'Upload LinkedIn PDF' }}
                </button>
              </div>
              <!-- Manual hint -->
              <span class="self-center text-xs text-gray-400">or fill the form manually below</span>
            </div>
            @if (cvPdfImportError) {
              <span class="block text-red-600 text-xs mt-1">{{ cvPdfImportError }}</span>
            }
            @if (linkedInImportError) {
              <span class="block text-red-600 text-xs mt-1">{{ linkedInImportError }}</span>
            }
          </div>

          <button type="submit" class="bg-blue-600 text-white px-4 py-2 rounded text-sm font-medium hover:bg-blue-700">
            Save Profile
          </button>
          <span *ngIf="saveSuccess" class="ml-3 text-green-600 text-sm">Saved!</span>
        </form>

        <!-- CV PDF Import Preview Modal -->
        @if (cvPdfPreview) {
          <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] overflow-y-auto p-6">
              <div class="flex items-center justify-between mb-4">
                <h2 class="text-lg font-semibold text-gray-900">CV Preview</h2>
                <button (click)="cvPdfPreview = null" class="text-gray-400 hover:text-gray-600 text-xl">✕</button>
              </div>
              <p class="text-sm text-gray-500 mb-4">
                Review the extracted data. Click "Apply to Profile" to fill your profile fields.
              </p>
              <div class="space-y-3 text-sm">
                @if (cvPdfPreview.fullName) {
                  <div><span class="font-medium text-gray-700">Name:</span> {{ cvPdfPreview.fullName }}</div>
                }
                @if (cvPdfPreview.headline) {
                  <div><span class="font-medium text-gray-700">Headline:</span> {{ cvPdfPreview.headline }}</div>
                }
                @if (cvPdfPreview.location) {
                  <div><span class="font-medium text-gray-700">Location:</span> {{ cvPdfPreview.location }}</div>
                }
                @if (cvPdfPreview.summary) {
                  <div>
                    <span class="font-medium text-gray-700">Summary:</span>
                    <p class="text-gray-600 mt-1 text-xs leading-relaxed line-clamp-4">{{ cvPdfPreview.summary }}</p>
                  </div>
                }
                @if (cvPdfPreview.skills?.length) {
                  <div>
                    <span class="font-medium text-gray-700">Skills ({{ cvPdfPreview.skills.length }}):</span>
                    <div class="flex flex-wrap gap-1 mt-1">
                      @for (s of cvPdfPreview.skills.slice(0, 20); track s) {
                        <span class="bg-green-50 text-green-700 text-xs px-2 py-0.5 rounded-full">{{ s }}</span>
                      }
                    </div>
                  </div>
                }
                @if (cvPdfPreview.technologies?.length) {
                  <div>
                    <span class="font-medium text-gray-700">Technologies:</span>
                    <div class="flex flex-wrap gap-1 mt-1">
                      @for (t of cvPdfPreview.technologies.slice(0, 20); track t) {
                        <span class="bg-blue-50 text-blue-700 text-xs px-2 py-0.5 rounded-full">{{ t }}</span>
                      }
                    </div>
                  </div>
                }
              </div>
              <div class="flex gap-3 mt-6 pt-4 border-t border-gray-100">
                <button (click)="applyCvPdfPreview()"
                        class="bg-green-600 text-white px-4 py-2 rounded text-sm font-medium hover:bg-green-700">
                  Apply to Profile
                </button>
                <button (click)="cvPdfPreview = null"
                        class="border border-gray-300 text-gray-700 px-4 py-2 rounded text-sm hover:bg-gray-50">
                  Cancel
                </button>
              </div>
            </div>
          </div>
        }

        <!-- LinkedIn Import Preview Modal -->
        @if (linkedInPreview) {
          <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
            <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] overflow-y-auto p-6">
              <div class="flex items-center justify-between mb-4">
                <h2 class="text-lg font-semibold text-gray-900">LinkedIn Profile Preview</h2>
                <button (click)="linkedInPreview = null" class="text-gray-400 hover:text-gray-600 text-xl">✕</button>
              </div>
              <p class="text-sm text-gray-500 mb-4">
                Review the extracted data below. Click "Apply to Profile" to fill your profile fields.
                You can edit anything after applying.
              </p>

              <div class="space-y-3 text-sm">
                @if (linkedInPreview.fullName) {
                  <div><span class="font-medium text-gray-700">Name:</span> {{ linkedInPreview.fullName }}</div>
                }
                @if (linkedInPreview.headline) {
                  <div><span class="font-medium text-gray-700">Headline:</span> {{ linkedInPreview.headline }}</div>
                }
                @if (linkedInPreview.location) {
                  <div><span class="font-medium text-gray-700">Location:</span> {{ linkedInPreview.location }}</div>
                }
                @if (linkedInPreview.summary) {
                  <div>
                    <span class="font-medium text-gray-700">Summary:</span>
                    <p class="text-gray-600 mt-1 text-xs leading-relaxed line-clamp-4">{{ linkedInPreview.summary }}</p>
                  </div>
                }
                @if (linkedInPreview.skills?.length) {
                  <div>
                    <span class="font-medium text-gray-700">Skills ({{ linkedInPreview.skills.length }}):</span>
                    <div class="flex flex-wrap gap-1 mt-1">
                      @for (s of linkedInPreview.skills.slice(0, 15); track s) {
                        <span class="bg-blue-50 text-blue-700 text-xs px-2 py-0.5 rounded-full">{{ s }}</span>
                      }
                      @if (linkedInPreview.skills.length > 15) {
                        <span class="text-xs text-gray-400">+{{ linkedInPreview.skills.length - 15 }} more</span>
                      }
                    </div>
                  </div>
                }
                @if (linkedInPreview.experience?.length) {
                  <div>
                    <span class="font-medium text-gray-700">Experience ({{ linkedInPreview.experience.length }} roles):</span>
                    <ul class="mt-1 space-y-1 text-xs text-gray-600">
                      @for (e of linkedInPreview.experience.slice(0, 5); track e.company) {
                        <li>{{ e.title }} {{ '@' }} {{ e.company }} ({{ e.startDate }} – {{ e.endDate || 'Present' }})</li>
                      }
                    </ul>
                  </div>
                }
                @if (linkedInPreview.education?.length) {
                  <div>
                    <span class="font-medium text-gray-700">Education:</span>
                    <ul class="mt-1 space-y-1 text-xs text-gray-600">
                      @for (e of linkedInPreview.education; track e.institution) {
                        <li>{{ e.degree }} in {{ e.fieldOfStudy }} — {{ e.institution }}</li>
                      }
                    </ul>
                  </div>
                }
              </div>

              <div class="flex gap-3 mt-6 pt-4 border-t border-gray-100">
                <button (click)="applyLinkedInPreview()"
                        class="bg-blue-600 text-white px-4 py-2 rounded text-sm font-medium hover:bg-blue-700">
                  Apply to Profile
                </button>
                <button (click)="linkedInPreview = null"
                        class="border border-gray-300 text-gray-700 px-4 py-2 rounded text-sm hover:bg-gray-50">
                  Cancel
                </button>
              </div>
            </div>
          </div>
        }
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

      <!-- Skills Tab -->
      <div *ngIf="activeTab === 'skills'">
        <h2 class="text-lg font-semibold mb-4">Skills</h2>

        <!-- Add Skill Form -->
        <div class="bg-gray-50 rounded p-4 mb-6">
          <h3 class="text-sm font-medium text-gray-700 mb-3">Add Skill</h3>
          <div class="grid grid-cols-2 gap-3 mb-3">
            <!-- Skill Name with Autocomplete -->
            <div class="relative">
              <label class="block text-xs font-medium text-gray-700 mb-1">Skill Name</label>
              <input
                [(ngModel)]="newSkill.skillName"
                (ngModelChange)="onSkillNameChange($event)"
                (blur)="hideDropdownDelayed()"
                placeholder="Search for a skill..."
                class="w-full border rounded px-2 py-1.5 text-sm"/>
              <div *ngIf="taxonomySuggestions.length > 0 && showDropdown"
                   class="absolute z-10 top-full left-0 right-0 bg-white border rounded shadow-lg mt-0.5 max-h-48 overflow-y-auto">
                <button *ngFor="let suggestion of taxonomySuggestions"
                        type="button"
                        (mousedown)="selectTaxonomySuggestion(suggestion)"
                        class="w-full text-left px-3 py-2 text-sm hover:bg-blue-50 border-b border-gray-100 last:border-0">
                  <span class="font-medium">{{ suggestion.name }}</span>
                  <span class="text-xs text-gray-400 ml-2">{{ suggestion.category }}</span>
                </button>
              </div>
            </div>

            <!-- Proficiency -->
            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">Proficiency</label>
              <select [(ngModel)]="newSkill.proficiencyLevel" class="w-full border rounded px-2 py-1.5 text-sm">
                <option value="BEGINNER">Beginner</option>
                <option value="INTERMEDIATE">Intermediate</option>
                <option value="ADVANCED">Advanced</option>
                <option value="EXPERT">Expert</option>
              </select>
            </div>

            <!-- Years Experience -->
            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">Years Experience (optional)</label>
              <input
                [(ngModel)]="newSkill.yearsExperience"
                type="number"
                min="0"
                placeholder="e.g. 3"
                class="w-full border rounded px-2 py-1.5 text-sm"/>
            </div>

            <!-- Used in Production -->
            <div class="flex items-end pb-1.5">
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input [(ngModel)]="newSkill.usedInProduction" type="checkbox" class="rounded"/>
                Used in production
              </label>
            </div>
          </div>

          <button (click)="addProfileSkill()" class="bg-blue-600 text-white px-3 py-1.5 rounded text-sm font-medium hover:bg-blue-700">
            Save Skill
          </button>
          <span *ngIf="skillSaveError" class="ml-3 text-red-600 text-sm">{{ skillSaveError }}</span>
        </div>

        <!-- Skills chip groups -->
        @if (profileSkills.length > 0) {
          <!-- Technologies group -->
          @if (techSkills.length > 0) {
            <div class="mb-6">
              <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Technologies</h3>
              <div class="flex flex-wrap gap-2">
                @for (skill of techSkills; track skill.id) {
                  <div class="relative">
                    <button type="button"
                            (click)="toggleEditSkill(skill.id!)"
                            [class]="'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition-colors ' + chipClass(skill)">
                      {{ skill.skillName }}
                      @if (skill.yearsExperience) {
                        <span class="text-xs opacity-70">{{ skill.yearsExperience }}y</span>
                      }
                    </button>
                    @if (editingSkillId === skill.id) {
                      <div class="absolute z-20 top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg p-3 w-52">
                        <div class="space-y-2">
                          <div>
                            <label class="block text-xs font-medium text-gray-600 mb-0.5">Proficiency</label>
                            <select [(ngModel)]="editingSkill.proficiencyLevel"
                                    class="w-full border rounded px-2 py-1 text-xs">
                              <option value="BEGINNER">Beginner</option>
                              <option value="INTERMEDIATE">Intermediate</option>
                              <option value="ADVANCED">Advanced</option>
                              <option value="EXPERT">Expert</option>
                            </select>
                          </div>
                          <div>
                            <label class="block text-xs font-medium text-gray-600 mb-0.5">Years experience</label>
                            <input type="number" min="0" [(ngModel)]="editingSkill.yearsExperience"
                                   class="w-full border rounded px-2 py-1 text-xs" placeholder="e.g. 3"/>
                          </div>
                          <div class="flex gap-2 pt-1">
                            <button (click)="saveEditSkill(skill)" class="flex-1 bg-blue-600 text-white text-xs py-1 rounded hover:bg-blue-700">Save</button>
                            <button (click)="deleteProfileSkill(skill.id!)" class="text-red-500 text-xs hover:text-red-700 px-2">Delete</button>
                          </div>
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }

          <!-- Skills group -->
          @if (softSkills.length > 0) {
            <div>
              <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Skills</h3>
              <div class="flex flex-wrap gap-2">
                @for (skill of softSkills; track skill.id) {
                  <div class="relative">
                    <button type="button"
                            (click)="toggleEditSkill(skill.id!)"
                            [class]="'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition-colors ' + chipClass(skill)">
                      {{ skill.skillName }}
                      @if (skill.yearsExperience) {
                        <span class="text-xs opacity-70">{{ skill.yearsExperience }}y</span>
                      }
                    </button>
                    @if (editingSkillId === skill.id) {
                      <div class="absolute z-20 top-full left-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg p-3 w-52">
                        <div class="space-y-2">
                          <div>
                            <label class="block text-xs font-medium text-gray-600 mb-0.5">Proficiency</label>
                            <select [(ngModel)]="editingSkill.proficiencyLevel"
                                    class="w-full border rounded px-2 py-1 text-xs">
                              <option value="BEGINNER">Beginner</option>
                              <option value="INTERMEDIATE">Intermediate</option>
                              <option value="ADVANCED">Advanced</option>
                              <option value="EXPERT">Expert</option>
                            </select>
                          </div>
                          <div>
                            <label class="block text-xs font-medium text-gray-600 mb-0.5">Years experience</label>
                            <input type="number" min="0" [(ngModel)]="editingSkill.yearsExperience"
                                   class="w-full border rounded px-2 py-1 text-xs" placeholder="e.g. 3"/>
                          </div>
                          <div class="flex gap-2 pt-1">
                            <button (click)="saveEditSkill(skill)" class="flex-1 bg-blue-600 text-white text-xs py-1 rounded hover:bg-blue-700">Save</button>
                            <button (click)="deleteProfileSkill(skill.id!)" class="text-red-500 text-xs hover:text-red-700 px-2">Delete</button>
                          </div>
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }
        } @else {
          <p class="text-gray-500 text-sm">No skills added yet. Search above to add your first skill.</p>
        }
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private sectionsApi = inject(ProfileSectionsApiService);
  private skillsApi = inject(SkillsApiService);

  @ViewChild('linkedinFileInput') linkedinFileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('cvPdfFileInput') cvPdfFileInput!: ElementRef<HTMLInputElement>;

  activeTab: Tab = 'overview';
  saveSuccess = false;
  showExpForm = false;
  showProjectForm = false;
  showEduForm = false;
  showCertForm = false;

  // Section-level skill picker state
  expEditSkills: SkillTaxonomy[] = [];
  projectEditSkills: SkillTaxonomy[] = [];
  educationEditSkills: SkillTaxonomy[] = [];
  sectionSkillInput = '';
  sectionSkillSuggestions: SkillTaxonomy[] = [];
  showSectionSkillDropdown = false;
  pendingNewSkill: { name: string; section: 'exp' | 'project' | 'edu' } | null = null;
  pendingNewSkillCategory = '';
  availableCategories: string[] = [];
  private sectionSkillSearchSubject = new Subject<string>();

  importingLinkedIn = false;
  linkedInImportError = '';
  linkedInPreview: any = null;

  importingCvPdf = false;
  cvPdfImportError = '';
  cvPdfPreview: any = null;

  photoUrl = '';
  uploadingPhoto = false;
  photoError = '';

  tabs = [
    { key: 'overview' as Tab, label: 'Overview' },
    { key: 'experience' as Tab, label: 'Experience' },
    { key: 'projects' as Tab, label: 'Projects' },
    { key: 'education' as Tab, label: 'Education' },
    { key: 'certifications' as Tab, label: 'Certifications' },
    { key: 'skills' as Tab, label: 'Skills' },
  ];

  experiences: WorkExperience[] = [];
  projects: Project[] = [];
  educations: Education[] = [];
  certifications: Certification[] = [];
  profileSkills: ProfileSkill[] = [];

  // Skills tab state
  readonly TECH_CATEGORIES = TECH_CATEGORIES;

  get techSkills(): ProfileSkill[] {
    return this.profileSkills.filter(s => s.category && TECH_CATEGORIES.has(s.category));
  }
  get softSkills(): ProfileSkill[] {
    return this.profileSkills.filter(s => !s.category || !TECH_CATEGORIES.has(s.category));
  }

  editingSkillId: string | null = null;
  editingSkill: Partial<ProfileSkill> = {};

  toggleEditSkill(id: string): void {
    if (this.editingSkillId === id) {
      this.editingSkillId = null;
      return;
    }
    const skill = this.profileSkills.find(s => s.id === id);
    if (skill) {
      this.editingSkill = { proficiencyLevel: skill.proficiencyLevel, yearsExperience: skill.yearsExperience };
    }
    this.editingSkillId = id;
  }

  saveEditSkill(original: ProfileSkill): void {
    const updated: ProfileSkill = {
      ...original,
      proficiencyLevel: this.editingSkill.proficiencyLevel ?? original.proficiencyLevel,
      yearsExperience: this.editingSkill.yearsExperience,
    };
    this.skillsApi.updateProfileSkill(original.id!, updated).subscribe({
      next: () => {
        this.editingSkillId = null;
        this.skillsApi.getProfileSkills().subscribe(d => this.profileSkills = d);
      }
    });
  }

  chipClass(skill: ProfileSkill): string {
    const level = skill.proficiencyLevel;
    if (level === 'EXPERT') return 'bg-purple-50 border-purple-300 text-purple-800 hover:bg-purple-100';
    if (level === 'ADVANCED') return 'bg-blue-50 border-blue-300 text-blue-800 hover:bg-blue-100';
    if (level === 'INTERMEDIATE') return 'bg-green-50 border-green-300 text-green-800 hover:bg-green-100';
    return 'bg-gray-50 border-gray-300 text-gray-700 hover:bg-gray-100';
  }

  newSkill: Partial<ProfileSkill> = {
    skillName: '',
    proficiencyLevel: 'INTERMEDIATE',
    yearsExperience: undefined,
    usedInProduction: false,
  };
  taxonomySuggestions: SkillTaxonomy[] = [];
  showDropdown = false;
  skillSaveError = '';
  private skillSearchSubject = new Subject<string>();

  profileForm = this.fb.group({
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
      next: profile => {
        this.profileForm.patchValue({
          ...profile,
          technologiesRaw: (profile.technologies || []).join(', '),
          skillsRaw: (profile.skills || []).join(', '),
        });
        if (profile.photoUrl) this.photoUrl = profile.photoUrl;
      },
      error: () => {}
    });
    this.loadSections();

    // Set up debounced skill search (profile skills tab)
    this.skillSearchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => q.length >= 1 ? this.skillsApi.searchTaxonomy(q) : [])
    ).subscribe({
      next: results => {
        this.taxonomySuggestions = results.slice(0, 8);
        this.showDropdown = this.taxonomySuggestions.length > 0;
      },
      error: () => { this.taxonomySuggestions = []; this.showDropdown = false; }
    });

    // Set up debounced skill search for section forms
    this.sectionSkillSearchSubject.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => q.length >= 1 ? this.skillsApi.searchTaxonomy(q) : [])
    ).subscribe({
      next: results => {
        this.sectionSkillSuggestions = results.slice(0, 8);
        this.showSectionSkillDropdown = this.sectionSkillSuggestions.length > 0 || this.sectionSkillInput.trim().length > 0;
      },
      error: () => { this.sectionSkillSuggestions = []; this.showSectionSkillDropdown = false; }
    });

    // Load categories for the "new skill" prompt
    this.skillsApi.getCategories().subscribe(cats => this.availableCategories = cats);
  }

  private loadSections(): void {
    this.sectionsApi.getExperience().subscribe(d => this.experiences = d);
    this.sectionsApi.getProjects().subscribe(d => this.projects = d);
    this.sectionsApi.getEducation().subscribe(d => this.educations = d);
    this.sectionsApi.getCertifications().subscribe(d => this.certifications = d);
    this.skillsApi.getProfileSkills().subscribe(d => this.profileSkills = d);
  }

  onSkillNameChange(value: string): void {
    this.showDropdown = false;
    if (value && value.length >= 1) {
      this.skillSearchSubject.next(value);
    } else {
      this.taxonomySuggestions = [];
    }
  }

  selectTaxonomySuggestion(suggestion: SkillTaxonomy): void {
    this.newSkill.skillName = suggestion.name;
    this.newSkill = { ...this.newSkill, taxonomyId: suggestion.id };
    this.taxonomySuggestions = [];
    this.showDropdown = false;
  }

  hideDropdownDelayed(): void {
    // Use a short timeout so mousedown on suggestion fires before blur hides it
    setTimeout(() => { this.showDropdown = false; }, 150);
  }

  proficiencyBadgeClass(level: string): string {
    const map: Record<string, string> = {
      BEGINNER: 'bg-gray-100 text-gray-600',
      INTERMEDIATE: 'bg-blue-100 text-blue-700',
      ADVANCED: 'bg-green-100 text-green-700',
      EXPERT: 'bg-purple-100 text-purple-700',
    };
    return map[level] ?? 'bg-gray-100 text-gray-600';
  }

  // ── Section skill picker ─────────────────────────────────────────────────

  onSectionSkillInput(value: string): void {
    this.showSectionSkillDropdown = false;
    if (value && value.length >= 1) {
      this.sectionSkillSearchSubject.next(value);
    } else {
      this.sectionSkillSuggestions = [];
    }
  }

  selectSectionSkill(skill: SkillTaxonomy, section: 'exp' | 'project' | 'edu'): void {
    const list = this.sectionSkillsFor(section);
    if (!list.find(s => s.id === skill.id)) list.push(skill);
    this.sectionSkillInput = '';
    this.sectionSkillSuggestions = [];
    this.showSectionSkillDropdown = false;
  }

  promptCreateSkillForSection(section: 'exp' | 'project' | 'edu'): void {
    const name = this.sectionSkillInput.trim();
    if (!name) return;
    this.pendingNewSkill = { name, section };
    this.pendingNewSkillCategory = '';
    this.sectionSkillSuggestions = [];
    this.showSectionSkillDropdown = false;
  }

  createSkillForSection(): void {
    if (!this.pendingNewSkill) return;
    const { name, section } = this.pendingNewSkill;
    this.skillsApi.createTaxonomySkill(name, this.pendingNewSkillCategory || undefined).subscribe({
      next: skill => {
        const list = this.sectionSkillsFor(section);
        if (!list.find(s => s.id === skill.id)) list.push(skill);
        this.sectionSkillInput = '';
        this.pendingNewSkill = null;
        this.pendingNewSkillCategory = '';
      }
    });
  }

  removeSkillFromSection(skillId: string, section: 'exp' | 'project' | 'edu'): void {
    const list = this.sectionSkillsFor(section);
    const idx = list.findIndex(s => s.id === skillId);
    if (idx !== -1) list.splice(idx, 1);
  }

  hideSectionSkillDropdownDelayed(): void {
    setTimeout(() => { this.showSectionSkillDropdown = false; }, 150);
  }

  private sectionSkillsFor(section: 'exp' | 'project' | 'edu'): SkillTaxonomy[] {
    if (section === 'exp') return this.expEditSkills;
    if (section === 'project') return this.projectEditSkills;
    return this.educationEditSkills;
  }

  // ── Profile skills tab ───────────────────────────────────────────────────

  addProfileSkill(): void {
    this.skillSaveError = '';
    if (!this.newSkill.skillName?.trim()) {
      this.skillSaveError = 'Skill name is required.';
      return;
    }
    const save = (taxonomyId?: string) => {
      const payload: ProfileSkill = {
        skillName: this.newSkill.skillName!.trim(),
        taxonomyId: taxonomyId ?? this.newSkill.taxonomyId,
        proficiencyLevel: this.newSkill.proficiencyLevel ?? 'INTERMEDIATE',
        yearsExperience: this.newSkill.yearsExperience ?? undefined,
        usedInProduction: this.newSkill.usedInProduction ?? false,
        displayOrder: this.profileSkills.length,
      };
      this.skillsApi.addProfileSkill(payload).subscribe({
        next: () => {
          this.skillsApi.getProfileSkills().subscribe(d => this.profileSkills = d);
          this.newSkill = { skillName: '', proficiencyLevel: 'INTERMEDIATE', yearsExperience: undefined, usedInProduction: false };
          this.taxonomySuggestions = [];
        },
        error: () => { this.skillSaveError = 'Failed to save skill. Please try again.'; }
      });
    };

    // If no taxonomy entry selected, auto-create one
    if (!this.newSkill.taxonomyId) {
      this.skillsApi.createTaxonomySkill(this.newSkill.skillName!.trim()).subscribe({
        next: taxonomy => save(taxonomy.id),
        error: () => save()  // Fall back to saving without taxonomy
      });
    } else {
      save();
    }
  }

  deleteProfileSkill(id: string): void {
    this.skillsApi.deleteProfileSkill(id).subscribe(() =>
      this.skillsApi.getProfileSkills().subscribe(d => this.profileSkills = d));
  }

  onPhotoSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.uploadingPhoto = true;
    this.photoError = '';
    const formData = new FormData();
    formData.append('file', file);
    this.http.post<{ url: string }>('/api/v1/users/me/profile/photo', formData).subscribe({
      next: res => {
        this.photoUrl = res.url;
        this.uploadingPhoto = false;
      },
      error: () => {
        this.photoError = 'Failed to upload photo. Please try again.';
        this.uploadingPhoto = false;
      }
    });
  }

  onLinkedInPdfSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.importingLinkedIn = true;
    this.linkedInImportError = '';
    const formData = new FormData();
    formData.append('file', file);
    this.http.post<any>('/api/v1/users/me/import/linkedin-pdf', formData).subscribe({
      next: result => {
        this.linkedInPreview = result;
        this.importingLinkedIn = false;
      },
      error: () => {
        this.linkedInImportError = 'Failed to parse PDF. Please try again.';
        this.importingLinkedIn = false;
      }
    });
  }

  onCvPdfSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.importingCvPdf = true;
    this.cvPdfImportError = '';
    const formData = new FormData();
    formData.append('file', file);
    this.http.post<any>('/api/v1/profile/import/cv-pdf', formData).subscribe({
      next: result => {
        this.cvPdfPreview = result;
        this.importingCvPdf = false;
      },
      error: () => {
        this.cvPdfImportError = 'Failed to parse PDF. Please try again.';
        this.importingCvPdf = false;
      }
    });
  }

  applyCvPdfPreview(): void {
    if (!this.cvPdfPreview) return;
    const p = this.cvPdfPreview;
    this.profileForm.patchValue({
      fullName: p.fullName || this.profileForm.value.fullName,
      headline: p.headline || this.profileForm.value.headline,
      summary: p.summary || this.profileForm.value.summary,
      location: p.location || this.profileForm.value.location,
    });
    this.cvPdfPreview = null;
    this.saveProfile();
  }

  applyLinkedInPreview(): void {
    if (!this.linkedInPreview) return;
    const p = this.linkedInPreview;
    this.profileForm.patchValue({
      fullName: p.fullName || this.profileForm.value.fullName,
      headline: p.headline || this.profileForm.value.headline,
      summary: p.summary || this.profileForm.value.summary,
      location: p.location || this.profileForm.value.location,
      skillsRaw: p.skills?.join(', ') || this.profileForm.value.skillsRaw,
    });
    this.linkedInPreview = null;
    // Save automatically
    this.saveProfile();
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
      skills: [...this.expEditSkills],
    };
    this.sectionsApi.addExperience(payload).subscribe(() => {
      this.sectionsApi.getExperience().subscribe(d => this.experiences = d);
      this.expForm.reset({ isCurrent: false });
      this.expEditSkills = [];
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
      skills: [...this.projectEditSkills],
    };
    this.sectionsApi.addProject(payload).subscribe(() => {
      this.sectionsApi.getProjects().subscribe(d => this.projects = d);
      this.projectForm.reset({ isFeatured: false });
      this.projectEditSkills = [];
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
