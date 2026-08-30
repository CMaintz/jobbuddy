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
  source: 'TAXONOMY_ADJACENT' | 'MARKET_DEMAND' | 'BOTH' | 'DOCUMENT_INFERRED';
  /**
   * For a DOCUMENT_INFERRED candidate, the line of the user's own CV or LinkedIn export that
   * implies the skill. It is what makes the suggestion answerable from memory.
   */
  evidence?: string | null;
}

export interface SkillConfirmation {
  name: string;
  decision: 'YES' | 'NO' | 'SKIP';
  usedInProduction: boolean;
  yearsExperience?: number | null;
}

/** A claimed, in-demand skill with nothing in the profile to prove it. */
export interface EvidenceGap {
  skillName: string;
  marketFrequency: number;
  question: string;
}

/** A free-text answer restructured for confirmation — never saved until the user accepts it. */
export interface EvidenceDraft {
  skillName: string;
  situation: string | null;
  action: string | null;
  result: string | null;
  /** Figures the draft contains that the user did not write. Should be empty. */
  unsupportedFigures: string[];
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

  getEvidenceGaps(limit = 5): Observable<EvidenceGap[]> {
    return this.http.get<EvidenceGap[]>('/api/v1/skills/evidence-gaps', { params: { limit } });
  }

  /** Gaps with questions tailored to this profile; falls back to templates server-side. */
  getTailoredEvidenceGaps(limit = 5): Observable<EvidenceGap[]> {
    return this.http.get<EvidenceGap[]>('/api/v1/skills/evidence-gaps/tailored', { params: { limit } });
  }

  /** Restructures what the user typed into STAR fields for them to confirm. Saves nothing. */
  draftEvidence(skillName: string, answer: string): Observable<EvidenceDraft> {
    return this.http.post<EvidenceDraft>('/api/v1/skills/evidence/draft', { skillName, answer });
  }

  /** Stores evidence as a STAR story tagged with the skill; the gap closes immediately. */
  recordEvidence(skillName: string, situation: string, action: string, result: string): Observable<unknown> {
    return this.http.post('/api/v1/skills/evidence', { skillName, situation, action, result });
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
