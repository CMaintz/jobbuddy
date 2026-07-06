import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { AiApiService } from '../../core/api/ai.api';

interface LetterTemplate {
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
  headerStyle: 'columns' | 'centered' | 'rail';
  rail: boolean;
  swatchBg: string;
  swatchAccent: string;
}

const LETTER_TEMPLATES: LetterTemplate[] = [
  {
    key: 'editorial', label: 'Editorial', sub: 'Calm serif headers - cream paper',
    bg: '#f6f3ec', fg: '#1a1714', muted: '#6b6660', divider: '#d9d2c4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.65,
    headerStyle: 'columns', rail: false, swatchBg: '#f6f3ec', swatchAccent: '#1a1714',
  },
  {
    key: 'classic', label: 'Classic', sub: 'Serif body - centered header',
    bg: '#fbfaf6', fg: '#1c1a16', muted: '#5a554e', divider: '#cfc8b9',
    font: '"Source Serif Pro", Charter, Cambria, Georgia, serif', size: 13, lineH: 1.78,
    headerStyle: 'centered', rail: false, swatchBg: '#fbfaf6', swatchAccent: '#5a554e',
  },
  {
    key: 'bold', label: 'Bold', sub: 'White paper - amber rail accent',
    bg: '#ffffff', fg: '#0e0e0e', muted: '#5a5a5a', divider: '#e5e0d4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.62,
    headerStyle: 'rail', rail: true, swatchBg: '#ffffff', swatchAccent: '#f5a623',
  },
];

@Component({
  selector: 'app-application-output',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, DiffViewerComponent],
  templateUrl: './application-output.component.html',
})
export class ApplicationOutputComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);

  format = signal<'app' | 'cl' | 'dm'>('app');
  template = signal('editorial');
  tplOpen = signal(false);
  refining = signal(false);
  originalText = signal('');
  refinedText = signal('');
  customRevisePrompt = '';
  mobileShowSidebar = signal(false);

  templates = LETTER_TEMPLATES;
  formats = [
    { key: 'app', label: 'Application', words: '~450w' },
    { key: 'cl', label: 'Cover letter', words: '~250w' },
    { key: 'dm', label: 'Short pitch', words: '~120w' },
  ];

  writingAngles = ['Concrete result opener', 'Why this company', 'Confident close'];
  revisePrompts = [
    'Make the second paragraph tighter',
    'Add a line about open-source',
    'Sound less like a job ad',
    'Shorter by 50 words',
  ];

  sampleBody = [
    'I am writing to express my interest in the Design Engineer position at Anthropic. With seven years of experience building polished web products at Mercury, Linear, and Klarna, I bring both deep technical expertise and strong design sensibility to the role.',
    'At Mercury, I rebuilt the customer dashboard around a streaming-first data layer, reducing initial paint from 1.6 seconds to 380 milliseconds. I also designed the motion language that was adopted across four product surfaces, demonstrating my ability to bridge engineering and design.',
    'At Linear, I led the editor migration to a custom ProseMirror schema, working closely with designers in daily co-working sessions that cut Figma-to-production time from one week to roughly one day.',
    'I am particularly drawn to Anthropic because you ship product-grade AI surfaces with taste and craft. The intersection of design engineering and AI products is exactly where I want to be.',
  ];

  activeTemplate(): LetterTemplate {
    return this.templates.find(t => t.key === this.template()) ?? this.templates[0];
  }

  paperSubtitle(): string {
    const fmt = this.format();
    const words = fmt === 'app' ? '447' : fmt === 'cl' ? '232' : '118';
    return `${words} words - highlights on`;
  }

  stats() {
    const fmt = this.format();
    return [
      { label: 'Words', value: fmt === 'app' ? '447' : fmt === 'cl' ? '232' : '118' },
      { label: 'Reading', value: fmt === 'app' ? '2:15' : fmt === 'cl' ? '1:05' : '0:30' },
      { label: 'Keywords matched', value: '9 / 10' },
      { label: 'Originality', value: '74%' },
    ];
  }

  ngOnInit(): void {
    const fmt = this.route.snapshot.queryParamMap.get('format');
    if (fmt === 'cl' || fmt === 'dm') this.format.set(fmt);
  }

  refineWith(prompt: string): void {
    if (!prompt?.trim() || this.refining()) return;
    const currentContent = this.sampleBody.join('\n\n');
    this.originalText.set(currentContent);
    this.refining.set(true);

    this.aiApi.refine({
      currentContent,
      userMessage: prompt,
    }).subscribe({
      next: (resp) => {
        this.refinedText.set(resp.refinedContent);
        this.refining.set(false);
      },
      error: () => {
        this.refining.set(false);
      }
    });
  }

  acceptRefinement(): void {
    const refined = this.refinedText();
    if (refined) {
      this.sampleBody = refined.split('\n\n').filter(p => p.trim());
      this.originalText.set('');
      this.refinedText.set('');
    }
  }

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }
}
