import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, from, switchMap } from 'rxjs';
import { tap } from 'rxjs/operators';
import { User } from '../models/user.model';
import { environment } from '../../../environments/environment';
import { auth } from '../firebase/firebase.config';
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithPopup,
  signInWithCustomToken,
  signOut,
  GoogleAuthProvider
} from 'firebase/auth';

export interface MeResponse {
  userId: string;
  email: string;
  role: 'USER' | 'ADMIN';
  onboardingComplete: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private readonly USER_KEY = 'aa_user';

  private currentUserSubject = new BehaviorSubject<User | null>(this.loadUser());
  currentUser$ = this.currentUserSubject.asObservable();

  /** Email/password sign-in. Returns internal user record from /me. */
  login(email: string, password: string): Observable<MeResponse> {
    return from(signInWithEmailAndPassword(auth, email, password)).pipe(
      switchMap(() => this.fetchMe())
    );
  }

  /**
   * Creates a new Firebase account and sends a verification email.
   * Does NOT sign the user in — they must click the link in their inbox first.
   */
  register(email: string, password: string): Promise<void> {
    return createUserWithEmailAndPassword(auth, email, password)
      .then(cred => sendEmailVerification(cred.user))
      .then(() => signOut(auth));
  }

  /** Google Sign-In via popup. */
  loginWithGoogle(): Observable<MeResponse> {
    return from(signInWithPopup(auth, new GoogleAuthProvider())).pipe(
      switchMap(() => this.fetchMe())
    );
  }

  /**
   * LinkedIn OAuth callback: backend exchanges the code and returns a Firebase
   * custom token; we use that to establish a real Firebase session.
   */
  linkedinCallback(code: string, redirectUri: string): Observable<MeResponse> {
    return this.http
      .post<{ customToken: string }>('/api/v1/auth/linkedin', { code, redirectUri })
      .pipe(
        switchMap(res => from(signInWithCustomToken(auth, res.customToken))),
        switchMap(() => this.fetchMe())
      );
  }

  buildLinkedInAuthUrl(redirectUri: string): string {
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

  logout(): Promise<void> {
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    return signOut(auth);
  }

  /** True when the signed-in Firebase user has verified their email. */
  isEmailVerified(): boolean {
    return auth.currentUser?.emailVerified ?? false;
  }

  /** Sign-in provider id of the current session: 'password', 'google.com', or custom (LinkedIn). */
  signInProvider(): string {
    return auth.currentUser?.providerData[0]?.providerId ?? 'password';
  }

  resendVerification(): Promise<void> {
    if (!auth.currentUser) return Promise.reject(new Error('Not signed in'));
    return sendEmailVerification(auth.currentUser);
  }

  /** Sends the Firebase password-reset email to the signed-in user's address. */
  sendPasswordReset(): Promise<void> {
    const email = auth.currentUser?.email;
    if (!email) return Promise.reject(new Error('No email on the account'));
    return sendPasswordResetEmail(auth, email);
  }

  /**
   * Resolves once Firebase has restored (or ruled out) a persisted session.
   * On a fresh page load auth.currentUser is null until the SDK finishes reading
   * IndexedDB — checking it before this resolves misreads a logged-in user as
   * logged out.
   */
  whenAuthReady(): Promise<void> {
    return auth.authStateReady();
  }

  /** Returns the current Firebase ID token (auto-refreshed by the Firebase SDK). */
  getIdToken(): Promise<string | null> {
    return auth.authStateReady()
      .then(() => auth.currentUser ? auth.currentUser.getIdToken() : null);
  }

  isAuthenticated(): boolean {
    return !!auth.currentUser;
  }

  isOnboardingComplete(): boolean {
    return this.currentUserSubject.value?.onboardingComplete ?? true;
  }

  completeOnboarding(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/complete-onboarding', {}).pipe(
      tap(() => {
        const user = this.currentUserSubject.value;
        if (user) {
          const updated: User = { ...user, onboardingComplete: true };
          localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
          this.currentUserSubject.next(updated);
        }
      })
    );
  }

  private fetchMe(): Observable<MeResponse> {
    return this.http.get<MeResponse>('/api/v1/auth/me').pipe(
      tap(res => {
        const user: User = {
          id: res.userId, email: res.email, role: res.role, onboardingComplete: res.onboardingComplete,
        };
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.currentUserSubject.next(user);
      })
    );
  }

  private loadUser(): User | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
