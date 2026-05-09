import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PdfTemplate } from '../models/pdf-template.model';

@Injectable({ providedIn: 'root' })
export class PdfTemplatesApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/pdf-templates';

  getAll(): Observable<PdfTemplate[]> {
    return this.http.get<PdfTemplate[]>(this.base);
  }

  getById(id: string): Observable<PdfTemplate> {
    return this.http.get<PdfTemplate>(`${this.base}/${id}`);
  }

  create(template: Partial<PdfTemplate>): Observable<PdfTemplate> {
    return this.http.post<PdfTemplate>(this.base, template);
  }

  update(id: string, template: Partial<PdfTemplate>): Observable<PdfTemplate> {
    return this.http.put<PdfTemplate>(`${this.base}/${id}`, template);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
