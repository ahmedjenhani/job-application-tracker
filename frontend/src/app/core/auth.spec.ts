import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService, authInterceptor } from './auth';
import { APP_CONFIG } from './config';
import { AuthResponse } from './models';

const RESPONSE: AuthResponse = { token: 'jwt-123', user: { id: 1, email: 'jane@example.com', fullName: 'Jane' } };

describe('AuthService and authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: { apiUrl: 'http://api.test' } },
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('starts logged out', () => {
    expect(auth.isLoggedIn()).toBe(false);
    expect(auth.user()).toBeNull();
  });

  it('stores the session after login and clears it on logout', () => {
    auth.login('jane@example.com', 'secret').subscribe();
    backend.expectOne('http://api.test/api/auth/login').flush(RESPONSE);

    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.user()?.fullName).toBe('Jane');
    expect(localStorage.getItem('jt_token')).toBe('jwt-123');

    auth.logout();
    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('jt_token')).toBeNull();
  });

  it('sends the bearer token to the API only', () => {
    auth.login('jane@example.com', 'secret').subscribe();
    backend.expectOne('http://api.test/api/auth/login').flush(RESPONSE);

    http.get('http://api.test/api/applications').subscribe();
    expect(backend.expectOne('http://api.test/api/applications').request.headers.get('Authorization')).toBe(
      'Bearer jwt-123',
    );

    http.get('https://other.example.com/data').subscribe();
    expect(backend.expectOne('https://other.example.com/data').request.headers.has('Authorization')).toBe(false);
  });

  it('logs the user out when the API answers 401 on a protected call', () => {
    auth.login('jane@example.com', 'secret').subscribe();
    backend.expectOne('http://api.test/api/auth/login').flush(RESPONSE);

    http.get('http://api.test/api/applications').subscribe({ error: () => undefined });
    backend.expectOne('http://api.test/api/applications').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
  });

  it('keeps the session when a login attempt itself fails with 401', () => {
    auth.login('jane@example.com', 'wrong').subscribe({ error: () => undefined });
    backend
      .expectOne('http://api.test/api/auth/login')
      .flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false); // never logged in, and no redirect loop
  });
});
