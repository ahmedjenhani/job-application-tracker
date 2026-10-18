import { describe, expect, it } from 'vitest';
import { toIsoDate } from '../core/api';
import { ApplicationStatus, JobApplication } from '../core/models';
import { followUpState } from './follow-up';

function daysFromToday(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return toIsoDate(d)!;
}

function app(status: ApplicationStatus, followUpDate?: string): JobApplication {
  return { id: 1, company: 'C', role: 'R', status, followUpDate, skills: [], createdAt: '', updatedAt: '' };
}

describe('followUpState', () => {
  it('flags overdue, due today and upcoming follow-ups while waiting for a reply', () => {
    expect(followUpState(app('APPLIED', daysFromToday(-1)))).toBe('overdue');
    expect(followUpState(app('INTERVIEW', daysFromToday(0)))).toBe('today');
    expect(followUpState(app('APPLIED', daysFromToday(3)))).toBe('upcoming');
  });

  it('ignores applications that are not waiting for the company', () => {
    expect(followUpState(app('WISHLIST', daysFromToday(-5)))).toBeNull();
    expect(followUpState(app('OFFER', daysFromToday(-5)))).toBeNull();
    expect(followUpState(app('REJECTED', daysFromToday(-5)))).toBeNull();
  });

  it('is null when no follow-up date is set', () => {
    expect(followUpState(app('APPLIED'))).toBeNull();
  });
});
