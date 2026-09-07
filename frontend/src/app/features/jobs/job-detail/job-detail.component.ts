import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JobsApiService } from '../../../core/api/jobs.api';
import { NotesApiService } from '../../../core/api/notes.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { AiApiService } from '../../../core/api/ai.api';
import { InterviewPrepApiService, InterviewQuestion } from '../../../core/api/interview-prep.api';
import { RemindersApiService, FollowUpReminder } from '../../../core/api/reminders.api';
import { Job } from '../../../core/models/job.model';
import { Note } from '../../../core/models/note.model';
import { GeneratedDocument } from '../../../core/models/generated-document.model';
import { Application } from '../../../core/models/application.model';
import { ConfirmDeleteButtonComponent } from '../../../shared/components/ui/confirm-delete-button.component';
import { EmptyStateComponent } from '../../../shared/components/ui/empty-state.component';
import { clearTextAfter, runAction } from '../../../shared/utils/async-ui';
import { LucideAngularModule, MapPin, Briefcase, Monitor, TrendingUp, Banknote } from 'lucide-angular';
import { SkillGapApiService, SkillGapResult } from '../../../core/api/skill-gap.api';

const CATEGORY_LABELS: Record<string, string> = {
  BEHAVIORAL: 'Behavioral',
  TECHNICAL: 'Technical',
  SITUATIONAL: 'Situational',
  COMPANY: 'Company',
};

