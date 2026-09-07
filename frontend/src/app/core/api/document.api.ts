import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CvVersion } from '../models/cv-version.model';

@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private http = inject(HttpClient);

  getCvVersions(): Observable<CvVersion[]> {
    return this.http.get<CvVersion[]>('/api/v1/cv');
  }

  uploadCv(name: string, content: string, format = 'MARKDOWN'): Observable<CvVersion> {
    return this.http.post<CvVersion>('/api/v1/cv', { name, content, format });
  }
}
