import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DocumentTemplateOption } from '../../../../core/models/structured-document.model';
import { LanguageOption } from './application-generator.types';
import { AiApiService } from '../../../../core/api/ai.api';

@Component({
  selector: 'app-generation-config-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './generation-config-form.component.html'
})
export class GenerationConfigFormComponent {
  private aiApi = inject(AiApiService);

  @Input({ required: true }) form!: FormGroup;
  @Input() availableStructuredTemplates: DocumentTemplateOption[] = [];
  @Input() languages: LanguageOption[] = [];
  @Input() loading = false;
  @Input() jobTitle = '';

  @Output() generate = new EventEmitter<void>();
  @Output() generateApplicationSet = new EventEmitter<void>();

  motivationOpen = signal(false);
  generatingMotivation = signal(false);

  generateMotivation(): void {
    this.generatingMotivation.set(true);
    const jobDescription = this.form.value.jobDescription || undefined;
    this.aiApi.refine({
      currentContent: '',
      userMessage: 'Draft a short personal motivation statement (2-3 sentences) for why I want this role based on the job description.',
      jobDescription,
    }).subscribe({
      next: r => {
        this.form.patchValue({ motivationText: r.refinedContent });
        this.generatingMotivation.set(false);
      },
      error: () => this.generatingMotivation.set(false),
    });
  }
}
