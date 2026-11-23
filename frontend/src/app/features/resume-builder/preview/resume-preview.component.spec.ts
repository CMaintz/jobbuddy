import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AtsPdfService } from '../../../shared/services/ats-pdf.service';
import { PdfExportService } from '../services/pdf-export.service';
import { ResumeDraftApiService } from '../services/resume-draft-api.service';
import { ResumeStateService } from '../services/resume-state.service';
import { ResumePreviewComponent } from './resume-preview.component';

describe('ResumePreviewComponent page budget', () => {
  let fixture: ComponentFixture<ResumePreviewComponent>;
  let state: ResumeStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ResumePreviewComponent, TranslateModule.forRoot()],
      providers: [
        { provide: PdfExportService, useValue: { download: () => Promise.resolve(3) } },
        { provide: AtsPdfService, useValue: { downloadResume: () => Promise.resolve(3) } },
        { provide: ResumeDraftApiService, useValue: {} },
      ],
    });
    state = TestBed.inject(ResumeStateService);
    fixture = TestBed.createComponent(ResumePreviewComponent);
    fixture.detectChanges();
  });

  function warning(): string {
    const el = (fixture.nativeElement as HTMLElement).querySelector('jb-page-budget-warning');
    return el?.textContent?.trim() ?? '';
  }

  async function exportCv(): Promise<void> {
    await fixture.componentInstance.downloadAtsPdf();
    fixture.detectChanges();
  }

  it('hides the warning once the CV text changes, since it described the old file', async () => {
    await exportCv();

    state.updatePersonalInfo({ summary: 'Kortere resumé' });
    fixture.detectChanges();

    expect(warning()).toBe('');
  });

  it('hides the warning when a layout setting changes the page count', async () => {
    await exportCv();

    state.updateSettings({ fontSize: 'sm' });
    fixture.detectChanges();

    expect(warning()).toBe('');
  });

  it('warns again after the edited CV is downloaded and still runs over', async () => {
    await exportCv();
    state.updatePersonalInfo({ summary: 'Kortere resumé' });
    fixture.detectChanges();

    await exportCv();

    expect(warning()).toContain('pageBudget.cv.over');
  });
});
