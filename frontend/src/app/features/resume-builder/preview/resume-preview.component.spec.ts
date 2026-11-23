import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { AtsPdfService } from '../../../shared/services/ats-pdf.service';
import { DocumentRenderApiService } from '../../../core/api/document-render.api';
import { ResumeDesign } from '../../../core/models/resume-design.model';
import { ResumeDraftApiService } from '../services/resume-draft-api.service';
import { ResumeStateService } from '../services/resume-state.service';
import { ResumePreviewComponent } from './resume-preview.component';

/** Longer than the preview's debounce, so the render it waits for has been requested. */
const PREVIEW_SETTLE_MS = 500;

describe('ResumePreviewComponent page budget', () => {
  let fixture: ComponentFixture<ResumePreviewComponent>;
  let state: ResumeStateService;
  let render: jasmine.SpyObj<DocumentRenderApiService>;

  beforeEach(() => {
    render = jasmine.createSpyObj<DocumentRenderApiService>('DocumentRenderApiService',
      ['previewResume', 'exportResumePdf']);
    render.previewResume.and.returnValue(of(['<svg xmlns="http://www.w3.org/2000/svg"/>']));
    render.exportResumePdf.and.returnValue(Promise.resolve({ blob: new Blob(), pageCount: 3 }));
    TestBed.configureTestingModule({
      imports: [ResumePreviewComponent, TranslateModule.forRoot()],
      providers: [
        { provide: DocumentRenderApiService, useValue: render },
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

  it('previews the pages the backend rendered for the current design', async () => {
    // Bug: the preview goes back to an HTML layout, so the PDF no longer looks like what she saw.
    state.updatePersonalInfo({ fullName: 'Mette Højgaard' });
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, PREVIEW_SETTLE_MS));
    fixture.detectChanges();

    const imgs = (fixture.nativeElement as HTMLElement).querySelectorAll('#previewContainer img');
    expect(imgs.length).toBe(1);
    const previewed: ResumeDesign = render.previewResume.calls.mostRecent().args[0];
    expect(previewed.person.name).toBe('Mette Højgaard');
  });

  it('sends the anonymised design to the PDF export', async () => {
    // Bug: the export reads the raw personal info, so an anonymised CV carries her real name,
    // e-mail and links into the PDF.
    state.updatePersonalInfo({ fullName: 'Mette Højgaard', email: 'mette@example.dk' });
    state.updateSettings({ anonymise: true });
    spyOn(HTMLAnchorElement.prototype, 'click');

    await fixture.componentInstance.downloadPdf();

    const exported: ResumeDesign = render.exportResumePdf.calls.mostRecent().args[0];
    expect(exported.person.name).not.toContain('Mette');
    expect(exported.person.email).toBe('');
    expect(exported.socials).toEqual([]);
  });

  it('warns when the designed PDF the backend measured runs over two pages', async () => {
    // Bug: the page count header is not read, so an overlong designed CV goes out unwarned.
    spyOn(HTMLAnchorElement.prototype, 'click');

    await fixture.componentInstance.downloadPdf();
    fixture.detectChanges();

    expect(warning()).toContain('pageBudget.cv.over');
  });
});
