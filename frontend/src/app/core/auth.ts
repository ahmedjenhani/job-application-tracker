import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { Api } from './api';
import { AuthResponse, User } from './models';

const TOKEN_KEY = 'jt_token';
const USER_KEY = 'jt_user';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // storage can be unavailable (private mode); the session then lasts until reload
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(read(TOKEN_KEY));
  private readonly userSignal = signal<User | null>(this.readUser());

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.tokenSignal() !== null);

  login(email: string, password: string) {
    return this.http
      .post<AuthResponse>(this.api.url('/auth/login'), { email, password })
      .pipe(tap((res) => this.startSession(res)));
  }

  register(fullName: string, email: string, password: string) {
    return this.http
      .post<AuthResponse>(this.api.url('/auth/register'), { fullName, email, password })
      .pipe(tap((res) => this.startSession(res)));
  }

  logout(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    write(TOKEN_KEY, null);
    write(USER_KEY, null);
    void this.router.navigate(['/login']);
  }

  private startSession(res: AuthResponse): void {
    this.tokenSignal.set(res.token);
    this.userSignal.set(res.user);
    write(TOKEN_KEY, res.token);
    write(USER_KEY, JSON.stringify(res.user));
  }

  private readUser(): User | null {
    const raw = read(USER_KEY);
    try {
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  }
}

/** Adds the bearer token to API calls and logs the user out when the token is no longer valid. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const api = inject(Api);

  const token = auth.token();
  const authenticated =
    token && req.url.startsWith(api.base) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authenticated).pipe(
    catchError((err) => {
      if (err?.status === 401 && !req.url.includes('/auth/')) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
