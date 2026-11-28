import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, firstValueFrom, map } from 'rxjs';
import { StructuredDocument } from '../models/structured-document.model';
import { ResumeDesign } from '../models/resume-design.model';

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
      .pipe(map(res => res.pages));
  }

  private async pdf(request: Observable<HttpResponse<Blob>>): Promise<RenderedPdf> {
    const res = await firstValueFrom(request);
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
