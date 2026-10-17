import { Component, EventEmitter, Input, Output, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiApiService } from '../../../core/api/ai.api';
import { PromptApiService } from '../../../core/api/prompt.api';
import { sectionCategory, templatesForSection } from '../../../core/models/cv-section-category';
import { PromptTemplate } from '../../../core/models/prompt-template.model';
import { DiffViewerComponent } from '../../../shared/components/diff-viewer/diff-viewer.component';
import { htmlTextContent } from '../../../shared/utils/sanitize-html';
import { PromptExplainerComponent } from '../../../shared/components/prompt-explainer/prompt-explainer.component';
import { toHtml } from './rich-text-editor.component';

const SUGGESTIONS = [
  'Quantify the achievements with concrete numbers where the text implies them',
  'Tighten the wording — same facts, fewer words',
  'Lead with impact and results instead of responsibilities',
  'Fix grammar and awkward phrasing only',
];

const SUGGESTION_LABELS = ['resumeBuilder.refine.label.quantify', 'resumeBuilder.refine.label.tighten', 'resumeBuilder.refine.label.impact', 'resumeBuilder.refine.label.grammar'];

/** The chosen template carries the actual guidance; this only tells the model to apply it. */
const SECTION_STYLE_INSTRUCTION = 'Rewrite this text following the section guidance.';

/**
 * Small AI popover for a single resume field: the section's writing-style templates, suggestion
 * chips and a free-text ask. Takes the field's current content (HTML or plain), sends plain text
 * to /ai/refine, and emits the result back as rich-text HTML.
 */
@Component({
  selector: 'app-ai-refine-menu',
  imports: [FormsModule, TranslateModule, DiffViewerComponent, PromptExplainerComponent],
  templateUrl: './ai-refine-menu.component.html',
  styleUrls: ['./ai-refine-menu.component.css'],
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AiRefineMenuComponent {
  private aiApi = inject(AiApiService);
  private promptApi = inject(PromptApiService);
  private translate = inject(TranslateService);

  /** Current field content — HTML from the rich editor or plain text. */
  @Input() content = '';
  /** Optional job description so refinements are job-aware. */
  @Input() jobDescription?: string;
  /** Optional CV section key; the refine then follows that section's writing-style template. */
  @Input() sectionKey?: string;
  /** Emit plain text instead of rich-text HTML (master CV fields are plain by design). */
  @Input() plainOutput = false;
  /** Emits the refined content — HTML by default, plain text with plainOutput. */
  @Output() refined = new EventEmitter<string>();

  suggestions = SUGGESTIONS;
  labels = SUGGESTION_LABELS;

  open = signal(false);
  busy = signal(false);
  error = signal('');
  preview = signal<string | null>(null);
  sectionTemplates = signal<PromptTemplate[]>([]);
  /** The style whose explanation is expanded; null keeps the popover compact. */
  explainedId = signal<string | null>(null);
  customAsk = '';

  toggleOpen(): void {
    const opening = !this.open();
    this.open.set(opening);
    if (opening) this.loadSectionTemplates();
  }

  toggleExplainer(template: PromptTemplate): void {
    this.explainedId.set(this.explainedId() === template.id ? null : template.id);
  }

  explainedTemplate(): PromptTemplate | undefined {
    return this.sectionTemplates().find(t => t.id === this.explainedId());
  }

  runWithTemplate(template: PromptTemplate): void {
    this.run(SECTION_STYLE_INSTRUCTION, template.id);
  }

  run(prompt: string, promptTemplateId?: string): void {
    const text = this.plainContent();
    if (!prompt?.trim() || !text.trim() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.aiApi.refine({
      currentContent: text,
      userMessage: prompt.trim(),
      jobDescription: this.jobDescription || undefined,
      sectionKey: this.sectionKey || undefined,
      promptTemplateId,
    }).subscribe({
      next: resp => {
        this.busy.set(false);
        this.preview.set(resp.refinedContent);
        this.customAsk = '';
      },
      error: () => {
        this.busy.set(false);
        this.error.set(this.translate.instant('resumeBuilder.refine.failed'));
      }
    });
  }

  apply(): void {
    const result = this.preview();
    if (result) this.refined.emit(this.plainOutput ? result : toHtml(result));
    this.preview.set(null);
    this.open.set(false);
  }

  /** Plain-text view of the field content (also feeds the diff preview). */
  plainContent(): string {
    if (!this.content.includes('<')) return this.content;
    return htmlTextContent(this.content.replace(/<\/(p|li)>/g, '</$1>\n')).trim();
  }

  /**
   * Fetched on open rather than on creation: an editor renders one menu per field and most are
   * never opened. A failure just leaves the style group out; the suggestions still work.
   */
  private loadSectionTemplates(): void {
    if (!sectionCategory(this.sectionKey)) return;
    this.promptApi.getAllCached().subscribe({
      next: all => this.sectionTemplates.set(templatesForSection(all, this.sectionKey)),
      error: () => this.sectionTemplates.set([]),
    });
  }
}
