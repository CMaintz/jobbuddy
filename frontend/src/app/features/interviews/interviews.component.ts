import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';

interface PrepItem { done: boolean; text: string; }

interface Interview {
  id: number;
  company: string;
  role: string;
  when: string;
  date: string;
  round: string;
  type: string;
  status: 'upcoming' | 'done';
  people: string[];
  format: string;
  prep?: PrepItem[];
  outcome?: 'advanced' | 'rejected' | 'pending';
  debrief?: string;
}

const OUTCOME_CONFIG: Record<string, { label: string; tone: 'success' | 'danger' | 'accent' }> = {
  advanced: { label: 'Advanced', tone: 'success' },
  rejected: { label: 'Rejected', tone: 'danger' },
  pending:  { label: 'Awaiting', tone: 'accent' },
};

@Component({
  selector: 'app-interviews',
  standalone: true,
  imports: [CommonModule, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent],
  templateUrl: './interviews.component.html'
})
export class InterviewsComponent {
  activeTab = signal<'upcoming' | 'past'>('upcoming');

  interviews: Interview[] = [
    {
      id: 1, company: 'Stripe', role: 'Frontend Engineer', when: 'Tomorrow · 09:30', date: 'Thu 16 May',
      round: '2 of 4', type: 'Technical · pairing', status: 'upcoming',
      people: ['Sarah Chen (recruiter)', 'Diego R. (staff eng)', 'Mia T. (eng)'],
      format: 'Video · 60 min · CoderPad',
      prep: [
        { done: true, text: 'Review their public API design docs' },
        { done: true, text: 'Re-do a streaming-UI warm-up' },
        { done: false, text: 'Prepare 2 questions about the team' },
        { done: false, text: 'Test camera + CoderPad setup' },
      ],
    },
    {
      id: 2, company: 'Plaid', role: 'Software Engineer, Web', when: 'Wed · 14:00', date: 'Wed 15 May',
      round: '1 of 3', type: 'Recruiter screen', status: 'upcoming',
      people: ['James Park (recruiter)'],
      format: 'Phone · 20 min',
      prep: [
        { done: false, text: 'Prepare salary expectations range' },
        { done: false, text: 'One-line "why Plaid"' },
      ],
    },
    {
      id: 3, company: 'Anthropic', role: 'Design Engineer', when: 'Mon 21 May', date: 'Mon 21 May',
      round: '1 of 4', type: 'Hiring manager', status: 'upcoming',
      people: ['Alex M. (EM)'],
      format: 'Video · 45 min',
      prep: [
        { done: false, text: 'Read up on Artifacts + recent launches' },
        { done: false, text: 'Prepare design-eng portfolio walkthrough' },
      ],
    },
    {
      id: 4, company: 'Figma', role: 'Design Engineer', when: '2 weeks ago', date: 'Tue 30 Apr',
      round: '2 of 3', type: 'Technical', status: 'done', outcome: 'advanced',
      people: ['Priya N. (lead)'],
      format: 'Video · 60 min',
      debrief: 'Went well. Pairing on a canvas-rendering bug \u2014 got it. They liked my motion questions. Next: team-fit round.',
    },
    {
      id: 5, company: 'Mercury', role: 'Senior Frontend', when: '3 weeks ago', date: 'Mon 22 Apr',
      round: '1 of 3', type: 'Recruiter screen', status: 'done', outcome: 'advanced',
      people: ['Recruiter'],
      format: 'Phone · 25 min',
      debrief: 'Standard screen. Comp aligned. Advanced to take-home.',
    },
    {
      id: 6, company: 'Webflow', role: 'Frontend Engineer', when: '1 month ago', date: 'Apr',
      round: 'Final', type: 'Onsite loop', status: 'done', outcome: 'rejected',
      people: ['Panel of 4'],
      format: 'Onsite · 4h',
      debrief: 'Strong on craft, but they wanted deeper backend. Good practice. Ask for feedback notes.',
    },
  ];

  get upcoming() { return this.interviews.filter(i => i.status === 'upcoming'); }
  get past() { return this.interviews.filter(i => i.status === 'done'); }

  summaryCards = [
    { label: 'Upcoming', value: 3, color: 'var(--jb-accent)' },
    { label: 'This week', value: 2, color: 'var(--jb-info)' },
    { label: 'Advanced', value: 2, color: 'var(--jb-success)' },
    { label: 'Conversion', value: '67%', color: 'var(--jb-text)' },
  ];

  tabs = [
    { key: 'upcoming' as const, label: 'Upcoming', count: 3 },
    { key: 'past' as const, label: 'Past', count: 3 },
  ];

  shownInterviews(): Interview[] {
    return this.activeTab() === 'upcoming' ? this.upcoming : this.past;
  }

  doneCount(prep: PrepItem[]): number {
    return prep.filter(p => p.done).length;
  }

  outcomeConfig(outcome: string) {
    return OUTCOME_CONFIG[outcome] || OUTCOME_CONFIG['pending'];
  }
}
