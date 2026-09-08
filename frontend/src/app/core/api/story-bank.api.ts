import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface InterviewStory {
  id?: string;
  userId?: string;
  title: string;
  situation?: string;
  task?: string;
  action?: string;
  result?: string;
  reflection?: string;
  tags?: string[];
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class StoryBankApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/interview/stories';

  list(): Observable<InterviewStory[]> {
    return this.http.get<InterviewStory[]>(this.base);
  }

  upsert(story: InterviewStory): Observable<InterviewStory> {
    return this.http.put<InterviewStory>(this.base, story);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
