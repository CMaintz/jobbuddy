import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageService } from './language.service';
import { DESKTOP_BACKEND_ORIGIN } from './desktop/desktop-shell';

describe('LanguageService', () => {
  const STORAGE_KEY = 'jb_lang_choice';
  const LEGACY_KEY = 'jb_lang';
  let languages: jasmine.Spy;

  function start(browserLanguages: string[], desktopOrigin: string | null = null): LanguageService {
    languages.and.returnValue(browserLanguages);
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [{ provide: DESKTOP_BACKEND_ORIGIN, useValue: desktopOrigin }],
    });
    return TestBed.inject(LanguageService);
  }

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_KEY);
    languages = spyOnProperty(navigator, 'languages');
  });
  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_KEY);
  });

  it('starts in Danish when the browser asks for no supported language', () => {
    expect(start(['de-DE', 'fr']).lang()).toBe('da');
  });

  it('follows a browser that asks for English', () => {
    expect(start(['en-GB', 'da']).lang()).toBe('en');
  });

  it('takes the browser languages in order of preference', () => {
    expect(start(['da-DK', 'en-US']).lang()).toBe('da');
  });

  it('keeps the language the user picked over the browser', () => {
    localStorage.setItem(STORAGE_KEY, 'en');

    expect(start(['da-DK']).lang()).toBe('en');
  });

  it('remembers a language the user picks for the next visit', () => {
    start(['da-DK']).set('en');
    TestBed.resetTestingModule();

    expect(start(['da-DK']).lang()).toBe('en');
  });

  it('ignores the language the old version saved on every visit, which nobody chose', () => {
    localStorage.setItem(LEGACY_KEY, 'en');

    expect(start(['da-DK']).lang()).toBe('da');
    expect(localStorage.getItem(LEGACY_KEY)).toBeNull();
  });

  it('ignores the old saved language for an unsupported browser too', () => {
    localStorage.setItem(LEGACY_KEY, 'en');

    expect(start(['de-DE']).lang()).toBe('da');
  });

  describe('in the desktop app', () => {
    const DESKTOP = 'http://127.0.0.1:51234';

    // Many Danes run English Windows, so the OS language says nothing about what she reads.
    it('starts in Danish even when Windows asks for English', () => {
      expect(start(['en-US', 'en'], DESKTOP).lang()).toBe('da');
    });

    it('still keeps a language she picked herself', () => {
      localStorage.setItem(STORAGE_KEY, 'en');

      expect(start(['da-DK'], DESKTOP).lang()).toBe('en');
    });
  });
});
