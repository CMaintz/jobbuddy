import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

/**
 * Every API call leaves through this interceptor, so it decides whether the backend sees
 * a Firebase identity at all. A signed-out session must still reach the network — the
 * public endpoints (login, LinkedIn exchange) depend on it.
 */
// The token arrives through a promise, so a test has to let pending microtasks run before the
// request reaches the testing backend.
const flushMicrotasks = (): Promise<void> => new Promise(resolve => setTimeout(resolve));

describe('authInterceptor', () => {
  let token: string | null;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    token = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getIdToken: () => Promise.resolve(token) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('attaches the Firebase ID token as a bearer header', async () => {
    token = 'id-token-123';
    http.get('/api/v1/jobs').subscribe();
    await flushMicrotasks();

    const req = backend.expectOne('/api/v1/jobs');
    expect(req.request.headers.get('Authorization')).toBe('Bearer id-token-123');
    req.flush([]);
  });

  it('sends the request unauthenticated when there is no signed-in user', async () => {
    http.post('/api/v1/auth/linkedin', { code: 'c' }).subscribe();
    await flushMicrotasks();

    const req = backend.expectOne('/api/v1/auth/linkedin');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.body).toEqual({ code: 'c' });
    req.flush({});
  });

  it('holds the request until the token has resolved', async () => {
    token = 'late';
    http.get('/api/v1/dashboard').subscribe();

    expect(backend.match('/api/v1/dashboard')).toEqual([]);
    await flushMicrotasks();

    const req = backend.expectOne('/api/v1/dashboard');
    expect(req.request.headers.get('Authorization')).toBe('Bearer late');
    req.flush({});
  });

  it('passes the response through untouched', async () => {
    token = 't';
    let body: unknown;
    http.get('/api/v1/jobs/1').subscribe(res => (body = res));
    await flushMicrotasks();

    backend.expectOne('/api/v1/jobs/1').flush({ id: '1', title: 'Engineer' });
    expect(body).toEqual({ id: '1', title: 'Engineer' });
  });
});
