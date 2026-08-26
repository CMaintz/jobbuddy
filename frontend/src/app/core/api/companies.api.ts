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

export type OutreachStatus = 'SAVED' | 'CONTACTED' | 'REPLIED' | 'MEETING' | 'CLOSED';

/** A tracked unsolicited outreach — who was written to, and when to come back. */
export interface OutreachContact {
  id: string;
  companyId: string | null;
  companyName: string;
  status: OutreachStatus;
  channel: string | null;
  contactName: string | null;
  contactedAt: string | null;
  /** ISO date. The field that turns a list of sent letters into a process. */
  followUpDue: string | null;
  notes: string | null;
  createdAt: string;
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

  /** Tracked outreach, follow-ups due first. */
  listOutreach(): Observable<OutreachContact[]> {
    return this.http.get<OutreachContact[]>('/api/v1/companies/outreach');
  }

  /** Idempotent per company — tracking one already tracked returns the existing record. */
  trackOutreach(companyId: string | null, companyName: string, contactName?: string): Observable<OutreachContact> {
    return this.http.post<OutreachContact>('/api/v1/companies/outreach', { companyId, companyName, contactName });
  }

  /** Null fields are left unchanged, so the UI can send only what it is changing. */
  updateOutreach(id: string, patch: Partial<Pick<OutreachContact, 'status' | 'channel' | 'followUpDue' | 'notes'>>): Observable<OutreachContact> {
    return this.http.patch<OutreachContact>(`/api/v1/companies/outreach/${id}`, patch);
  }

  untrackOutreach(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/companies/outreach/${id}`);
  }

  getById(id: string): Observable<Company> {
    return this.http.get<Company>(`/api/v1/companies/${id}`);
  }
}
