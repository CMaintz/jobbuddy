import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Application, ApplicationStatus } from '../models/application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/applications';

  getAll(): Observable<Application[]> {
    return this.http.get<Application[]>(this.base);
  }

  create(jobId: string, cvVersionId?: string, notes?: string): Observable<Application> {
    return this.http.post<Application>(this.base, { jobId, cvVersionId, notes });
  }

  getById(id: string): Observable<Application> {
    return this.http.get<Application>(`${this.base}/${id}`);
  }

  updateStatus(id: string, status: ApplicationStatus, notes?: string): Observable<Application> {
    return this.http.patch<Application>(`${this.base}/${id}/status`, { status, notes });
  }
}
