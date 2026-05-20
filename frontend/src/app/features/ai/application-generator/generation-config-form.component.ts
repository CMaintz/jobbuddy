import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DocumentTemplateOption } from '../../../core/models/structured-document.model';
import { LanguageOption } from './application-generator.types';

@Component({
  selector: 'app-generation-config-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="card">
      <form [formGroup]="form" (ngSubmit)="generate.emit()" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="label">Document Type</label>
          <select formControlName="documentType" class="input">
            <option value="COVER_LETTER">Cover Letter</option>
            <option value="APPLICATION_TEXT">Application Text</option>
            <option value="RECRUITER_MESSAGE">Recruiter Message</option>
            <option value="FOLLOW_UP_MESSAGE">Follow-up Message</option>
            <option value="CV">Angled CV</option>
          </select>
        </div>

        <div>
          <label class="label">Layout Template</label>
          <select formControlName="structuredTemplateId" class="input">
            @for (template of availableStructuredTemplates; track template.id) {
              <option [value]="template.id">{{ template.familyName ? template.familyName + ' · ' : '' }}{{ template.label }}</option>
            }
          </select>
        </div>

        <div class="md:col-span-2 flex items-center gap-3">
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" formControlName="showProfileImage" class="rounded border-gray-300" />
            <span class="text-sm text-gray-700">Show profile image in designed templates</span>
          </label>
          <span class="text-xs text-gray-400">ATS templates always hide images.</span>
        </div>

        <div>
          <label class="label">Primary Color</label>
          <input type="color" formControlName="primaryColor" class="h-10 w-full rounded-md border border-gray-300 bg-white px-2" />
        </div>

        <div>
          <label class="label">Accent Color</label>
          <input type="color" formControlName="accentColor" class="h-10 w-full rounded-md border border-gray-300 bg-white px-2" />
        </div>

        <div>
          <label class="label">Font</label>
          <select formControlName="fontFamily" class="input">
            <option value="Arial">Arial</option>
            <option value="Inter">Inter</option>
            <option value="Calibri">Calibri</option>
            <option value="Georgia">Georgia</option>
            <option value="Times New Roman">Times New Roman</option>
          </select>
        </div>

        <div>
          <label class="label">Font Size</label>
          <select formControlName="fontScale" class="input">
            <option value="small">Small</option>
            <option value="normal">Normal</option>
            <option value="large">Large</option>
          </select>
        </div>

        <div>
          <label class="label">Language</label>
          <select formControlName="targetLanguage" class="input">
            @for (lang of languages; track lang.code) {
              <option [value]="lang.code">{{ lang.label }}</option>
            }
          </select>
        </div>

        <div class="md:col-span-2">
          <label class="label">Custom Instructions (optional)</label>
          <textarea formControlName="customInstructions" class="input" rows="2"
                    placeholder="e.g. Keep it under 300 words, emphasise leadership experience..."></textarea>
        </div>

        <div class="md:col-span-2">
          <label class="label">
            Indsæt jobopslag
            @if (jobTitle) {
              <span class="ml-2 text-xs font-normal text-green-600">✓ {{ jobTitle }}</span>
            }
          </label>
          <textarea formControlName="jobDescription" class="input" rows="5"
                    placeholder="Indsæt jobopslaget her — eller vælg et job via ?jobId=... parameteret..."></textarea>
        </div>

        <div class="md:col-span-2 flex flex-col sm:flex-row gap-2">
          <button type="submit" [disabled]="form.invalid || loading" class="btn-primary flex-1">
            {{ loading ? 'Generating...' : 'Generate' }}
          </button>
          <button type="button"
                  (click)="generateApplicationSet.emit()"
                  [disabled]="form.invalid || loading || form.value.documentType === 'CV'"
                  class="btn-secondary flex-1">
            Generate Application + CV
          </button>
        </div>
      </form>
    </div>
  `
})
export class GenerationConfigFormComponent {
  @Input({ required: true }) form!: FormGroup;
  @Input() availableStructuredTemplates: DocumentTemplateOption[] = [];
  @Input() languages: LanguageOption[] = [];
  @Input() loading = false;
  @Input() jobTitle = '';

  @Output() generate = new EventEmitter<void>();
  @Output() generateApplicationSet = new EventEmitter<void>();
}
