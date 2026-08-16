import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RetractedClaim {
  id: string;
  userId?: string;
  claim: string;
  reason?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class RetractedClaimsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile/retracted-claims';

  list(): Observable<RetractedClaim[]> {
    return this.http.get<RetractedClaim[]>(this.base);
  }

  add(claim: string, reason?: string): Observable<RetractedClaim> {
    return this.http.post<RetractedClaim>(this.base, { claim, reason });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
