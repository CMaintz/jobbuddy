import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AiApiService } from '../../../core/api/ai.api';
import { DiffViewerComponent } from '../../../shared/components/diff-viewer/diff-viewer.component';
import { toHtml } from './rich-text-editor.component';

const SUGGESTIONS = [
  'Quantify the achievements with concrete numbers where the text implies them',
  'Tighten the wording — same facts, fewer words',
  'Lead with impact and results instead of responsibilities',
  'Fix grammar and awkward phrasing only',
];

const SUGGESTION_LABELS = ['resumeBuilder.refine.label.quantify', 'resumeBuilder.refine.label.tighten', 'resumeBuilder.refine.label.impact', 'resumeBuilder.refine.label.grammar'];

/**
 * Small AI popover for a single resume field: suggestion chips + free-text ask.
 * Takes the field's current content (HTML or plain), sends plain text to
 * /ai/refine, and emits the result back as rich-text HTML.
 */
@Component({
  selector: 'app-ai-refine-menu',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, DiffViewerComponent],
  template: `
    <div class="relative inline-block">
      <button type="button" class="airm-trigger" (click)="open.set(!open())" [title]="'resumeBuilder.refine.improveTitle' | translate">
        ✦ {{ 'resumeBuilder.refine.ai' | translate }}
      </button>
      @if (open()) {
        <div class="fixed inset-0 z-[90]" (click)="open.set(false)"></div>
        <div class="airm-panel">
          @if (!preview()) {
            <div class="airm-label">{{ 'resumeBuilder.refine.improveText' | translate }}</div>
            @for (label of labels; track label; let i = $index) {
              <button type="button" class="airm-item" [disabled]="busy()" (click)="run(suggestions[i])">{{ label | translate }}</button>
            }
            <div class="airm-custom">
              <input [(ngModel)]="customAsk" [disabled]="busy()" [placeholder]="'resumeBuilder.refine.askPlaceholder' | translate"
                (keydown.enter)="run(customAsk)" />
            </div>
            @if (busy()) {
              <div class="airm-busy">{{ 'resumeBuilder.refine.refining' | translate }}</div>
            }
            @if (error()) {
              <div class="airm-error">{{ error() }}</div>
            }
          } @else {
            <div class="airm-label">{{ 'resumeBuilder.refine.suggestedChange' | translate }}</div>
            <div class="airm-preview">
              <jb-diff-viewer [before]="plainContent()" [after]="preview()!" />
            </div>
            <div class="airm-actions">
              <button type="button" class="airm-item" (click)="preview.set(null)">{{ 'resumeBuilder.refine.discard' | translate }}</button>
              <button type="button" class="airm-item airm-apply" (click)="apply()">{{ 'resumeBuilder.refine.apply' | translate }}</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .airm-trigger { padding:2px 8px; font-size:10.5px; font-weight:600; border-radius:99px; cursor:pointer;
      background:var(--jb-accent-soft); border:1px solid var(--jb-accent-border); color:var(--jb-accent-2); }
    .airm-panel { position:absolute; right:0; top:calc(100% + 4px); z-index:100; width:300px; padding:8px;
      background:var(--jb-surface); border:1px solid var(--jb-border-strong); border-radius:8px;
      box-shadow:0 14px 40px rgba(0,0,0,0.35); display:flex; flex-direction:column; gap:4px; }
    .airm-label { font-size:9.5px; text-transform:uppercase; letter-spacing:0.08em; color:var(--jb-text-dim); padding:0 2px 2px; }
    .airm-item { text-align:left; padding:5px 8px; font-size:11.5px; border-radius:5px; cursor:pointer;
      background:var(--jb-surface-2); border:1px solid var(--jb-border); color:var(--jb-text); }
    .airm-item:hover { border-color:var(--jb-border-strong); }
    .airm-item:disabled { opacity:0.5; cursor:default; }
    .airm-apply { background:var(--jb-accent-soft); border-color:var(--jb-accent-border); color:var(--jb-accent-2); font-weight:600; }
    .airm-custom input { width:100%; padding:5px 8px; font-size:11.5px; border-radius:5px;
      background:var(--jb-surface-2); border:1px solid var(--jb-border); color:var(--jb-text); }
    .airm-busy { font-size:11px; color:var(--jb-text-dim); padding:2px; }
    .airm-error { font-size:11px; color:var(--jb-danger); padding:2px; }
    .airm-preview { font-size:11.5px; line-height:1.5; color:var(--jb-text-mid); max-height:180px; overflow:auto;
      padding:6px 8px; background:var(--jb-surface-2); border:1px solid var(--jb-border); border-radius:5px; white-space:pre-line; }
    .airm-actions { display:flex; gap:4px; justify-content:flex-end; }
  `],
})
export class AiRefineMenuComponent {
  private aiApi = inject(AiApiService);
  private translate = inject(TranslateService);

  /** Current field content — HTML from the rich editor or plain text. */
  @Input() content = '';
  /** Optional job description so refinements are job-aware. */
  @Input() jobDescription?: string;
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
  customAsk = '';

  run(prompt: string): void {
    const text = this.plainContent();
    if (!prompt?.trim() || !text.trim() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.aiApi.refine({
      currentContent: text,
      userMessage: prompt.trim(),
      jobDescription: this.jobDescription || undefined,
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
    const div = document.createElement('div');
    div.innerHTML = this.content.replace(/<\/(p|li)>/g, '</$1>\n');
    return (div.textContent ?? '').trim();
  }
}
