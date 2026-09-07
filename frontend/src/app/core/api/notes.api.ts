import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Note } from '../models/note.model';

@Injectable({ providedIn: 'root' })
export class NotesApiService {
  private http = inject(HttpClient);

  getForJob(jobId: string): Observable<Note[]> {
    return this.http.get<Note[]>(`/api/v1/jobs/${jobId}/notes`);
  }

  create(jobId: string, content: string): Observable<Note> {
    return this.http.post<Note>(`/api/v1/jobs/${jobId}/notes`, { content });
  }

  update(jobId: string, noteId: string, content: string): Observable<Note> {
    return this.http.put<Note>(`/api/v1/jobs/${jobId}/notes/${noteId}`, { content });
  }

  delete(jobId: string, noteId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/jobs/${jobId}/notes/${noteId}`);
  }
}
