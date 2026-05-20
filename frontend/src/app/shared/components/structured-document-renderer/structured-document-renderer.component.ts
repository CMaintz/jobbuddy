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
  styles: [`
    :host { display: block; }
    .sheet {
      width: min(100%, 210mm);
      min-height: 297mm;
      margin: 0 auto;
      background: white;
      color: #172033;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
      overflow: hidden;
      font-family: var(--doc-font-family);
      font-size: var(--doc-base-size);
    }
    .topbar {
      display: grid;
      grid-template-columns: auto 1fr auto;
      gap: 18px;
      align-items: center;
      padding: 34px 42px 28px;
      background: var(--doc-primary);
      color: white;
    }
    .avatar {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      overflow: hidden;
      display: grid;
      place-items: center;
      background: #eef6ff;
      color: var(--doc-primary);
      font-size: 24px;
      font-weight: 800;
      flex: 0 0 auto;
    }
    .avatar img { width: 100%; height: 100%; object-fit: cover; }
    h1, h2, h3, p { margin: 0; }
    .person h1 { font-size: var(--doc-title-size); line-height: 1.05; font-weight: 800; letter-spacing: 0; }
    .person p { margin-top: 6px; color: #cce0f0; font-size: 14px; }
    .contact {
      display: flex;
      flex-direction: column;
      gap: 4px;
      text-align: right;
      font-size: 12px;
      color: #eef6ff;
    }
    .content { padding: 34px 42px 42px; }
    .section { break-inside: avoid; margin-bottom: 22px; }
    .section h2 {
      font-size: 13px;
      text-transform: uppercase;
      letter-spacing: 0;
      color: var(--doc-primary);
      border-bottom: 1px solid var(--doc-accent);
      padding-bottom: 5px;
      margin-bottom: 10px;
    }
    .section-body { line-height: 1.6; color: #263246; }
    .item { break-inside: avoid; margin-bottom: 14px; }
    .item-head { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; }
    .item h3 { font-size: 15px; font-weight: 800; color: #101827; }
    .subtitle, .dates, .description, .tech, .links { color: #526071; }
    .subtitle { margin-top: 2px; font-size: 13px; }
    .dates { white-space: nowrap; font-size: 12px; }
    .description { margin-top: 6px; line-height: 1.55; font-size: 13px; color: #263246; }
    .bullets { margin: 8px 0 0 18px; padding: 0; color: #263246; font-size: 13px; line-height: 1.5; }
    .bullets li { margin-bottom: 3px; }
    .skills { display: flex; flex-wrap: wrap; gap: 7px; list-style: none; margin: 0; padding: 0; }
    .skills li { border: 1px solid var(--doc-accent); background: #f7fafc; border-radius: 4px; padding: 4px 8px; font-size: 12px; }
    .tech, .links { margin-top: 7px; font-size: 12px; }
    .body p { margin-bottom: 12px; line-height: 1.75; color: #263246; }

    .ats {
      box-shadow: none;
      padding: 38px 44px;
      font-family: Arial, sans-serif;
    }
    .ats .topbar {
      display: block;
      padding: 0 0 10px;
      background: white;
      color: #111827;
      border-bottom: 1px solid #111827;
    }
    .ats .avatar { display: none; }
    .ats .person h1 { font-size: 26px; color: #111827; }
    .ats .person p { color: #333; font-size: 14px; }
    .ats .contact {
      display: block;
      margin-top: 6px;
      text-align: left;
      color: #222;
    }
    .ats .contact span::after { content: " | "; }
    .ats .contact span:last-child::after { content: ""; }
    .ats .content { padding: 18px 0 0; }
    .ats .section h2 { color: #111827; border-color: #d1d5db; }
    .ats .skills { display: block; columns: 2; list-style: disc; padding-left: 18px; }
    .ats .skills li { border: 0; background: transparent; padding: 0; }

    .cv-compact-tech .content { padding-top: 26px; }
    .cv-compact-tech .section { margin-bottom: 15px; }
    .cv-compact-tech .topbar { background: #20343a; }
    .cv-compact-tech .skills li { background: #eef7f5; border-color: #b8d6cf; }

    .cv-executive .topbar { background: #243041; }
    .cv-executive .section h2 { color: #243041; border-color: #b8c0cc; }
    .cv-executive .person h1 { font-family: Georgia, serif; font-weight: 700; }

    .cv-minimal-scandinavian .topbar {
      background: white;
      color: #172033;
      border-bottom: 2px solid #172033;
    }
    .cv-minimal-scandinavian .person p,
    .cv-minimal-scandinavian .contact { color: #526071; }
    .cv-minimal-scandinavian .avatar { background: #edf2f4; color: #172033; }

    .application-formal .topbar { background: white; color: #172033; border-bottom: 1px solid #172033; }
    .application-formal .person h1 { font-family: Georgia, serif; font-weight: 700; }
    .application-formal .person p,
    .application-formal .contact { color: #526071; }
    .application-ats { box-shadow: none; }

    @media (max-width: 760px) {
      .sheet { min-height: auto; }
      .topbar { grid-template-columns: auto 1fr; padding: 24px; }
      .contact { grid-column: 1 / -1; text-align: left; }
      .content { padding: 24px; }
      .item-head { display: block; }
      .dates { margin-top: 3px; }
    }

    @media print {
      .sheet { width: 210mm; min-height: 297mm; box-shadow: none; }
      .section, .item { break-inside: avoid; }
    }
  `],
  template: `
    @if (document) {
      <article class="sheet" [ngClass]="sheetClasses" [ngStyle]="themeStyles">
        <app-document-header
          [identity]="document.identity"
          [showProfileImage]="showProfileImage">
        </app-document-header>

        <main class="content">
          @if (isCv) {
            @for (section of document.sections; track section.id) {
              <app-document-section [section]="section"></app-document-section>
            }
          } @else {
            <div class="body">
              @for (paragraph of bodyParagraphs; track paragraph) {
                <p>{{ paragraph }}</p>
              }
            </div>
          }
        </main>
      </article>
    }
  `
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
