import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';

interface Prompt {
  id: number;
  kind: string;
  name: string;
  body: string;
  tags: string[];
  lang: string;
  uses: number;
  fav: boolean;
  updated: string;
}

const PROMPT_KINDS = [
  { key: 'cv', label: 'Angled CV', icon: 'doc', color: 'var(--jb-violet)' },
  { key: 'application', label: 'Application', icon: 'layers', color: 'var(--jb-accent)' },
  { key: 'cl', label: 'Cover letter', icon: 'doc', color: 'var(--jb-info)' },
  { key: 'recruiter', label: 'Recruiter email', icon: 'mail', color: '#7df2a8' },
  { key: 'followup', label: 'Follow-up', icon: 'chat', color: 'var(--jb-accent)' },
  { key: 'other', label: 'Other', icon: 'lightbulb', color: 'var(--jb-text-dim)' },
];

@Component({
  selector: 'app-prompts-library',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent],
  templateUrl: './prompts-library.component.html'
})
export class PromptsLibraryComponent {
  activeKind = signal('all');
  openPrompt = signal<Prompt | null>(null);
  promptKinds = PROMPT_KINDS;

  // Mock data
  prompts: Prompt[] = [
    { id: 1, kind: 'application', name: 'DK \u00b7 Ans\u00f8gning \u2014 warm + concrete result', body: 'Open with one concrete result from my master CV that maps to the JD\'s primary hire-for. Three short paragraphs in Danish. End with availability and one specific question.', tags: ['dansk', 'warm'], lang: 'dansk', uses: 14, fav: true, updated: '2d' },
    { id: 2, kind: 'application', name: 'EN \u00b7 Ans\u00f8gning \u2014 formal', body: 'Open with the company\'s last shipped product I admire. Map two bullets from master CV. Formal close.', tags: ['english', 'formal'], lang: 'english', uses: 6, fav: false, updated: '1w' },
    { id: 3, kind: 'cl', name: 'Short cover \u00b7 220\u2013260 words', body: 'Three paragraphs. First: a single concrete result. Second: why this company specifically. Third: short close. No fluff.', tags: ['english', 'short'], lang: 'english', uses: 21, fav: true, updated: '6h' },
    { id: 4, kind: 'cv', name: 'Angled CV \u2014 quantify-everything', body: 'Re-angle the profile to lead with the JD\'s primary hire-for. Add numbers to every bullet. Cut anything older than 5 years if it doesn\'t map.', tags: ['quantify'], lang: 'either', uses: 9, fav: true, updated: '3d' },
    { id: 5, kind: 'cv', name: 'Angled CV \u2014 motion / craft lead', body: 'Lead with motion and craft. Promote any prototype work. Demote pure infra bullets.', tags: ['motion'], lang: 'either', uses: 4, fav: false, updated: '11d' },
    { id: 6, kind: 'recruiter', name: 'Recruiter DM \u2014 friendly', body: 'Hi {name}, saw your post about {role}. Quick intro: I\'ve been doing {result}. If it sounds relevant I\'d love a 15-min chat.', tags: ['linkedin'], lang: 'english', uses: 11, fav: false, updated: '4d' },
    { id: 7, kind: 'recruiter', name: 'Recruiter DM \u2014 direct', body: 'Hi {name}, I\'m a senior frontend engineer. Three relevant results: {bullets}. Open to a chat?', tags: ['linkedin', 'short'], lang: 'english', uses: 3, fav: false, updated: '2w' },
    { id: 8, kind: 'followup', name: 'Polite follow-up \u2014 5 days', body: 'Hi {name}, circling back on my application for {role}. Happy to share more about {specific-thing} if useful.', tags: ['english'], lang: 'english', uses: 7, fav: false, updated: '3d' },
    { id: 9, kind: 'other', name: 'Interview prep \u2014 STAR drafts', body: 'For each of the top 3 likely Qs from the JD, draft a STAR answer using master CV bullets. ~80 words each.', tags: ['interview'], lang: 'english', uses: 2, fav: true, updated: '1d' },
  ];

  filteredPrompts(): Prompt[] {
    const kind = this.activeKind();
    if (kind === 'all') return this.prompts;
    return this.prompts.filter(p => p.kind === kind);
  }

  kindConfig(kind: string) {
    return PROMPT_KINDS.find(k => k.key === kind) || { key: kind, label: kind, icon: 'doc', color: 'var(--jb-text-dim)' };
  }
}
