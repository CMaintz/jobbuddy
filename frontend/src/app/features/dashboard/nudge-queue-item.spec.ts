import { Nudge } from '../../core/api/nudges.api';
import { nudgeQueueItem } from './nudge-queue-item';

describe('nudgeQueueItem', () => {
  function isoDate(daysFromToday: number): string {
    const d = new Date();
    d.setDate(d.getDate() + daysFromToday);
    const pad = (v: number) => String(v).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  function interview(date: string, time: string | null = null): Nudge {
    return {
      type: 'INTERVIEW_SOON', applicationId: 'a1', jobId: null, outreachId: null, reminderId: null,
      jobTitle: 'Kommunikationsmedarbejder', companyName: 'Acme A/S', deadline: date,
      daysSinceApplied: null, note: null, time,
    };
  }

  it('shows an interview today as an interview, flagged for today', () => {
    const item = nudgeQueueItem(interview(isoDate(0)), 'Acme A/S');

    expect(item.what).toBe('dashboard.queue.interview');
    expect(item.time).toBe('dashboard.queue.today');
    expect(item.hot).toBeTrue();
  });

  it('shows an interview tomorrow as tomorrow, not as a missing reply', () => {
    const item = nudgeQueueItem(interview(isoDate(1)), 'Acme A/S');

    expect(item.what).toBe('dashboard.queue.interview');
    expect(item.time).toBe('dashboard.queue.tomorrow');
    expect(item.hot).toBeFalse();
  });

  it('puts the interview time on an interview today, to the minute', () => {
    const item = nudgeQueueItem(interview(isoDate(0), '10:00:00'), 'Acme A/S');

    expect(item.what).toBe('dashboard.queue.interviewAt');
    expect(item.whatParams).toEqual({ time: '10:00' });
  });

  it('puts the interview time on an interview tomorrow', () => {
    const item = nudgeQueueItem(interview(isoDate(1), '14:30:00'), 'Acme A/S');

    expect(item.time).toBe('dashboard.queue.tomorrow');
    expect(item.what).toBe('dashboard.queue.interviewAt');
    expect(item.whatParams).toEqual({ time: '14:30' });
  });

  it('leaves the time out when the interview has none, rather than show an empty clock', () => {
    const item = nudgeQueueItem(interview(isoDate(0), null), 'Acme A/S');

    expect(item.what).toBe('dashboard.queue.interview');
    expect(item.whatParams).toBeUndefined();
  });
});
