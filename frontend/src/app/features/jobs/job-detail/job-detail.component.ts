import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JobsApiService } from '../../../core/api/jobs.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { NotesApiService } from '../../../core/api/notes.api';
import { Job } from '../../../core/models/job.model';
import { Note } from '../../../core/models/note.model';

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="space-y-6 max-w-3xl mx-auto">
      <a routerLink="/jobs/search" class="text-sm text-blue-600 hover:underline">← Back to jobs</a>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (!job) {
        <div class="text-center py-12 text-gray-500">Job not found.</div>
      } @else {
        <div class="card space-y-4">
          <!-- Header -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1 min-w-0">
              <h1 class="text-2xl font-bold text-gray-900">{{ job.title }}</h1>
              <p class="text-gray-600 mt-1">{{ job.companyName }}</p>
            </div>
            <div class="flex flex-col gap-2 shrink-0">
              <button (click)="generateApplication()" class="btn-primary text-sm whitespace-nowrap">
                Generate Application
              </button>
              <div class="flex gap-2">
                <button (click)="toggleSave()" class="btn-secondary text-sm flex-1">
                  {{ saved ? 'Unsave' : 'Save' }}
                </button>
                <button (click)="apply()" [disabled]="applying" class="btn-secondary text-sm flex-1">
                  {{ applying ? '...' : 'Apply' }}
                </button>
              </div>
            </div>
          </div>

          <!-- Meta badges -->
          <div class="flex flex-wrap gap-2 text-sm text-gray-500">
            @if (job.location) {
              <span class="flex items-center gap-1"><span>📍</span>{{ job.location }}</span>
            }
            @if (job.employmentType) {
              <span class="flex items-center gap-1"><span>🕐</span>{{ job.employmentType }}</span>
            }
            @if (job.remoteType) {
              <span class="flex items-center gap-1"><span>🏠</span>{{ job.remoteType }}</span>
            }
            @if (job.seniority) {
              <span class="flex items-center gap-1"><span>📊</span>{{ job.seniority }}</span>
            }
            @if (job.salaryMin) {
              <span class="flex items-center gap-1">
                <span>💰</span>{{ job.salaryMin | number:'1.0-0' }}
                @if (job.salaryMax) { – {{ job.salaryMax | number:'1.0-0' }} }
                {{ job.currency ?? 'DKK' }}/yr
              </span>
            }
          </div>

          @if (applied) {
            <div class="bg-green-50 text-green-700 rounded-md p-3 text-sm">
              Application created! <a routerLink="/applications" class="underline">View applications →</a>
            </div>
          }

          @if (saveMessage) {
            <div class="bg-blue-50 text-blue-700 rounded-md p-3 text-sm">{{ saveMessage }}</div>
          }

          <!-- AI Summary -->
          @if (job.aiSummary) {
            <div class="bg-blue-50 rounded-lg p-4">
              <h3 class="text-sm font-semibold text-blue-800 mb-1">AI Summary</h3>
              <p class="text-sm text-blue-900 leading-relaxed">{{ job.aiSummary }}</p>
            </div>
          }

          <!-- Technologies & Skills -->
          @if (job.technologies?.length || job.skills?.length) {
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Technologies & Skills</h3>
              <div class="flex flex-wrap gap-1.5">
                @for (t of (job.technologies ?? []).concat(job.skills ?? []); track t) {
                  <span class="text-xs bg-gray-100 text-gray-700 px-2 py-0.5 rounded-full">{{ t }}</span>
                }
              </div>
            </div>
          }

          <!-- Full Job Description -->
          @if (job.descriptionClean) {
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Job Description</h3>
              <div class="text-sm text-gray-700 whitespace-pre-line leading-relaxed prose prose-sm max-w-none">
                {{ job.descriptionClean }}
              </div>
            </div>
          }

          <!-- Footer links -->
          <div class="pt-2 border-t border-gray-100 flex items-center justify-between">
            <a [href]="job.url" target="_blank" rel="noopener" class="text-sm text-blue-600 hover:underline">
              View original posting →
            </a>
            <button (click)="generateApplication()" class="text-sm text-blue-600 hover:underline">
              Generate application →
            </button>
          </div>
        </div>

        <!-- Notes Section -->
        <div class="card space-y-3">
          <h2 class="text-base font-semibold text-gray-900">Notes</h2>

          @for (note of notes; track note.id) {
            <div class="group flex items-start gap-2 p-3 bg-gray-50 rounded-lg">
              @if (editingNoteId === note.id) {
                <textarea [(ngModel)]="editContent" rows="3"
                          class="input flex-1 text-sm resize-none"></textarea>
                <div class="flex flex-col gap-1 shrink-0">
                  <button (click)="saveEdit(note)" class="text-xs text-green-600 hover:text-green-700 font-medium">Save</button>
                  <button (click)="cancelEdit()" class="text-xs text-gray-400 hover:text-gray-600">Cancel</button>
                </div>
              } @else {
                <p class="flex-1 text-sm text-gray-700 whitespace-pre-line">{{ note.content }}</p>
                <div class="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
                  <button (click)="startEdit(note)" class="text-xs text-blue-500 hover:text-blue-700">Edit</button>
                  <button (click)="deleteNote(note.id)" class="text-xs text-red-400 hover:text-red-600">Delete</button>
                </div>
              }
            </div>
          }

          <!-- New note input -->
          <div class="flex gap-2">
            <textarea [(ngModel)]="newNoteContent" placeholder="Add a note about this job..."
                      rows="2" class="input flex-1 text-sm resize-none"></textarea>
            <button (click)="addNote()" [disabled]="!newNoteContent.trim()"
                    class="btn-primary text-sm self-end px-4 py-2 shrink-0">
              Add
            </button>
          </div>
        </div>
      }
    </div>
  `
})
export class JobDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);
  private notesApi = inject(NotesApiService);

  job: Job | null = null;
  notes: Note[] = [];
  loading = true;
  applying = false;
  applied = false;
  saved = false;
  saveMessage = '';

  newNoteContent = '';
  editingNoteId: string | null = null;
  editContent = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.jobsApi.getById(id).subscribe({
      next: j => {
        this.job = j;
        this.loading = false;
        this.notesApi.getForJob(j.id).subscribe(ns => this.notes = ns);
      },
      error: () => this.loading = false
    });
  }

  generateApplication(): void {
    if (!this.job) return;
    this.router.navigate(['/ai/generate'], { queryParams: { jobId: this.job.id } });
  }

  toggleSave(): void {
    if (!this.job) return;
    if (this.saved) {
      this.jobsApi.unsave(this.job.id).subscribe(() => {
        this.saved = false;
        this.saveMessage = 'Removed from saved jobs.';
        setTimeout(() => this.saveMessage = '', 2000);
      });
    } else {
      this.jobsApi.save(this.job.id).subscribe(() => {
        this.saved = true;
        this.saveMessage = 'Job saved!';
        setTimeout(() => this.saveMessage = '', 2000);
      });
    }
  }

  apply(): void {
    if (!this.job || this.applying) return;
    this.applying = true;
    this.appsApi.create(this.job.id).subscribe({
      next: () => { this.applying = false; this.applied = true; },
      error: () => this.applying = false
    });
  }

  addNote(): void {
    if (!this.job || !this.newNoteContent.trim()) return;
    this.notesApi.create(this.job.id, this.newNoteContent.trim()).subscribe(note => {
      this.notes = [...this.notes, note];
      this.newNoteContent = '';
    });
  }

  startEdit(note: Note): void {
    this.editingNoteId = note.id;
    this.editContent = note.content;
  }

  cancelEdit(): void {
    this.editingNoteId = null;
    this.editContent = '';
  }

  saveEdit(note: Note): void {
    if (!this.job || !this.editContent.trim()) return;
    this.notesApi.update(this.job.id, note.id, this.editContent.trim()).subscribe(updated => {
      this.notes = this.notes.map(n => n.id === updated.id ? updated : n);
      this.cancelEdit();
    });
  }

  deleteNote(noteId: string): void {
    if (!this.job) return;
    this.notesApi.delete(this.job.id, noteId).subscribe(() => {
      this.notes = this.notes.filter(n => n.id !== noteId);
    });
  }
}
