import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentTemplateOption } from '../models/structured-document.model';

@Injectable({ providedIn: 'root' })
export class StructuredDocumentTemplatesApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/structured-document-templates';

  getActive(): Observable<DocumentTemplateOption[]> {
    return this.http.get<DocumentTemplateOption[]>(this.base);
  }
}
