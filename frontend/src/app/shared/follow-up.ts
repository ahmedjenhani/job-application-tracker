import { todayIso } from '../core/api';
import { JobApplication } from '../core/models';

export type FollowUpState = 'overdue' | 'today' | 'upcoming' | null;

/** Follow-ups only matter while we are still waiting for the company to answer. */
export function followUpState(app: JobApplication): FollowUpState {
  if ((app.status !== 'APPLIED' && app.status !== 'INTERVIEW') || !app.followUpDate) {
    return null;
  }
  const today = todayIso();
  if (app.followUpDate < today) {
    return 'overdue';
  }
  return app.followUpDate === today ? 'today' : 'upcoming';
}
