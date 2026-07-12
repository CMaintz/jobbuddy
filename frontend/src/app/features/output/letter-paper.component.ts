import { Component, ElementRef, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RichTextEditorComponent } from '../resume-builder/shared/rich-text-editor.component';
import { RichTextPipe } from '../resume-builder/shared/rich-text.pipe';
import { DocumentIdentity } from '../../core/models/structured-document.model';
import { LetterTemplate } from './letter-templates';

/**
 * The rendered letter sheet: template styling, identity header, and the body
 * (read-only with optional JD-keyword highlighting, or the in-place editor).
 * The host element is what PDF capture targets — grab it via `el`.
 */
@Component({
  selector: 'app-letter-paper',
  standalone: true,
  imports: [CommonModule, RichTextEditorComponent, RichTextPipe],
  templateUrl: './letter-paper.component.html',
})
export class LetterPaperComponent {
  readonly el = inject(ElementRef<HTMLElement>);
  private sanitizer = inject(DomSanitizer);

  @Input({ required: true }) tpl!: LetterTemplate;
  @Input() identity: DocumentIdentity = {};
  @Input() content = '';
  @Input() editing = false;
  /** JD keywords to highlight in the body when `highlight` is on. */
  @Input() keywords: string[] = [];
  @Input() highlight = false;
  @Output() edited = new EventEmitter<string>();

  get isRich(): boolean {
    return this.content.includes('<');
  }

  get paragraphs(): string[] {
    return this.content.split(/\n{2,}/).map(p => p.trim()).filter(Boolean);
  }

  /** Plain paragraph → safe HTML with optional keyword marks. */
  paraHtml(para: string): SafeHtml {
    const escaped = para
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return this.sanitizer.bypassSecurityTrustHtml(this.markKeywords(escaped));
  }

  /** Rich (TipTap) content with optional keyword marks. */
  richHtml(): string {
    return this.markKeywords(this.content);
  }

  /** Wraps JD keywords in <mark> while leaving HTML tags untouched. */
  private markKeywords(html: string): string {
    if (!this.highlight || this.keywords.length === 0) return html;
    const pattern = new RegExp(
      `(${this.keywords.map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi');
    return html.split(/(<[^>]+>)/g)
      .map(seg => seg.startsWith('<') ? seg : seg.replace(pattern, '<mark class="jb-kw">$1</mark>'))
      .join('');
  }
}
