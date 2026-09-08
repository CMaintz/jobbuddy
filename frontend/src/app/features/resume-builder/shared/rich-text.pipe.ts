import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { toHtml } from './rich-text-editor.component';

/**
 * Renders resume rich-text content (TipTap HTML) in preview layouts.
 * Legacy plain-text content is wrapped in paragraphs. Trusting the HTML is
 * acceptable here: it is authored by the user in their own workspace and
 * Angular's sanitizer would otherwise strip the inline font/colour styles.
 */
@Pipe({ name: 'richText', standalone: true })
export class RichTextPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);

  transform(value: string | undefined | null): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(toHtml(value ?? ''));
  }
}
