import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { LanguageService } from './language.service';
import { User } from './models/user.model';
import { UiLanguageSyncService } from './ui-language-sync.service';

/** The reminder email is written server-side, so the server has to know the UI language. */
describe('UiLanguageSyncService', () => {
  const URL = '/api/v1/users/me/language';
  let user$: BehaviorSubject<User | null>;
  let backend: HttpTestingController;
  let language: LanguageService;

  beforeEach(() => {
    localStorage.removeItem('jb_lang_choice');
    user$ = new BehaviorSubject<User | null>(null);
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUser$: user$ } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    language = TestBed.inject(LanguageService);
    language.set('da');
    TestBed.inject(UiLanguageSyncService).start();
    TestBed.tick();
  });

  afterEach(() => localStorage.removeItem('jb_lang_choice'));

  it('tells the server the language once the user is signed in', () => {
    backend.expectNone(URL);

    user$.next({ id: 'u1', email: 'me@example.dk', role: 'USER', onboardingComplete: true });
    TestBed.tick();

    expect(backend.expectOne(URL).request.body).toEqual({ language: 'da' });
  });

  it('tells the server when the user switches language', () => {
    user$.next({ id: 'u1', email: 'me@example.dk', role: 'USER', onboardingComplete: true });
    TestBed.tick();
    backend.expectOne(URL).flush(null);

    language.set('en');
    TestBed.tick();

    expect(backend.expectOne(URL).request.body).toEqual({ language: 'en' });
  });
});
