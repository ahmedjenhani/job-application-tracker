import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { APP_CONFIG } from './config';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly config = inject(APP_CONFIG);

  /** Origin of the backend; requests to it get the auth header. */
  readonly base = this.config.apiUrl;

  url(path: string): string {
    return `${this.base}/api${path}`;
  }
}

/** Turns any HTTP failure into a sentence a user can act on. */
export function errorMessage(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  if (err.status === 0) {
    return 'Cannot reach the server. It may be waking up (free hosting) - please try again in a few seconds.';
  }
  const problem = err.error as { detail?: string; errors?: Record<string, string> } | null;
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join(' - ');
  }
  if (problem?.detail) {
    return problem.detail;
  }
  return err.status >= 500 ? 'The server had a problem. Please try again.' : 'The request could not be completed.';
}

/** Local calendar date as yyyy-MM-dd (never shifts by timezone like toISOString would). */
export function toIsoDate(date: Date | null | undefined): string | null {
  if (!date) {
    return null;
  }
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function parseIsoDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function todayIso(): string {
  return toIsoDate(new Date())!;
}
