import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { sanitizeRichText } from '../../../shared/utils/sanitize-html';
import { toHtml } from './rich-text-editor.component';

/**
 * Renders rich-text content (TipTap HTML, AI output, crawled text) in preview layouts.
 * Plain text, which is what generation returns, is wrapped in paragraphs. The HTML is reduced to
 * the editor's formatting allowlist first; only that output is trusted, because Angular's own
 * sanitizer would strip the inline font/colour styles the editor writes.
 */
@Pipe({ name: 'richText' })
export class RichTextPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);

  transform(value: string | undefined | null): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(sanitizeRichText(toHtml(value ?? '')));
  }
}
