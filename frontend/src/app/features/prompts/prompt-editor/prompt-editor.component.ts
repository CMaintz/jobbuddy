import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PromptApiService } from '../../../core/api/prompt.api';
import { PromptCategory } from '../../../core/models/prompt-template.model';
import { FormActionsComponent } from '../../../shared/components/ui/form-actions.component';
import { runAction } from '../../../shared/utils/async-ui';

@Component({
  selector: 'app-prompt-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormActionsComponent],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <a routerLink="/prompts" class="text-sm text-blue-600 hover:underline">← Back to templates</a>
      <h1 class="text-3xl font-bold text-gray-900">New Template</h1>

      <div class="card">
        @if (error) {
          <div class="bg-red-50 text-red-700 rounded-md p-3 mb-4 text-sm">{{ error }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label">Name <span class="text-red-500">*</span></label>
            <input type="text" formControlName="name" class="input" placeholder="Cover Letter Template" />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Category</label>
              <select formControlName="category" class="input">
                <option value="">— none —</option>
                <option value="COVER_LETTER">Cover Letter</option>
                <option value="APPLICATION">Application</option>
                <option value="RECRUITER_MESSAGE">Recruiter Message</option>
                <option value="CV_ANALYSIS">CV Analysis</option>
                <option value="GENERAL">General</option>
              </select>
            </div>
            <div class="flex items-end pb-1">
              <label class="flex items-center gap-2 cursor-pointer">
                <input type="checkbox" formControlName="isPublic" class="rounded" />
                <span class="text-sm text-gray-700">Make public</span>
              </label>
            </div>
          </div>

          <div>
            <label class="label">Description</label>
            <input type="text" formControlName="description" class="input"
                   placeholder="Brief description of this template" />
          </div>

          <div>
            <label class="label">System Prompt</label>
            <textarea formControlName="systemPrompt" class="input min-h-24 font-mono text-xs"
                      placeholder="You are a professional job application writer..."></textarea>
          </div>

          <div>
            <label class="label">User Prompt <span class="text-red-500">*</span></label>
            <textarea formControlName="userPrompt" class="input min-h-32 font-mono text-xs"
                      placeholder="Write a cover letter for the following job:&#10;{job_description}&#10;&#10;Using this CV:&#10;{cv_content}"></textarea>
            <p class="text-xs text-gray-400 mt-1">
              Available variables: {{ '{job_description}, {cv_content}, {writing_style}' }}
            </p>
          </div>

          <div>
            <label class="label">Output Constraints</label>
            <input type="text" formControlName="outputConstraints" class="input"
                   placeholder="Max 400 words, professional tone..." />
          </div>

          <app-form-actions
            [disabled]="form.invalid || loading"
            [saveLabel]="loading ? 'Saving...' : 'Save Template'"
            (cancel)="cancel()">
          </app-form-actions>
        </form>
      </div>
    </div>
  `
})
export class PromptEditorComponent {
  private fb = inject(FormBuilder);
  private api = inject(PromptApiService);
  private router = inject(Router);

  loading = false;
  error = '';

  form = this.fb.group({
    name: ['', Validators.required],
    category: ['' as PromptCategory | ''],
    description: [''],
    systemPrompt: [''],
    userPrompt: ['', Validators.required],
    outputConstraints: [''],
    isPublic: [false]
  });

  submit(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    runAction({
      action$: this.api.create({
        name: v.name!,
        category: (v.category || undefined) as PromptCategory | undefined,
        description: v.description || undefined,
        systemPrompt: v.systemPrompt || undefined,
        userPrompt: v.userPrompt!,
        outputConstraints: v.outputConstraints || undefined,
        isPublic: v.isPublic ?? false
      }),
      setLoading: value => this.loading = value,
      setError: message => this.error = message,
      errorMessage: error => (error as any)?.error?.message || 'Save failed',
      next: () => this.router.navigate(['/prompts']),
    });
  }

  cancel(): void {
    this.router.navigate(['/prompts']);
  }
}
