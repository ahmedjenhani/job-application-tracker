import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { errorMessage, parseIsoDate, todayIso, toIsoDate } from './api';

describe('date helpers', () => {
  it('formats a local date without shifting it across timezones', () => {
    // late evening local time is exactly where toISOString() would roll over to the next UTC day
    expect(toIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
    expect(toIsoDate(new Date(2026, 11, 31, 0, 1))).toBe('2026-12-31');
  });

  it('round-trips through parseIsoDate', () => {
    const parsed = parseIsoDate('2026-03-09')!;
    expect(parsed.getFullYear()).toBe(2026);
    expect(parsed.getMonth()).toBe(2);
    expect(parsed.getDate()).toBe(9);
    expect(toIsoDate(parsed)).toBe('2026-03-09');
  });

  it('handles empty values', () => {
    expect(toIsoDate(null)).toBeNull();
    expect(parseIsoDate(undefined)).toBeNull();
    expect(parseIsoDate('')).toBeNull();
  });

  it('todayIso is a yyyy-MM-dd string', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('errorMessage', () => {
  const http = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

  it('explains an unreachable server (cold start on free hosting)', () => {
    expect(errorMessage(http(0))).toContain('waking up');
  });

  it('uses the detail of a problem response', () => {
    expect(errorMessage(http(409, { detail: 'An account with this email already exists' }))).toBe(
      'An account with this email already exists',
    );
  });

  it('lists field errors for validation failures', () => {
    const message = errorMessage(http(400, { detail: 'Validation failed', errors: { email: 'must be valid', password: 'too short' } }));
    expect(message).toContain('email: must be valid');
    expect(message).toContain('password: too short');
  });

  it('has a friendly fallback for server errors and unknown values', () => {
    expect(errorMessage(http(500))).toContain('server had a problem');
    expect(errorMessage(new Error('boom'))).toContain('Something went wrong');
  });
});
