import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProfileStrength } from '../models/profile-section.model';

@Injectable({ providedIn: 'root' })
export class ProfileStrengthApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile/strengths';

  getStrengths(): Observable<ProfileStrength[]> {
    return this.http.get<ProfileStrength[]>(this.base);
  }

  createStrength(data: Omit<ProfileStrength, 'id' | 'userId'>): Observable<ProfileStrength> {
    return this.http.post<ProfileStrength>(this.base, data);
  }

  updateStrength(id: string, data: ProfileStrength): Observable<ProfileStrength> {
    return this.http.put<ProfileStrength>(`${this.base}/${id}`, data);
  }

  deleteStrength(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  reorderStrengths(order: ProfileStrength[]): Observable<ProfileStrength[]> {
    return this.http.put<ProfileStrength[]>(`${this.base}/reorder`, order);
  }
}
