import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { DocumentApiService } from '../../core/api/document.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { AtsPdfService } from '../../shared/services/ats-pdf.service';
import { PdfExportService } from '../resume-builder/services/pdf-export.service';
import { ApplicationOutputComponent } from './application-output.component';
import { LetterPaperComponent } from './letter-paper.component';
import { LetterExportWarningComponent } from './letter-export-warning.component';

describe('ApplicationOutputComponent page budget', () => {
  let fixture: ComponentFixture<ApplicationOutputComponent>;

  const letter: GeneratedDocument = {
    id: 'd1', userId: 'u1', documentType: 'APPLICATION_TEXT', createdAt: '2026-11-01T10:00:00Z',
    content: 'Første afsnit.\n\nAndet afsnit.',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationOutputComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: {
          paramMap: convertToParamMap({ id: 'a1' }), queryParamMap: convertToParamMap({}) } } },
        { provide: ApplicationsApiService, useValue: { getById: () => of({ id: 'a1', jobId: 'j1' }) } },
        { provide: JobsApiService, useValue: {
          getById: () => of({ id: 'j1' }), getDocumentsForJob: () => of([letter]) } },
        { provide: DocumentApiService, useValue: { getQualityScores: () => of([]) } },
        { provide: AiApiService, useValue: {} },
        { provide: PdfExportService, useValue: { download: () => Promise.resolve(2) } },
        { provide: AtsPdfService, useValue: { downloadLetter: () => Promise.resolve(2) } },
      ],
    });
    fixture = TestBed.createComponent(ApplicationOutputComponent);
    fixture.detectChanges();
  });

  function warning(): string {
    const el = (fixture.nativeElement as HTMLElement).querySelector('jb-page-budget-warning');
    return el?.textContent?.trim() ?? '';
  }

  /**
   * Downloads the letter the way the user does. It has never passed the letter gate, so the export
   * warning holds the download until she picks "download anyway".
   */
  async function exportLetter(): Promise<void> {
    fixture.componentInstance.downloadAtsPdf();
    fixture.detectChanges();
    fixture.debugElement.query(By.directive(LetterExportWarningComponent))
      .componentInstance.exportAnyway.emit();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function editLetter(html: string): void {
    fixture.debugElement.query(By.directive(LetterPaperComponent))
      .componentInstance.edited.emit(html);
    fixture.detectChanges();
  }

  it('hides the warning once the letter is edited, since it described the old file', async () => {
    await exportLetter();
    expect(warning()).toContain('pageBudget.letter.over');

    editLetter('<p>Én kortere tekst.</p>');

    expect(warning()).toBe('');
  });

  it('hides the warning when another letter design is picked, which reflows the page', async () => {
    await exportLetter();
    expect(warning()).toContain('pageBudget.letter.over');

    fixture.componentInstance.template.set('classic');
    fixture.detectChanges();

    expect(warning()).toBe('');
  });

  it('warns again after the edited letter is downloaded and still runs over', async () => {
    await exportLetter();
    editLetter('<p>Én kortere tekst.</p>');

    await exportLetter();

    expect(warning()).toContain('pageBudget.letter.over');
  });
});
