import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  imports: [FormsModule],
  host: { class: 'flex items-center justify-center h-full bg-gray-100 dark:bg-gray-900 font-sans' },
  selector: 'app-login',
  templateUrl: './login.html',
})
export class Login {
  protected username = '';
  protected password = '';
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected submit(): void {
    if (!this.username || !this.password) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.error.set('Неверный логин или пароль');
        this.loading.set(false);
      },
    });
  }
}
