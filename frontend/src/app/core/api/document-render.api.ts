import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse, HttpStatusCode } from '@angular/common/http';
import { Observable, catchError, firstValueFrom, from, map, switchMap, throwError } from 'rxjs';
import { StructuredDocument } from '../models/structured-document.model';
import { ResumeDesign } from '../models/resume-design.model';

/** Matches DocumentRendererBusyException.CODE on the server. */
const RENDERER_BUSY = 'RENDERER_BUSY';

/** Every renderer slot stayed taken; nothing is wrong with the document, a retry soon works. */
export class RendererBusyError extends Error {
  constructor() {
    super('The renderer is busy');
    this.name = 'RendererBusyError';
  }
}

/** The notice for a failed render: "busy, try again" for a full renderer, else the generic one. */
export function renderFailureKey(err: unknown): string {
  return err instanceof RendererBusyError ? 'notify.rendererBusy' : 'notify.actionFailed';
}

/** A PDF the backend rendered, with the page count it measured on it. */
export interface RenderedPdf {
  blob: Blob;
  pageCount: number;
}

/**
 * The backend's Typst renderer: designed CVs and letters as real text PDFs, and the builder's
 * preview as the SVG pages of that same layout.
 */
@Injectable({ providedIn: 'root' })
export class DocumentRenderApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/documents';

  exportResumePdf(design: ResumeDesign): Promise<RenderedPdf> {
    return this.pdf(this.http.post(`${this.base}/export-resume-pdf`, design,
      { observe: 'response', responseType: 'blob' }));
  }

  exportStructuredPdf(document: StructuredDocument): Promise<RenderedPdf> {
    return this.pdf(this.http.post(`${this.base}/export-structured-pdf`, { document },
      { observe: 'response', responseType: 'blob' }));
  }

  /** One SVG document per page, in page order. */
  previewResume(design: ResumeDesign): Observable<string[]> {
    return this.http.post<{ pages: string[] }>(`${this.base}/preview-resume`, design)
      .pipe(map(res => res.pages), catchError(busyOrRethrow));
  }

  private async pdf(request: Observable<HttpResponse<Blob>>): Promise<RenderedPdf> {
    const res = await firstValueFrom(request.pipe(catchError(busyOrRethrow)));
    // The backend measures the PDF it built; without the header the count is unknown, and 0
    // keeps the budget warning quiet rather than claiming the document fits.
    const pageCount = Number(res.headers.get('X-Page-Count') ?? 0);
    return { blob: res.body ?? new Blob([], { type: 'application/pdf' }), pageCount };
  }
}

/** Saves a rendered PDF under `name` and resolves to its page count, for the page budget. */
export function savePdf(pdf: RenderedPdf, name: string): number {
  const url = URL.createObjectURL(pdf.blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${name}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
  return pdf.pageCount;
}


/** Turns the server's 429 RENDERER_BUSY into a {@link RendererBusyError}; any other error passes through. */
function busyOrRethrow(err: unknown): Observable<never> {
  if (!(err instanceof HttpErrorResponse) || err.status !== HttpStatusCode.TooManyRequests) {
    return throwError(() => err);
  }
  return from(errorCode(err.error)).pipe(
    switchMap(code => throwError(() => code === RENDERER_BUSY ? new RendererBusyError() : err)));
}

/** A blob request gets its error body as a blob too, so the JSON has to be read out of it. */
async function errorCode(body: unknown): Promise<string | undefined> {
  if (!(body instanceof Blob)) return (body as { code?: string } | null)?.code;
  try {
    return (JSON.parse(await body.text()) as { code?: string }).code;
  } catch {
    return undefined;
  }
}
