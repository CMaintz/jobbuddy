import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Nudge } from '../../core/api/nudges.api';
import { nudgeQueueItem } from './nudge-queue-item';
import { NudgeQueueRowComponent } from './nudge-queue-row.component';

describe('NudgeQueueRowComponent', () => {
  function interviewToday(time: string): Nudge {
    const d = new Date();
    const pad = (v: number) => String(v).padStart(2, '0');
    return {
      type: 'INTERVIEW_SOON', applicationId: 'a1', jobId: null, outreachId: null, reminderId: null,
      jobTitle: 'Kommunikationsmedarbejder', companyName: 'Acme A/S',
      deadline: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
      daysSinceApplied: null, note: null, time,
    };
  }

  function render(nudge: Nudge): string {
    const fixture = TestBed.createComponent(NudgeQueueRowComponent);
    fixture.componentRef.setInput('item', nudgeQueueItem(nudge, 'Acme A/S'));
    fixture.detectChanges();
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NudgeQueueRowComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('da', {
      dashboard: {
        queue: { today: 'i dag', interview: 'Jobsamtale', interviewAt: 'Jobsamtale kl. {{time}}' },
        tag: { interview: 'samtale' },
      },
    });
    translate.use('da');
  });

  it('shows the day and the clock time of an interview', () => {
    const text = render(interviewToday('10:00:00'));

    expect(text).toContain('i dag');
    expect(text).toContain('Jobsamtale kl. 10:00');
  });
});
