import { Injectable, signal, effect, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type Lang = 'en' | 'da';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly STORAGE_KEY = 'jb_lang';
  private readonly translate = inject(TranslateService);

  readonly lang = signal<Lang>(this.loadLang());

  constructor() {
    this.translate.addLangs(['en', 'da']);
    this.translate.setDefaultLang('en');

    effect(() => {
      const l = this.lang();
      this.translate.use(l);
      document.documentElement.lang = l;
      try {
        localStorage.setItem(this.STORAGE_KEY, l);
      } catch { /* private mode — language just won't persist */ }
    });
  }

  toggle(): void {
    this.lang.set(this.lang() === 'en' ? 'da' : 'en');
  }

  set(lang: Lang): void {
    this.lang.set(lang);
  }

  private loadLang(): Lang {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored === 'en' || stored === 'da') return stored;
    } catch { /* private mode — fall through to default */ }
    return 'en';
  }
}
