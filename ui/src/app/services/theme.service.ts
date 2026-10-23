import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'technova-theme';
const DARK_MODE_QUERY = '(prefers-color-scheme: dark)';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly root = this.document.documentElement;
  private readonly mediaQuery = this.document.defaultView?.matchMedia(DARK_MODE_QUERY);
  private readonly selectedTheme = signal<Theme | null>(this.readStoredTheme());
  private readonly systemTheme = signal<Theme>(this.mediaQuery?.matches ? 'dark' : 'light');

  readonly theme = computed<Theme>(() => this.selectedTheme() ?? this.systemTheme());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    this.applyTheme(this.theme());
    this.mediaQuery?.addEventListener('change', this.handleSystemThemeChange);
  }

  toggle(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.selectedTheme.set(theme);
    this.writeStoredTheme(theme);
    this.applyTheme(theme);
  }

  private readonly handleSystemThemeChange = (event: MediaQueryListEvent): void => {
    const theme: Theme = event.matches ? 'dark' : 'light';
    this.systemTheme.set(theme);
    if (this.selectedTheme() === null) {
      this.applyTheme(theme);
    }
  };

  private applyTheme(theme: Theme): void {
    this.root.dataset['theme'] = theme;
    this.root.style.colorScheme = theme;
  }

  private readStoredTheme(): Theme | null {
    try {
      const storedTheme = this.document.defaultView?.localStorage.getItem(THEME_STORAGE_KEY);
      return storedTheme === 'dark' || storedTheme === 'light' ? storedTheme : null;
    } catch {
      return null;
    }
  }

  private writeStoredTheme(theme: Theme): void {
    try {
      this.document.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Storage may be unavailable in privacy-restricted browser contexts.
    }
  }
}
