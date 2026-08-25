import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { apiBaseUrl } from './api-base-url';

interface LoginResponse {
  token: string;
  username: string;
  role: 'OPERATOR' | 'ADMIN';
}

const TOKEN_KEY = 'telemetry_token';
const USERNAME_KEY = 'telemetry_username';
const ROLE_KEY = 'telemetry_role';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${apiBaseUrl()}/auth`;

  protected readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly isAuthenticated = () => this.tokenSignal() != null;
  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly role = signal<string | null>(localStorage.getItem(ROLE_KEY));

  constructor(private readonly http: HttpClient) {}

  get token(): string | null {
    return this.tokenSignal();
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { username, password }).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        localStorage.setItem(USERNAME_KEY, response.username);
        localStorage.setItem(ROLE_KEY, response.role);
        this.tokenSignal.set(response.token);
        this.username.set(response.username);
        this.role.set(response.role);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ROLE_KEY);
    this.tokenSignal.set(null);
    this.username.set(null);
    this.role.set(null);
  }
}
