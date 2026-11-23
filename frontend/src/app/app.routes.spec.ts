import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('app routes', () => {
  async function landingFor(url: string, isAdmin: boolean): Promise<string> {
    const auth = {
      whenAuthReady: () => Promise.resolve(),
      isAuthenticated: () => true,
      needsEmailVerification: () => false,
      isAdmin: () => isAdmin,
      currentUser$: of(null),
    };
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter(routes), provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url).catch(() => undefined);
    return TestBed.inject(Router).url;
  }

  it('keeps a job seeker who types the prompt library URL out of it', async () => {
    expect(await landingFor('/prompts', false)).toBe('/dashboard');
  });

  it('opens the prompt library for an admin', async () => {
    expect(await landingFor('/prompts', true)).toBe('/prompts');
  });
});
