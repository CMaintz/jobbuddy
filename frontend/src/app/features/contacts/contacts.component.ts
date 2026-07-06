import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';

interface Contact {
  id: number;
  name: string;
  role: string;
  company: string;
  rel: 'recruiter' | 'hiring' | 'referral' | 'other';
  email: string;
  lastContact: string;
  warmth: 'hot' | 'warm' | 'cold' | 'new';
  note: string;
  nextAction: string | null;
}

const REL_LABELS: Record<string, string> = { recruiter: 'Recruiter', hiring: 'Hiring manager', referral: 'Referral', other: 'Other' };

const WARMTH_CONFIG: Record<string, { label: string; color: string; dot: string }> = {
  hot:  { label: 'Hot',  color: 'var(--jb-accent)',   dot: 'var(--jb-accent)' },
  warm: { label: 'Warm', color: '#7df2a8',            dot: 'var(--jb-success)' },
  cold: { label: 'Cold', color: 'var(--jb-text-dim)', dot: 'var(--jb-text-dim)' },
  new:  { label: 'New',  color: 'var(--jb-info)',     dot: 'var(--jb-info)' },
};

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, JbIconComponent, JbButtonComponent, JbPillComponent],
  templateUrl: './contacts.component.html'
})
export class ContactsComponent {
  selected = signal<Contact | null>(null);
  activeRel = signal('all');
  mobilePanel = signal<'list' | 'detail'>('list');

  relFilters = [
    { key: 'all', label: 'All' },
    { key: 'recruiter', label: 'Recruiters' },
    { key: 'hiring', label: 'Hiring' },
    { key: 'referral', label: 'Referrals' },
  ];

  // Mock data
  contacts: Contact[] = [
    { id: 1, name: 'Sarah Chen', role: 'Senior Recruiter', company: 'Stripe', rel: 'recruiter', email: 'sarah.chen@stripe.com', lastContact: '2 days ago', warmth: 'warm', note: 'Owns the frontend pipeline. Responsive. Mentioned a take-home is coming.', nextAction: 'Reply by Thu' },
    { id: 2, name: 'Tobias Lund', role: 'Eng Manager', company: 'Linear', rel: 'hiring', email: 'tobias@linear.app', lastContact: '5 days ago', warmth: 'warm', note: 'Hiring manager for Product Eng. Liked the prototype I shared.', nextAction: null },
    { id: 3, name: 'Maria Holm', role: 'Friend · referral', company: 'Anthropic', rel: 'referral', email: 'maria.holm@gmail.com', lastContact: '1 week ago', warmth: 'hot', note: 'Can refer me internally. Was a colleague at Klarna.', nextAction: 'Send CV for referral' },
    { id: 4, name: 'James Okafor', role: 'Talent Partner', company: 'Vercel', rel: 'recruiter', email: 'james@vercel.com', lastContact: '4 days ago', warmth: 'cold', note: 'Initial outreach, no reply yet. Follow up once more then drop.', nextAction: 'Final follow-up' },
    { id: 5, name: 'Priya Nair', role: 'Design Eng Lead', company: 'Figma', rel: 'hiring', email: 'priya@figma.com', lastContact: '2 weeks ago', warmth: 'cold', note: 'Phone screen done. Awaiting decision.', nextAction: null },
    { id: 6, name: 'Anders Beck', role: 'Recruiter', company: 'Notion', rel: 'recruiter', email: 'anders@notion.so', lastContact: 'Never', warmth: 'new', note: 'Found via LinkedIn. Haven\'t reached out yet.', nextAction: 'Intro DM' },
  ];

  filteredContacts(): Contact[] {
    const rel = this.activeRel();
    if (rel === 'all') return this.contacts;
    return this.contacts.filter(c => c.rel === rel);
  }

  initials(name: string): string {
    return name.split(' ').map(w => w[0]).slice(0, 2).join('');
  }

  avatarColor(name: string): string {
    let h = 0;
    for (const c of name) h = (h * 31 + c.charCodeAt(0)) % 360;
    return `oklch(0.42 0.09 ${h})`;
  }

  warmthConfig(w: string) {
    return WARMTH_CONFIG[w] || WARMTH_CONFIG['cold'];
  }

  relLabel(rel: string): string {
    return REL_LABELS[rel] || rel;
  }

  timelineFor(c: Contact): { time: string; what: string; who?: string }[] {
    return [
      { time: c.lastContact, what: 'Email exchange', who: `You \u2194 ${c.name.split(' ')[0]}` },
      { time: '1 week ago', what: 'Connected on LinkedIn' },
      { time: '2 weeks ago', what: 'Saved as contact', who: 'via job feed' },
    ];
  }
}
