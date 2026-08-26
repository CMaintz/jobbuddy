import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type CareerStage =
  | 'STUDENT' | 'NEW_GRAD' | 'EARLY_CAREER' | 'MID_CAREER' | 'SENIOR' | 'LEAD' | 'CAREER_CHANGER';

export interface CareerTarget {
  userId?: string;
  targetArchetypes?: string[];
  northStar?: string;
  narrative?: string;
  cultureRequirements?: string[];
  careerStage?: CareerStage;
  /** Free text as the user states it: "3 måneder", "1 month", "negotiable". */
  noticePeriod?: string;
  /** ISO date (yyyy-MM-dd) — the first day the candidate could start. */
  earliestStartDate?: string;
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
