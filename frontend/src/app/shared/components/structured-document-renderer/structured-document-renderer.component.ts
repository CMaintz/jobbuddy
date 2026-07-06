import { CommonModule } from '@angular/common';
import { Component, Input, ViewEncapsulation } from '@angular/core';
import { StructuredDocument } from '../../../core/models/structured-document.model';
import { DocumentHeaderComponent } from './document-header.component';
import { DocumentSectionComponent } from './document-section.component';

@Component({
  selector: 'app-structured-document-renderer',
  standalone: true,
  imports: [CommonModule, DocumentHeaderComponent, DocumentSectionComponent],
  encapsulation: ViewEncapsulation.None,
  styleUrls: ['./structured-document-renderer.component.css'],
  templateUrl: './structured-document-renderer.component.html'
})
export class StructuredDocumentRendererComponent {
  @Input({ required: true }) document!: StructuredDocument;

  get isCv(): boolean {
    return this.document?.documentType === 'CV';
  }

  get sheetClasses(): string[] {
    if (!this.document) return [];
    return [
      this.document.templateId,
      this.document.exportMode === 'ATS' || this.document.templateId.endsWith('-ats') ? 'ats' : 'designed'
    ];
  }

  get showProfileImage(): boolean {
    return !!this.document?.options?.showProfileImage && this.document?.exportMode !== 'ATS';
  }

  get themeStyles(): Record<string, string> {
    const theme = this.document?.options?.theme;
    const scale = theme?.fontScale ?? 'normal';
    return {
      '--doc-primary': this.safeColor(theme?.primaryColor, '#18324a'),
      '--doc-accent': this.safeColor(theme?.accentColor, '#cbd8e3'),
      '--doc-font-family': this.safeFont(theme?.fontFamily),
      '--doc-base-size': scale === 'small' ? '13px' : scale === 'large' ? '15px' : '14px',
      '--doc-title-size': scale === 'small' ? '28px' : scale === 'large' ? '36px' : '32px'
    };
  }

  get bodyParagraphs(): string[] {
    return (this.document?.bodyContent ?? '')
      .split(/\n\s*\n|\n/g)
      .map(part => part.trim())
      .filter(Boolean);
  }

  private safeColor(value: string | undefined, fallback: string): string {
    return value && /^#[0-9a-fA-F]{6}$/.test(value) ? value : fallback;
  }

  private safeFont(value: string | undefined): string {
    const allowed = ['Arial', 'Inter', 'Georgia', 'Calibri', 'Times New Roman'];
    return allowed.includes(value ?? '') ? `${value}, sans-serif` : 'Arial, sans-serif';
  }
}
