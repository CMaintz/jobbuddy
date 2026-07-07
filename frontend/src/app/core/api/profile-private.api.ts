import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProfilePrivateInfo } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class ProfilePrivateApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile/private';

  getPrivateInfo(): Observable<ProfilePrivateInfo> {
    return this.http.get<ProfilePrivateInfo>(this.base);
  }

  /** Partial update — omitted/null fields keep their current value; empty string clears. */
  updatePrivateInfo(data: ProfilePrivateInfo): Observable<ProfilePrivateInfo> {
    return this.http.patch<ProfilePrivateInfo>(this.base, data);
  }
}
