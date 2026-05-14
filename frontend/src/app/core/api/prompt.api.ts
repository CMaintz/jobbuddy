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
}

@Injectable({ providedIn: 'root' })
export class PromptApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/prompts';

  getAll(): Observable<PromptTemplate[]> {
    return this.http.get<PromptTemplate[]>(this.base);
  }

  create(req: CreateTemplateRequest): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(this.base, req);
  }

  duplicate(id: string, name?: string): Observable<PromptTemplate> {
    return this.http.post<PromptTemplate>(`${this.base}/${id}/duplicate`, {}, name ? { params: { name } } : undefined);
  }
}
