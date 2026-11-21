import { Nudge } from '../../core/api/nudges.api';

export interface QueueItem {
  time: string;
  what: string;
  detail: string;
  tag: string;
  tone: 'accent' | 'info' | 'danger' | 'neutral' | 'violet' | 'success';
  hot: boolean;
}

/**
 * One row of the dashboard's "today" queue for a nudge. `detail` is the company and role line,
 * resolved by the caller because it may need the application list.
 */
export function nudgeQueueItem(n: Nudge, detail: string): QueueItem {
  const overdue = !!n.deadline && new Date(n.deadline).getTime() < Date.now();

  if (n.type === 'REMINDER_DUE') {
    return {
      time: overdue ? 'dashboard.queue.now' : 'dashboard.queue.today',
      what: n.note || 'dashboard.queue.followUp',
      detail,
      tag: 'dashboard.tag.followUp',
      tone: overdue ? 'danger' : 'info',
      hot: overdue,
    };
  }
  if (n.type === 'OUTREACH_FOLLOW_UP') {
    return {
      time: overdue ? 'dashboard.queue.now' : 'dashboard.queue.today',
      what: 'dashboard.queue.outreachFollowUp',
      detail: n.note ? `${n.companyName ?? ''} · ${n.note}` : detail,
      tag: 'dashboard.tag.outreach',
      tone: overdue ? 'danger' : 'info',
      hot: overdue,
    };
  }
  if (n.type === 'INTERVIEW_SOON') return interviewItem(n, detail);
  if (n.type === 'DEADLINE_SOON') {
    const daysLeft = Math.max(0, Math.ceil((new Date(n.deadline ?? '').getTime() - Date.now()) / 86400000));
    return {
      time: daysLeft === 0 ? 'dashboard.queue.today' : `${daysLeft}d`,
      what: 'dashboard.queue.applyDeadline',
      detail,
      tag: 'dashboard.tag.deadline',
      tone: daysLeft <= 2 ? 'danger' : 'info',
      hot: daysLeft <= 2,
    };
  }
  return {
    time: `${n.daysSinceApplied}d`,
    what: 'dashboard.queue.noReply',
    detail,
    tag: 'dashboard.tag.noReply',
    tone: 'violet',
    hot: false,
  };
}

/** The backend only sends interviews for today or tomorrow, so the date is one of the two. */
function interviewItem(n: Nudge, detail: string): QueueItem {
  const today = n.deadline === localIsoDate(new Date());
  return {
    time: today ? 'dashboard.queue.today' : 'dashboard.queue.tomorrow',
    what: 'dashboard.queue.interview',
    detail,
    tag: 'dashboard.tag.interview',
    tone: 'accent',
    hot: today,
  };
}

/** The user's calendar date, matching the backend's plain date rather than a UTC instant. */
function localIsoDate(d: Date): string {
  const pad = (v: number) => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
