import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PromptTemplate, PromptCategory } from '../models/prompt-template.model';

export interface CreateTemplateRequest {
  name: string;
  category?: PromptCategory;
  description?: string;
  systemPrompt?: string;
  userPrompt: string;
  outputConstraints?: string;
  isPublic: boolean;
  tags?: string[];
}

@Injectable({ providedIn: 'root' })
export class PromptApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/prompts';

  getAll(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(this.base);
  }

  /**
   * Prompts other people chose to share. Excludes the app's own — everyone already has those —
   * and your own, which are already in your library.
   */
  getPublic(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(`${this.base}/public`);
  }

  create(req: CreateTemplateRequest): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(this.base, req);
  }

  duplicate(id: string, name?: string): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(`${this.base}/${id}/duplicate`, {}, name ? { params: { name } } : undefined);
  }

  /** Own templates only; protected (app-origin) templates require an admin account. */
  update(id: string, req: CreateTemplateRequest): Observable<PromptTemplate> {
    return this.http.put<PromptTemplate>(`${this.base}/${id}`, req);
  }

  /** Own templates only; protected (app-origin) templates require an admin account. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /**
   * Uses this template as the default for its category. Records the caller's own choice — the
   * app's seeded prompt is never modified, so `resetDefault` always has something to restore.
   */
  selectDefault(id: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/default`, {});
  }

  resetDefault(category: PromptCategory): Observable<void> {
    return this.http.delete<void>(`${this.base}/defaults/${category}`);
  }

  favourite(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/favorite`, {});
  }

  unfavourite(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/favorite`);
  }
}
