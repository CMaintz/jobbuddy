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

@Injectable({ providedIn: 'root' })
export class CompaniesApiService {
  private http = inject(HttpClient);

  search(query: string = '', page = 0, size = 20): Observable<Company[]> {
    const params = new HttpParams()
      .set('q', query)
      .set('page', page)
      .set('size', size);
    return this.http.get<Company[]>('/api/v1/companies', { params });
  }

  getById(id: string): Observable<Company> {
    return this.http.get<Company>(`/api/v1/companies/${id}`);
  }
}
