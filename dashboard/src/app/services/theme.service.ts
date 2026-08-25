import { Injectable, signal } from '@angular/core';

type Theme = 'light' | 'dark';

const THEME_KEY = 'telemetry_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  // Тёмная — исторический дефолт этого дашборда (весь UI проектировался под неё), поэтому
  // при отсутствии сохранённого выбора остаёмся тёмными, а не берём системную тему.
  readonly theme = signal<Theme>((localStorage.getItem(THEME_KEY) as Theme | null) ?? 'dark');

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    localStorage.setItem(THEME_KEY, next);
    this.apply(next);
  }

  private apply(theme: Theme): void {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }
}