const CATEGORY_COLORS: Record<string, string> = {
  BEHAVIORAL: 'bg-blue-50 text-blue-700',
  TECHNICAL: 'bg-purple-50 text-purple-700',
  SITUATIONAL: 'bg-yellow-50 text-yellow-700',
  COMPANY: 'bg-green-50 text-green-700',
};

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ConfirmDeleteButtonComponent, EmptyStateComponent, LucideAngularModule],
  template: `
    <div class="space-y-6 max-w-3xl mx-auto">
      <a routerLink="/jobs/search" class="text-sm text-blue-600 hover:underline">← Back to jobs</a>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (!job) {
        <app-empty-state message="Job not found."></app-empty-state>
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
                <button (click)="apply()" class="btn-secondary text-sm flex-1">
                  Apply
                </button>
              </div>
            </div>
          </div>

          <!-- Meta badges -->
          <div class="flex flex-wrap gap-2 text-sm text-gray-500">
            @if (job.location) {
              <span class="flex items-center gap-1"><lucide-icon [img]="MapPinIcon" [size]="14" class="text-gray-400"></lucide-icon>{{ job.location }}</span>
            }
            @if (job.employmentType) {
              <span class="flex items-center gap-1"><lucide-icon [img]="BriefcaseIcon" [size]="14" class="text-gray-400"></lucide-icon>{{ job.employmentType }}</span>
            }
            @if (job.remoteType) {
              <span class="flex items-center gap-1"><lucide-icon [img]="MonitorIcon" [size]="14" class="text-gray-400"></lucide-icon>{{ job.remoteType }}</span>
            }
            @if (job.seniority) {
              <span class="flex items-center gap-1"><lucide-icon [img]="TrendingUpIcon" [size]="14" class="text-gray-400"></lucide-icon>{{ job.seniority }}</span>
            }
            @if (job.salaryMin) {
              <span class="flex items-center gap-1">
                <lucide-icon [img]="BanknoteIcon" [size]="14" class="text-gray-400"></lucide-icon>{{ job.salaryMin | number:'1.0-0' }}
                @if (job.salaryMax) { – {{ job.salaryMax | number:'1.0-0' }} }
                {{ job.currency ?? 'DKK' }}/yr
              </span>
            }
          </div>

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

          <!-- Skill Gap Analysis -->
          @if (job.skills?.length || job.technologies?.length) {
            <div class="border-t border-gray-100 pt-4">
              <div class="flex items-center justify-between mb-3">
                <h3 class="text-sm font-semibold text-gray-700">Skill Gap Analysis</h3>
                @if (!skillGap && !skillGapLoading) {
                  <button (click)="analyzeSkillGap()" class="text-xs text-blue-600 hover:text-blue-700 font-medium">
                    Analyse →
                  </button>
                }
              </div>
              @if (skillGapLoading) {
                <p class="text-xs text-gray-400">Analysing...</p>
              }
              @if (skillGap) {
                <div class="space-y-2">
                  <!-- Coverage bar -->
                  <div class="flex items-center gap-2">
                    <div class="flex-1 bg-gray-100 rounded-full h-2">
                      <div class="h-2 rounded-full transition-all"
                           [class.bg-green-500]="skillGap.coveragePct >= 70"
                           [class.bg-yellow-500]="skillGap.coveragePct >= 40 && skillGap.coveragePct < 70"
                           [class.bg-red-400]="skillGap.coveragePct < 40"
                           [style.width.%]="skillGap.coveragePct"></div>
                    </div>
                    <span class="text-xs font-medium text-gray-600 shrink-0">{{ skillGap.coveragePct }}% match</span>
                  </div>
                  @if (skillGap.matched.length) {
                    <div class="flex flex-wrap gap-1.5">
                      @for (s of skillGap.matched; track s) {
                        <span class="text-xs bg-green-50 text-green-700 border border-green-200 px-2 py-0.5 rounded-full">✓ {{ s }}</span>
                      }
                    </div>
                  }
                  @if (skillGap.missing.length) {
                    <div class="flex flex-wrap gap-1.5">
                      @for (s of skillGap.missing; track s) {
                        <span class="text-xs bg-red-50 text-red-600 border border-red-200 px-2 py-0.5 rounded-full">✗ {{ s }}</span>
                      }
                    </div>
                  }
                </div>
              }
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

        <!-- Recruiter Outreach Section -->
        <div class="card space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-base font-semibold text-gray-900">Recruiter Outreach</h2>
            @if (application?.recruiterReply) {
              <span class="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-medium">Reply received</span>
            }
          </div>

          <!-- Recruiter details row -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-xs font-medium text-gray-500 block mb-1">Recruiter name</label>
              <input type="text" [(ngModel)]="recruiterName" placeholder="e.g. Jane Smith"
                     class="input text-sm w-full" />
            </div>
            <div>
              <label class="text-xs font-medium text-gray-500 block mb-1">Email (optional)</label>
              <input type="email" [(ngModel)]="recruiterEmail" placeholder="recruiter@company.com"
                     class="input text-sm w-full" />
            </div>
          </div>

          <!-- Outreach message -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-medium text-gray-500">Outreach message</label>
              <button class="text-xs text-blue-600 hover:underline"
                      [disabled]="generatingOutreach"
                      (click)="generateOutreachMessage()">
                {{ generatingOutreach ? 'Generating...' : (application?.recruiterMessage ? 'Regenerate' : 'Generate with AI') }}
              </button>
            </div>
            <textarea [(ngModel)]="recruiterMessageText"
                      placeholder="Write or generate an outreach message to the recruiter or hiring manager..."
                      rows="5" class="input text-sm w-full resize-none"></textarea>
          </div>

          <button class="btn-primary text-sm self-start"
                  [disabled]="savingOutreach || (!recruiterMessageText.trim() && !recruiterName.trim())"
                  (click)="saveOutreach()">
            {{ savingOutreach ? 'Saving...' : 'Save' }}
          </button>

          @if (outreachSaved) {
            <p class="text-xs text-green-600">Saved. Copy the message above and send it to the recruiter.</p>
          }

          <!-- Recruiter reply -->
          <div class="border-t border-gray-100 pt-4">
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-medium text-gray-500">Recruiter's reply</label>
              <span class="text-xs text-gray-400">Paste their response here — it will be used as AI context when generating your CV and cover letter</span>
            </div>
            <textarea [(ngModel)]="recruiterReplyText"
                      placeholder="Paste the recruiter's reply here..."
                      rows="4" class="input text-sm w-full resize-none"></textarea>
            <button class="btn-secondary text-sm mt-2"
                    [disabled]="savingReply || !recruiterReplyText.trim()"
                    (click)="saveReply()">
              {{ savingReply ? 'Saving...' : 'Save reply' }}
            </button>
            @if (replySaved) {
              <p class="text-xs text-green-600 mt-1">Reply saved. The AI will use it as context when generating application documents.</p>
            }
          </div>
        </div>

        <!-- Follow-up Reminders Section (requires application) -->
        @if (application) {
          <div class="card space-y-3">
            <div class="flex items-center justify-between">
              <h2 class="text-base font-semibold text-gray-900">Follow-up Reminders</h2>
              @if (overdueCount > 0) {
                <span class="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-medium">
                  {{ overdueCount }} overdue
                </span>
              }
            </div>

            @for (r of reminders; track r.id) {
              <div class="flex items-start gap-3 p-3 rounded-lg border"
                   [ngClass]="reminderRowClass(r)">
                <input type="checkbox" [checked]="r.completed"
                       (change)="completeReminder(r)"
                       [disabled]="r.completed"
                       class="mt-0.5 shrink-0 cursor-pointer" />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-gray-900"
                     [class.line-through]="r.completed"
                     [class.text-gray-400]="r.completed">
                    {{ r.note || 'Follow up' }}
                  </p>
                  <p class="text-xs mt-0.5"
                     [ngClass]="reminderDateClass(r)">
                    {{ r.completed ? 'Completed' : reminderDueLabel(r) }}
                  </p>
                </div>
                @if (!r.completed) {
                  <app-confirm-delete-button
                    buttonClass="text-xs text-red-400 hover:text-red-600 shrink-0"
                    (confirmed)="deleteReminder(r)">
                  </app-confirm-delete-button>
                }
              </div>
            }

            @if (reminders.length === 0) {
              <p class="text-sm text-gray-400 text-center py-2">No reminders set.</p>
            }

            <!-- Add reminder -->
            <div class="border-t border-gray-100 pt-3 space-y-2">
              <div class="flex gap-2">
                <input type="text" [(ngModel)]="newReminderNote"
                       placeholder="e.g. Follow up if no reply"
                       class="input text-sm flex-1" />
                <input type="datetime-local" [(ngModel)]="newReminderDueAt"
                       class="input text-sm w-48" />
                <button (click)="addReminder()"
                        [disabled]="!newReminderDueAt"
                        class="btn-secondary text-sm px-3 shrink-0">Set</button>
              </div>
              <!-- Quick date shortcuts -->
              <div class="flex gap-2">
                <button (click)="setReminderIn(1)" class="text-xs text-blue-500 hover:text-blue-700">+1 day</button>
                <button (click)="setReminderIn(3)" class="text-xs text-blue-500 hover:text-blue-700">+3 days</button>
                <button (click)="setReminderIn(7)" class="text-xs text-blue-500 hover:text-blue-700">+1 week</button>
                <button (click)="setReminderIn(14)" class="text-xs text-blue-500 hover:text-blue-700">+2 weeks</button>
              </div>
            </div>
          </div>
        }

        <!-- Interview Prep Section -->
        <div class="card space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-base font-semibold text-gray-900">Interview Prep</h2>
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-400">{{ preparedCount }}/{{ interviewQuestions.length }} practiced</span>
              <button class="btn-primary text-xs px-3 py-1.5"
                      [disabled]="generatingQuestions"
                      (click)="generateInterviewQuestions()">
                {{ generatingQuestions ? 'Generating...' : 'Generate with AI' }}
              </button>
            </div>
          </div>

          @if (generateQuestionsError) {
            <p class="text-xs text-red-500">{{ generateQuestionsError }}</p>
          }

          <!-- Question list -->
          @for (q of interviewQuestions; track q.id) {
            <div class="border border-gray-200 rounded-lg overflow-hidden"
                 [class.border-green-200]="q.practiced"
                 [class.bg-green-50]="q.practiced">
              <div class="p-3">
                <div class="flex items-start gap-2">
                  <input type="checkbox" [checked]="q.practiced"
                         (change)="togglePracticed(q)"
                         class="mt-1 shrink-0 cursor-pointer" />
                  <div class="flex-1 min-w-0">
                    <div class="flex items-start gap-2 justify-between">
                      <p class="text-sm font-medium text-gray-900 leading-snug" [class.line-through]="q.practiced"
                         [class.text-gray-400]="q.practiced">{{ q.question }}</p>
                      <div class="flex items-center gap-2 shrink-0">
                        <span class="text-xs px-2 py-0.5 rounded-full font-medium"
                              [ngClass]="categoryColor(q.category)">
                          {{ categoryLabel(q.category) }}
                        </span>
                        <app-confirm-delete-button
                          buttonClass="text-xs text-red-400 hover:text-red-600"
                          (confirmed)="deleteQuestion(q)">
                        </app-confirm-delete-button>
                      </div>
                    </div>

                    <!-- STAR Answer -->
                    <div class="mt-2">
                      @if (editingStarId === q.id) {
                        <textarea [(ngModel)]="editingStarText"
                                  placeholder="Write your STAR answer (Situation, Task, Action, Result)..."
                                  rows="5" class="input text-sm w-full resize-none mt-1"></textarea>
                        <div class="flex gap-2 mt-1">
                          <button (click)="saveStarAnswer(q)" class="text-xs text-green-600 font-medium hover:text-green-700">Save</button>
                          <button (click)="cancelStarEdit()" class="text-xs text-gray-400 hover:text-gray-600">Cancel</button>
                        </div>
                      } @else {
                        @if (q.starAnswer) {
                          <div class="text-xs text-gray-600 bg-white border border-gray-100 rounded p-2 mt-1 whitespace-pre-line cursor-pointer hover:border-blue-300"
                               (click)="startStarEdit(q)">{{ q.starAnswer }}</div>
                        } @else {
                          <button (click)="startStarEdit(q)"
                                  class="text-xs text-blue-500 hover:text-blue-700 mt-1">
                            + Add STAR answer
                          </button>
                        }
                      }
                    </div>
                  </div>
                </div>
              </div>
            </div>
          }

          @if (interviewQuestions.length === 0 && !generatingQuestions) {
            <p class="text-sm text-gray-400 text-center py-4">
              No questions yet. Generate some with AI or add your own below.
            </p>
          }

          <!-- Add question manually -->
          <div class="border-t border-gray-100 pt-3">
            <div class="flex gap-2">
              <input type="text" [(ngModel)]="newQuestionText"
                     placeholder="Add your own question..."
                     class="input text-sm flex-1"
                     (keydown.enter)="addQuestionManually()" />
              <select [(ngModel)]="newQuestionCategory" class="input text-sm w-36">
                <option value="BEHAVIORAL">Behavioral</option>
                <option value="TECHNICAL">Technical</option>
                <option value="SITUATIONAL">Situational</option>
                <option value="COMPANY">Company</option>
              </select>
              <button (click)="addQuestionManually()"
                      [disabled]="!newQuestionText.trim()"
                      class="btn-secondary text-sm px-3 shrink-0">Add</button>
            </div>
          </div>
        </div>

        <!-- Generated Documents Section -->
        @if (generatedDocs.length > 0) {
          <div class="card space-y-3">
            <h2 class="text-base font-semibold text-gray-900">Generated Documents</h2>
            @for (doc of generatedDocs; track doc.id) {
              <div class="border border-gray-200 rounded-lg p-3">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs font-medium uppercase tracking-wide text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                    {{ doc.documentType.replace('_', ' ') }}
                  </span>
                  <span class="text-xs text-gray-400">{{ doc.createdAt | date:'mediumDate' }}</span>
                </div>
                <p class="text-sm text-gray-700 line-clamp-3 whitespace-pre-line">{{ doc.content }}</p>
                <button (click)="expandedDoc = expandedDoc === doc.id ? null : doc.id"
                        class="text-xs text-blue-600 hover:underline mt-1">
                  {{ expandedDoc === doc.id ? 'Collapse' : 'View full' }}
                </button>
                @if (expandedDoc === doc.id) {
                  <div class="mt-2 text-sm text-gray-700 whitespace-pre-line bg-gray-50 rounded p-3">
                    {{ doc.content }}
                  </div>
                }
              </div>
            }
          </div>
        }

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
                  <app-confirm-delete-button
                    buttonClass="text-xs text-red-400 hover:text-red-600"
                    (confirmed)="deleteNote(note.id)">
                  </app-confirm-delete-button>
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
  readonly MapPinIcon = MapPin;
  readonly BriefcaseIcon = Briefcase;
  readonly MonitorIcon = Monitor;
  readonly TrendingUpIcon = TrendingUp;
  readonly BanknoteIcon = Banknote;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private jobsApi = inject(JobsApiService);
  private notesApi = inject(NotesApiService);
  private appsApi = inject(ApplicationsApiService);
  private aiApi = inject(AiApiService);
  private interviewPrepApi = inject(InterviewPrepApiService);
  private remindersApi = inject(RemindersApiService);
  private skillGapApi = inject(SkillGapApiService);

  job: Job | null = null;
  notes: Note[] = [];
  generatedDocs: GeneratedDocument[] = [];
  application: Application | null = null;
  loading = true;

  saved = false;
  saveMessage = '';
  expandedDoc: string | null = null;

  skillGap: SkillGapResult | null = null;
  skillGapLoading = false;

  newNoteContent = '';
  editingNoteId: string | null = null;
  editContent = '';

  // Recruiter outreach state
  recruiterName = '';
  recruiterEmail = '';
  recruiterMessageText = '';
  recruiterReplyText = '';
  generatingOutreach = false;
  savingOutreach = false;
  savingReply = false;
  outreachSaved = false;
  replySaved = false;

  // Reminder state
  reminders: FollowUpReminder[] = [];
  newReminderNote = '';
  newReminderDueAt = '';

  get overdueCount(): number {
    const now = new Date();
    return this.reminders.filter(r => !r.completed && new Date(r.dueAt) < now).length;
  }

  // Interview prep state
  interviewQuestions: InterviewQuestion[] = [];
  generatingQuestions = false;
  generateQuestionsError = '';
  editingStarId: string | null = null;
  editingStarText = '';
  newQuestionText = '';
  newQuestionCategory = 'BEHAVIORAL';

  get preparedCount(): number {
    return this.interviewQuestions.filter(q => q.practiced).length;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    runAction({
      action$: this.jobsApi.getById(id),
      setLoading: value => this.loading = value,
      next: j => {
        this.job = j;
        this.notesApi.getForJob(j.id).subscribe(ns => this.notes = ns);
        this.jobsApi.getDocumentsForJob(j.id).subscribe(docs => this.generatedDocs = docs);
        this.interviewPrepApi.getQuestions(j.id).subscribe(qs => this.interviewQuestions = qs);
        this.appsApi.getAll().subscribe(apps => {
          const app = apps.find(a => a.jobId === j.id) ?? null;
          this.application = app;
          if (app) {
            this.recruiterName = app.recruiterName ?? '';
            this.recruiterEmail = app.recruiterEmail ?? '';
            this.recruiterMessageText = app.recruiterMessage ?? '';
            this.recruiterReplyText = app.recruiterReply ?? '';
            this.remindersApi.getForApplication(app.id).subscribe(rs => this.reminders = rs);
          }
        });
      }
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
        clearTextAfter(value => this.saveMessage = value, 2000);
      });
    } else {
      this.jobsApi.save(this.job.id).subscribe(() => {
        this.saved = true;
        this.saveMessage = 'Job saved!';
        clearTextAfter(value => this.saveMessage = value, 2000);
      });
    }
  }

  apply(): void {
    if (!this.job) return;
    this.router.navigate(['/apply', this.job.id]);
  }

  generateOutreachMessage(): void {
    if (!this.job) return;
    this.generatingOutreach = true;
    this.aiApi.generateDocument({
      jobId: this.job.id,
      documentType: 'RECRUITER_MESSAGE',
    }).subscribe({
      next: doc => {
        this.recruiterMessageText = doc.bodyContent ?? '';
        this.generatingOutreach = false;
      },
      error: () => { this.generatingOutreach = false; },
    });
  }

  saveOutreach(): void {
    if (!this.job) return;
    this.savingOutreach = true;
    this.outreachSaved = false;
    const save$ = this.application
      ? this.appsApi.updateRecruiterInfo(this.application.id, {
          recruiterName: this.recruiterName,
          recruiterEmail: this.recruiterEmail,
          recruiterMessage: this.recruiterMessageText,
        })
      : this.appsApi.create({
          jobId: this.job.id,
          status: 'SAVED',
        });

    save$.subscribe({
      next: app => {
        this.application = app;
        this.savingOutreach = false;
        this.outreachSaved = true;
        clearTextAfter(v => { if (v === '') this.outreachSaved = false; }, 3000);
      },
      error: () => { this.savingOutreach = false; },
    });
  }

  saveReply(): void {
    if (!this.job) return;
    this.savingReply = true;
    this.replySaved = false;

    const doSave = (appId: string) => {
      this.appsApi.updateRecruiterInfo(appId, { recruiterReply: this.recruiterReplyText }).subscribe({
        next: app => {
          this.application = app;
          this.savingReply = false;
          this.replySaved = true;
          clearTextAfter(v => { if (v === '') this.replySaved = false; }, 3000);
        },
        error: () => { this.savingReply = false; },
      });
    };

    if (this.application) {
      doSave(this.application.id);
    } else {
      this.appsApi.create({ jobId: this.job.id, status: 'SAVED' }).subscribe({
        next: app => { this.application = app; doSave(app.id); },
        error: () => { this.savingReply = false; },
      });
    }
  }

  // ── Interview Prep ────────────────────────────────────────────────────────

  generateInterviewQuestions(): void {
    if (!this.job) return;
    this.generatingQuestions = true;
    this.generateQuestionsError = '';
    const description = this.job.descriptionClean ?? '';
    this.interviewPrepApi.generateQuestions(this.job.id, description, 10).subscribe({
      next: qs => {
        this.interviewQuestions = [...this.interviewQuestions, ...qs];
        this.generatingQuestions = false;
      },
      error: () => {
        this.generateQuestionsError = 'Failed to generate questions. Please try again.';
        this.generatingQuestions = false;
      },
    });
  }

  addQuestionManually(): void {
    if (!this.job || !this.newQuestionText.trim()) return;
    this.interviewPrepApi.addQuestion(this.job.id, this.newQuestionText.trim(), this.newQuestionCategory)
      .subscribe(q => {
        this.interviewQuestions = [...this.interviewQuestions, q];
        this.newQuestionText = '';
      });
  }

  togglePracticed(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.updateQuestion(this.job.id, q.id, { practiced: !q.practiced }).subscribe(updated => {
      this.interviewQuestions = this.interviewQuestions.map(item => item.id === updated.id ? updated : item);
    });
  }

  startStarEdit(q: InterviewQuestion): void {
    this.editingStarId = q.id;
    this.editingStarText = q.starAnswer ?? '';
  }

  cancelStarEdit(): void {
    this.editingStarId = null;
    this.editingStarText = '';
  }

  saveStarAnswer(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.updateQuestion(this.job.id, q.id, { starAnswer: this.editingStarText }).subscribe(updated => {
      this.interviewQuestions = this.interviewQuestions.map(item => item.id === updated.id ? updated : item);
      this.cancelStarEdit();
    });
  }

  deleteQuestion(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.deleteQuestion(this.job.id, q.id).subscribe(() => {
      this.interviewQuestions = this.interviewQuestions.filter(item => item.id !== q.id);
    });
  }

  // ── Reminders ─────────────────────────────────────────────────────────────

  addReminder(): void {
    if (!this.application || !this.newReminderDueAt) return;
    const dueAt = new Date(this.newReminderDueAt).toISOString();
    this.remindersApi.create(this.application.id, this.newReminderNote.trim() || 'Follow up', dueAt)
      .subscribe(r => {
        this.reminders = [...this.reminders, r].sort(
          (a, b) => new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime());
        this.newReminderNote = '';
        this.newReminderDueAt = '';
      });
  }

  setReminderIn(days: number): void {
    const d = new Date();
    d.setDate(d.getDate() + days);
    d.setHours(9, 0, 0, 0);
    this.newReminderDueAt = d.toISOString().slice(0, 16);
  }

  completeReminder(r: FollowUpReminder): void {
    if (r.completed) return;
    this.remindersApi.complete(r.id).subscribe(updated => {
      this.reminders = this.reminders.map(item => item.id === updated.id ? updated : item);
    });
  }

  deleteReminder(r: FollowUpReminder): void {
    this.remindersApi.delete(r.id).subscribe(() => {
      this.reminders = this.reminders.filter(item => item.id !== r.id);
    });
  }

  reminderRowClass(r: FollowUpReminder): string {
    if (r.completed) return 'border-gray-100 bg-gray-50 opacity-60';
    const now = new Date();
    const due = new Date(r.dueAt);
    if (due < now) return 'border-red-200 bg-red-50';
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    if (due <= tomorrow) return 'border-yellow-200 bg-yellow-50';
    return 'border-green-100 bg-green-50';
  }

  reminderDateClass(r: FollowUpReminder): string {
    if (r.completed) return 'text-gray-400';
    const now = new Date();
    if (new Date(r.dueAt) < now) return 'text-red-600 font-medium';
    return 'text-gray-500';
  }

  reminderDueLabel(r: FollowUpReminder): string {
    const due = new Date(r.dueAt);
    const now = new Date();
    const diffMs = due.getTime() - now.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays < 0) return `Overdue by ${Math.abs(diffDays)} day${Math.abs(diffDays) !== 1 ? 's' : ''}`;
    if (diffDays === 0) return 'Due today';
    if (diffDays === 1) return 'Due tomorrow';
    return `Due in ${diffDays} days`;
  }

  categoryLabel(cat: string): string {
    return CATEGORY_LABELS[cat] ?? cat;
  }

  categoryColor(cat: string): string {
    return CATEGORY_COLORS[cat] ?? 'bg-gray-100 text-gray-600';
  }

  // ── Notes ─────────────────────────────────────────────────────────────────

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

  // ── Skill Gap ─────────────────────────────────────────────────────────────

  analyzeSkillGap(): void {
    if (!this.job) return;
    this.skillGapLoading = true;
    this.skillGapApi.analyze(this.job.id).subscribe({
      next: result => { this.skillGap = result; this.skillGapLoading = false; },
      error: () => this.skillGapLoading = false,
    });
  }
}
