import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProfileSocial } from '../models/profile-section.model';

@Injectable({ providedIn: 'root' })
export class ProfileSocialApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile/socials';

  getSocials(): Observable<ProfileSocial[]> {
    return this.http.get<ProfileSocial[]>(this.base);
  }

  createSocial(data: Omit<ProfileSocial, 'id' | 'userId'>): Observable<ProfileSocial> {
    return this.http.post<ProfileSocial>(this.base, data);
  }

  updateSocial(id: string, data: ProfileSocial): Observable<ProfileSocial> {
    return this.http.put<ProfileSocial>(`${this.base}/${id}`, data);
  }

  deleteSocial(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  reorderSocials(order: ProfileSocial[]): Observable<ProfileSocial[]> {
    return this.http.put<ProfileSocial[]>(`${this.base}/reorder`, order);
  }
}
