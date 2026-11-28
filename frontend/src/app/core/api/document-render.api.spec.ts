import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { DocumentRenderApiService, renderFailureKey } from './document-render.api';
import { ResumeDesign } from '../models/resume-design.model';
import { StructuredDocument } from '../models/structured-document.model';

const BUSY = { status: 429, statusText: 'Too Many Requests' };
const busyBody = { status: 429, code: 'RENDERER_BUSY', message: 'All Typst renderers are busy' };

describe('DocumentRenderApiService when the renderer is busy', () => {
  let api: DocumentRenderApiService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
    api = TestBed.inject(DocumentRenderApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('tells a busy PDF export apart from a failed one', async () => {
    // Bug: the PDF export asks for a blob, so the error body is a blob too and its code is never
    // read; a busy renderer then reads "something went wrong" instead of "try again".
    const exported = api.exportStructuredPdf({} as StructuredDocument);

    backend
      .expectOne('/api/v1/documents/export-structured-pdf')
      .flush(new Blob([JSON.stringify(busyBody)], { type: 'application/json' }), BUSY);

    expect(renderFailureKey(await exported.catch((e) => e))).toBe('notify.rendererBusy');
  });

  it('tells a busy preview apart from a failed one', async () => {
    const preview = firstValueFrom(api.previewResume({} as ResumeDesign));

    backend.expectOne('/api/v1/documents/preview-resume').flush(busyBody, BUSY);

    expect(renderFailureKey(await preview.catch((e) => e))).toBe('notify.rendererBusy');
  });

  it('keeps the generic message for a render that really failed', async () => {
    // Bug: every error is reported as busy, so a broken document is retried forever.
    const exported = api.exportResumePdf({} as ResumeDesign);

    backend
      .expectOne('/api/v1/documents/export-resume-pdf')
      .flush(new Blob(['{"status":500}'], { type: 'application/json' }), { status: 500, statusText: 'Server Error' });

    expect(renderFailureKey(await exported.catch((e) => e))).toBe('notify.actionFailed');
  });
});
