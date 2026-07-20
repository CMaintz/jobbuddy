import { Injectable, signal, effect } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'jb_theme';

  readonly theme = signal<'dark' | 'light'>(this.loadTheme());

  constructor() {
    effect(() => {
      const t = this.theme();
      const html = document.documentElement;
      if (t === 'dark') {
        html.classList.add('dark');
      } else {
        html.classList.remove('dark');
      }
      try {
        localStorage.setItem(this.STORAGE_KEY, t);
      } catch { /* private mode — theme just won't persist */ }
    });
  }

  toggle(): void {
    this.theme.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  private loadTheme(): 'dark' | 'light' {
    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') return stored;
    } catch { /* private mode — fall through to default */ }
    return 'dark';
  }
}
