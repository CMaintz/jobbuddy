import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WorkExperience, Project, Education, Certification } from '../models/profile-section.model';

@Injectable({ providedIn: 'root' })
export class ProfileSectionsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/profile';

  // Work Experience
  getExperience(): Observable<WorkExperience[]> {
    return this.http.get<WorkExperience[]>(`${this.base}/experience`);
  }
  addExperience(exp: WorkExperience): Observable<WorkExperience> {
    return this.http.post<WorkExperience>(`${this.base}/experience`, exp);
  }
  updateExperience(id: string, exp: WorkExperience): Observable<WorkExperience> {
    return this.http.put<WorkExperience>(`${this.base}/experience/${id}`, exp);
  }
  deleteExperience(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/experience/${id}`);
  }

  // Projects
  getProjects(): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.base}/projects`);
  }
  addProject(project: Project): Observable<Project> {
    return this.http.post<Project>(`${this.base}/projects`, project);
  }
  updateProject(id: string, project: Project): Observable<Project> {
    return this.http.put<Project>(`${this.base}/projects/${id}`, project);
  }
  deleteProject(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/projects/${id}`);
  }

  // Education
  getEducation(): Observable<Education[]> {
    return this.http.get<Education[]>(`${this.base}/education`);
  }
  addEducation(edu: Education): Observable<Education> {
    return this.http.post<Education>(`${this.base}/education`, edu);
  }
  updateEducation(id: string, edu: Education): Observable<Education> {
    return this.http.put<Education>(`${this.base}/education/${id}`, edu);
  }
  deleteEducation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/education/${id}`);
  }

  // Certifications
  getCertifications(): Observable<Certification[]> {
    return this.http.get<Certification[]>(`${this.base}/certifications`);
  }
  addCertification(cert: Certification): Observable<Certification> {
    return this.http.post<Certification>(`${this.base}/certifications`, cert);
  }
  deleteCertification(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/certifications/${id}`);
  }
}
