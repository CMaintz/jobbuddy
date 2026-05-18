import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SkillTaxonomy, ProfileSkill } from '../models/skill-taxonomy.model';

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
