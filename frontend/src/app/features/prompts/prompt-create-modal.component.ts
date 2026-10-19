import { Component, EventEmitter, Input, Output, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { PromptApiService } from '../../core/api/prompt.api';
import { PromptCategory } from '../../core/models/prompt-template.model';
import { isSectionCategory } from '../../core/models/cv-section-category';
import { PromptKind, saveErrorKey } from './prompt-kinds';

/** The "new prompt" form. Owns its draft so the library only hears about a finished prompt. */
@Component({
  selector: 'app-prompt-create-modal',
  imports: [FormsModule, TranslateModule, JbButtonComponent, JbModalComponent, TagInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './prompt-create-modal.component.html'
})
export class PromptCreateModalComponent {
  private promptApi = inject(PromptApiService);

  @Input() kinds: PromptKind[] = [];
  @Output() created = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  creating = signal(false);
  /** Translation key of the last failure, shown inside the form so the draft is not lost. */
  errorKey = signal('');

  name = '';
  category: PromptCategory = 'APPLICATION';
  description = '';
  systemPrompt = '';
  body = '';
  isPublic = false;
  tags: string[] = [];

  readonly isSectionCategory = isSectionCategory;

  create(): void {
    if (!this.name.trim() || !this.body.trim() || this.creating()) return;
    this.creating.set(true);
    this.errorKey.set('');
    this.promptApi.create({
      name: this.name.trim(),
      category: this.category,
      description: this.description.trim() || undefined,
      systemPrompt: isSectionCategory(this.category) ? undefined : this.systemPrompt.trim() || undefined,
      userPrompt: this.body.trim(),
      isPublic: this.isPublic,
      tags: this.tags,
    }).subscribe({
      next: () => {
        this.creating.set(false);
        this.created.emit();
      },
      error: err => {
        this.creating.set(false);
        this.errorKey.set(saveErrorKey(err, 'prompts.toast.createFailed'));
      }
    });
  }
}
