import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { SettingsComponent } from './settings.component';
import { DESKTOP_BACKEND_ORIGIN } from '../../core/desktop/desktop-shell';

/**
 * On the web, signing out ends the Firebase session. The desktop app has no account to leave:
 * the button would only strand her on a login page she has no login for.
 */
describe('Settings sign-out', () => {
  function accountSection(desktopOrigin: string | null): HTMLElement {
    TestBed.configureTestingModule({
      imports: [SettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DESKTOP_BACKEND_ORIGIN, useValue: desktopOrigin },
      ],
    });
    const fixture = TestBed.createComponent(SettingsComponent);
    fixture.componentInstance.activeSection.set('account');
    fixture.detectChanges();
    // The page shows a loading state until the preferences arrive.
    TestBed.inject(HttpTestingController)
      .match(req => req.url.endsWith('/users/me/preferences'))
      .forEach(req => req.flush({}));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    // The account section really rendered, so "no sign-out" below is not just an empty page.
    expect(page.textContent).toContain('settings.account.intro');
    return page;
  }

  it('is offered on the web', () => {
    expect(accountSection(null).textContent).toContain('settings.account.signOut');
  });

  it('is not offered in the desktop app', () => {
    expect(accountSection('http://127.0.0.1:51234').textContent).not.toContain('settings.account.signOut');
  });
});
