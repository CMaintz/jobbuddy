import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PromptApiService } from '../../../../core/api/prompt.api';
import { PromptCategory } from '../../../../core/models/prompt-template.model';
import { FormActionsComponent } from '../../../../shared/components/ui/form-actions.component';
import { runAction } from '../../../../shared/utils/async-ui';

@Component({
  selector: 'app-prompt-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormActionsComponent],
  templateUrl: './prompt-editor.component.html'
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
