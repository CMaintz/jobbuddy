import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SkillTaxonomy, ProfileSkill } from '../models/skill-taxonomy.model';

/** A skill the user has not claimed, offered for confirmation. */
export interface SkillCandidate {
  name: string;
  category: string | null;
  /** How many of the user's matched postings name it — the reason to spend attention here. */
  marketFrequency: number;
  /** The user's own skills it sits next to in the taxonomy. */
  relatedSkills: string[];
  source: 'TAXONOMY_ADJACENT' | 'MARKET_DEMAND' | 'BOTH';
}

export interface SkillConfirmation {
  name: string;
  decision: 'YES' | 'NO' | 'SKIP';
  usedInProduction: boolean;
  yearsExperience?: number | null;
}

@Injectable({ providedIn: 'root' })
export class SkillsApiService {
  private http = inject(HttpClient);

  searchTaxonomy(q: string): Observable<SkillTaxonomy[]> {
    return this.http.get<SkillTaxonomy[]>('/api/v1/skills', { params: { q } });
  }

  createTaxonomySkill(name: string, category?: string): Observable<SkillTaxonomy> {
    return this.http.post<SkillTaxonomy>('/api/v1/skills', { name, category });
  }

  getByCategory(category: string): Observable<SkillTaxonomy[]> {
    return this.http.get<SkillTaxonomy[]>('/api/v1/skills', { params: { category } });
  }

  getCategories(): Observable<string[]> {
    return this.http.get<string[]>('/api/v1/skills/categories');
  }

  /** Deterministic suggestions — safe to call on every page load, no AI cost. */
  getSkillCandidates(limit = 12): Observable<SkillCandidate[]> {
    return this.http.get<SkillCandidate[]>('/api/v1/skills/candidates', { params: { limit } });
  }

  /** Answers a round of suggestions; returns the skills that were added. */
  confirmSkillCandidates(confirmations: SkillConfirmation[]): Observable<ProfileSkill[]> {
    return this.http.post<ProfileSkill[]>('/api/v1/skills/candidates/confirm', { confirmations });
  }

  getProfileSkills(): Observable<ProfileSkill[]> {
    return this.http.get<ProfileSkill[]>('/api/v1/profile/skills');
  }

  addProfileSkill(skill: ProfileSkill): Observable<ProfileSkill> {
    return this.http.post<ProfileSkill>('/api/v1/profile/skills', skill);
  }

  updateProfileSkill(id: string, skill: ProfileSkill): Observable<ProfileSkill> {
    return this.http.put<ProfileSkill>(`/api/v1/profile/skills/${id}`, skill);
  }

  deleteProfileSkill(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/profile/skills/${id}`);
  }
}
