import { Injectable, signal, effect, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DESKTOP_BACKEND_ORIGIN } from './desktop/desktop-shell';

export type Lang = 'en' | 'da';

const SUPPORTED: readonly Lang[] = ['da', 'en'];

/** The app is built for Danish job seekers, so Danish wins unless something says otherwise. */
const DEFAULT_LANG: Lang = 'da';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly STORAGE_KEY = 'jb_lang_choice';
  /**
   * The old version wrote this on every visit, so it holds a default nobody chose (usually "en").
   * Reading it would keep existing users in English; it is dropped instead.
   */
  private readonly LEGACY_KEY = 'jb_lang';
  private readonly translate = inject(TranslateService);
  /** Many Danes run English Windows, so in the desktop app the OS language is no signal. */
  private readonly desktop = inject(DESKTOP_BACKEND_ORIGIN) !== null;

  readonly lang = signal<Lang>(this.loadLang());

  constructor() {
    this.translate.addLangs([...SUPPORTED]);
    this.translate.setDefaultLang('en');

    effect(() => {
      const l = this.lang();
      this.translate.use(l);
      document.documentElement.lang = l;
    });
  }

  toggle(): void {
    this.set(this.lang() === 'en' ? 'da' : 'en');
  }

  /**
   * Only an explicit choice is stored. Storing the derived default would pin it, so the
   * browser preference could never take over again for someone who never chose.
   */
  set(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(this.STORAGE_KEY, lang);
    } catch { /* private mode: the choice just won't persist */ }
  }

  private loadLang(): Lang {
    return this.storedLang() ?? (this.desktop ? null : browserLang()) ?? DEFAULT_LANG;
  }

  private storedLang(): Lang | null {
    try {
      localStorage.removeItem(this.LEGACY_KEY);
      return asLang(localStorage.getItem(this.STORAGE_KEY));
    } catch {
      return null;
    }
  }
}

/** The first browser preference we can serve, matched on the primary subtag (en-GB is en). */
function browserLang(): Lang | null {
  for (const tag of navigator.languages ?? []) {
    const lang = asLang(tag.split('-')[0].toLowerCase());
    if (lang) return lang;
  }
  return null;
}

function asLang(value: string | null): Lang | null {
  return SUPPORTED.find(l => l === value) ?? null;
}
