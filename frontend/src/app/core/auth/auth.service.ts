import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { AuthResponse, User } from '../models/user.model';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private readonly TOKEN_KEY = 'aa_token';
  private readonly USER_KEY = 'aa_user';

  private currentUserSubject = new BehaviorSubject<User | null>(this.loadUser());
  currentUser$ = this.currentUserSubject.asObservable();

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  register(email: string, password: string, fullName: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/register', { email, password, fullName }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  linkedinCallback(code: string, redirectUri: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/linkedin', { code, redirectUri }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  buildLinkedInAuthUrl(redirectUri: string): string {
    // Configure linkedInClientId in src/environments/environment.ts before using this
    const clientId = environment.linkedInClientId;
    const scope = 'openid profile email';
    const state = Math.random().toString(36).substring(2);
    sessionStorage.setItem('linkedin_state', state);
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: redirectUri,
      scope,
      state
    });
    return `https://www.linkedin.com/oauth/v2/authorization?${params}`;
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  private storeAuth(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    const user: User = { id: res.userId, email: res.email, role: res.role };
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  private loadUser(): User | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
