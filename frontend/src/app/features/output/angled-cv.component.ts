import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';

interface CvTemplate {
  key: string;
  label: string;
  sub: string;
  bg: string;
  fg: string;
  muted: string;
  divider: string;
  font: string;
  size: number;
  lineH: number;
  nameSize: number;
  nameWeight: number;
  sectionStyle: 'caps' | 'underline';
  sidebar: boolean;
  swatchBg: string;
  swatchAccent: string;
}

const CV_TEMPLATES: CvTemplate[] = [
  {
    key: 'editorial', label: 'Editorial', sub: 'Cream paper - two-column header',
    bg: '#f6f3ec', fg: '#1a1714', muted: '#6b6660', divider: '#d9d2c4',
    font: '"Geist", ui-sans-serif, system-ui', size: 11.5, lineH: 1.55,
    nameSize: 22, nameWeight: 500, sectionStyle: 'caps', sidebar: false,
    swatchBg: '#f6f3ec', swatchAccent: '#1a1714',
  },
  {
    key: 'classic', label: 'Classic', sub: 'Serif - centered name',
    bg: '#fbfaf6', fg: '#1c1a16', muted: '#5a554e', divider: '#cfc8b9',
    font: '"Source Serif Pro", Charter, Cambria, Georgia, serif', size: 12, lineH: 1.6,
    nameSize: 26, nameWeight: 600, sectionStyle: 'underline', sidebar: false,
    swatchBg: '#fbfaf6', swatchAccent: '#5a554e',
  },
  {
    key: 'sidebar', label: 'Sidebar', sub: 'White - skills column on the right',
    bg: '#ffffff', fg: '#0e0e0e', muted: '#5a5a5a', divider: '#e5e0d4',
    font: '"Geist", ui-sans-serif, system-ui', size: 11.5, lineH: 1.55,
    nameSize: 22, nameWeight: 600, sectionStyle: 'caps', sidebar: true,
    swatchBg: '#ffffff', swatchAccent: '#f5a623',
  },
];

interface CvBullet {
  id: number;
  text: string;
  mode: 'keep' | 'cut' | 'moved' | 'add';
  diffLabel?: string;
}

interface CvExperience {
  role: string;
  company: string;
  dates: string;
  bullets: CvBullet[];
}

@Component({
  selector: 'app-angled-cv',
  standalone: true,
  imports: [CommonModule, JbIconComponent],
  templateUrl: './angled-cv.component.html',
})
export class AngledCvComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  mode = signal<'diff' | 'clean'>('diff');
  template = signal('editorial');
  tplOpen = signal(false);
  mobileShowSidebar = signal(false);

  templates = CV_TEMPLATES;
  modes = [
    { key: 'diff', label: 'Diff' },
    { key: 'clean', label: 'Clean' },
  ];

  experience: CvExperience[] = [
    {
      role: 'Senior Frontend Engineer', company: 'Mercury', dates: '2021 - present',
      bullets: [
        { id: 1, text: 'Rebuilt customer dashboard around a streaming-first data layer (1.6s -> 380ms initial paint).', mode: 'keep' },
        { id: 2, text: 'Designed the dashboard motion language adopted across four product surfaces.', mode: 'moved' },
        { id: 3, text: 'Set up CI pipeline and automated visual regression tests.', mode: 'cut' },
        { id: 4, text: 'Built accessible component library used by 3 product teams.', mode: 'add' },
      ],
    },
    {
      role: 'Frontend Engineer', company: 'Linear', dates: '2019 - 2021',
      bullets: [
        { id: 5, text: 'Led the editor migration to a custom ProseMirror schema.', mode: 'keep' },
        { id: 6, text: 'Built daily 30-min design-eng co-working session; cut Figma-to-prod from 1 week to ~1 day.', mode: 'keep' },
        { id: 7, text: 'Polished micro-interactions across 12 core views.', mode: 'cut' },
      ],
    },
    {
      role: 'Frontend Developer', company: 'Klarna', dates: '2017 - 2019',
      bullets: [
        { id: 8, text: 'Shipped checkout redesign serving 4M monthly sessions.', mode: 'keep' },
        { id: 9, text: 'Prototyped AI-powered product recommendations surface.', mode: 'add' },
      ],
    },
  ];

  changesList = [
    { type: 'edit', label: 'Profile rewritten to lead with design-eng seam' },
    { type: 'edit', label: 'Bullet "streaming-first" tightened + numbers added' },
    { type: 'move', label: 'Mercury bullets reordered (motion up)' },
    { type: 'cut', label: '2 bullets cut (CI work, polish notes)' },
    { type: 'add', label: 'Skills line emphasises React + Motion + a11y' },
  ];

  keywords = [
    { term: 'React', hit: true }, { term: 'TypeScript', hit: true },
    { term: 'design systems', hit: true }, { term: 'motion', hit: true },
    { term: 'prototypes', hit: true }, { term: 'AI products', hit: true },
    { term: 'streaming UIs', hit: true }, { term: 'accessibility', hit: true },
    { term: 'Figma', hit: true }, { term: 'developer tools', hit: false },
  ];

  anglePrompts = ['More technical depth', 'Lead with motion / craft', 'Quantify everything', 'Shorter - one page'];

  activeTemplate(): CvTemplate {
    return this.templates.find(t => t.key === this.template()) ?? this.templates[0];
  }

  toolbarSubtitle(): string {
    return this.mode() === 'diff'
      ? '6 of 14 bullets kept - 2 cut - profile re-angled - diff on'
      : '6 bullets - profile re-angled - clean view';
  }

  sectionLabelClass(): string {
    const t = this.activeTemplate();
    return t.sectionStyle === 'underline'
      ? 'text-xs font-semibold uppercase tracking-widest mb-2 pb-1'
      : 'text-2xs uppercase tracking-widest mb-1.5';
  }

  changeBg(type: string): string {
    switch (type) {
      case 'cut': return 'rgba(248,113,113,0.12)';
      case 'add': return 'rgba(74,222,128,0.12)';
      case 'move': return 'rgba(122,162,247,0.12)';
      default: return 'rgba(245,166,35,0.12)';
    }
  }

  changeColor(type: string): string {
    switch (type) {
      case 'cut': return '#f4a8a8';
      case 'add': return '#7df2a8';
      case 'move': return '#9fbdf9';
      default: return 'var(--jb-accent-2)';
    }
  }

  ngOnInit(): void {}

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }
}
