import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Company {
  id: string;
  name: string;
  slug: string | null;
  website: string | null;
  linkedinUrl: string | null;
  description: string | null;
  logoUrl: string | null;
  sizeRange: string | null;
  industry: string | null;
  country: string | null;
  isConsulting: boolean;
  isRecruitingAgency: boolean;
}

/** One ranking reason: a translation key under `companies.targets.reason.` plus its arguments. */
export interface OutreachReason {
  code: string;
  args: Record<string, string>;
}

/** A company worth an unsolicited application, with the reasons it was ranked where it was. */
export interface OutreachTarget {
  companyId: string;
  companyName: string;
  website: string | null;
  score: number;
  reasons: OutreachReason[];
  lastPostedAt: string | null;
  matchedTechnologies: string[];
  hasOpenRole: boolean;
}

@Injectable({ providedIn: 'root' })
export class CompaniesApiService {
  private http = inject(HttpClient);

  search(query = '', page = 0, size = 20): Observable<Company[]> {
    const params = new HttpParams()
      .set('q', query)
      .set('page', page)
      .set('size', size);
    return this.http.get<Company[]>('/api/v1/companies', { params });
  }

  /** Ranked unsolicited-application targets. Companies hiring right now are excluded by default. */
  outreachTargets(limit = 20, includeHiringNow = false): Observable<OutreachTarget[]> {
    const params = new HttpParams()
      .set('limit', limit)
      .set('includeHiringNow', includeHiringNow);
    return this.http.get<OutreachTarget[]>('/api/v1/companies/outreach-targets', { params });
  }

  getById(id: string): Observable<Company> {
    return this.http.get<Company>(`/api/v1/companies/${id}`);
  }
}
