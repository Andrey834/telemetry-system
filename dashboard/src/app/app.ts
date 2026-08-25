import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './services/theme.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  // Инжектится здесь просто чтобы конструктор ThemeService (applies .dark на <html>) отработал
  // при старте приложения, а не только при первом обращении из компонента, который его использует.
  private readonly theme = inject(ThemeService);
}
