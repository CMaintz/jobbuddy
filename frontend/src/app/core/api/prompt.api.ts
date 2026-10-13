import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, MonoTypeOperatorFunction, distinctUntilChanged, map, shareReplay, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
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
  private cachedAll?: Observable<PromptTemplate[]>;

  constructor() {
    // Sign-out navigates without a page load, so a cache from the previous account would
    // otherwise show its private templates to whoever signs in next in the same tab.
    inject(AuthService).currentUser$
      .pipe(map(user => user?.id), distinctUntilChanged())
      .subscribe(() => (this.cachedAll = undefined));
  }

  getAll(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(this.base);
  }

  /**
   * The library shared across every caller that only reads it (one CV editor can host many
   * refine popovers). Any change made through this service drops it, and so does a failed fetch,
   * so the next reader asks again instead of inheriting a stale default or a replayed error.
   */
  getAllCached(): Observable<PromptTemplate[]> {
    this.cachedAll ??= this.getAll().pipe(
      tap({ error: () => (this.cachedAll = undefined) }),
      shareReplay(1),
    );
    return this.cachedAll;
  }

  /**
   * Prompts other people chose to share. Excludes the app's own — everyone already has those —
   * and your own, which are already in your library.
   */
  getPublic(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(`${this.base}/public`);
  }

  create(req: CreateTemplateRequest): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(this.base, req).pipe(this.invalidatingCache());
  }

  duplicate(id: string, name?: string): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(`${this.base}/${id}/duplicate`, {}, name ? { params: { name } } : undefined)
      .pipe(this.invalidatingCache());
  }

  /** Own templates only; protected (app-origin) templates require an admin account. */
  update(id: string, req: CreateTemplateRequest): Observable<PromptTemplate> {
    return this.http.put<PromptTemplate>(`${this.base}/${id}`, req).pipe(this.invalidatingCache());
  }

  /** Own templates only; protected (app-origin) templates require an admin account. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(this.invalidatingCache());
  }

  /**
   * Uses this template as the default for its category. Records the caller's own choice — the
   * app's seeded prompt is never modified, so `resetDefault` always has something to restore.
   */
  selectDefault(id: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/default`, {}).pipe(this.invalidatingCache());
  }

  resetDefault(category: PromptCategory): Observable<void> {
    return this.http.delete<void>(`${this.base}/defaults/${category}`).pipe(this.invalidatingCache());
  }

  favourite(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/favorite`, {}).pipe(this.invalidatingCache());
  }

  unfavourite(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/favorite`).pipe(this.invalidatingCache());
  }

  private invalidatingCache<T>(): MonoTypeOperatorFunction<T> {
    return tap(() => (this.cachedAll = undefined));
  }
}
