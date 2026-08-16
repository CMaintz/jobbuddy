import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CareerTarget {
  userId?: string;
  targetArchetypes?: string[];
  northStar?: string;
  narrative?: string;
  cultureRequirements?: string[];
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class CareerTargetApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile/career-target';

  get(): Observable<CareerTarget> {
    return this.http.get<CareerTarget>(this.base);
  }

  update(target: CareerTarget): Observable<CareerTarget> {
    return this.http.put<CareerTarget>(this.base, target);
  }
}
