import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { AiApiService } from '../../core/api/ai.api';

interface DocPreset {
  kind: string;
  label: string;
  crumbHint: string;
  paperTitle: string | null;
  body: string;
  voice: string;
  lang: string;
  words: number;
  angles: string[];
  revisePrompts: string[];
}

const DOC_PRESETS: Record<string, DocPreset> = {
  recruiter: {
    kind: 'recruiter', label: 'Recruiter DM', crumbHint: 'LinkedIn outreach',
    paperTitle: null, voice: 'Direct', lang: 'English', words: 118,
    angles: ['Concrete result opener', 'Confident close'],
    revisePrompts: ['Make it shorter', 'Drop the bullet list', 'More personal', 'Add a line about their last launch'],
    body: `Hi Sarah,

I saw your post about the Senior Frontend role on Treasury \u2014 looks like exactly the seam I\u2019ve been working at for the last couple of years.

Quick intro: I\u2019m a senior frontend engineer in CET, 7 years at Mercury and Linear before that. Recent results that map to the JD:

  \u00b7 Rebuilt our customer dashboard around a streaming-first data layer (1.6s \u2192 380ms initial paint).
  \u00b7 Designed the dashboard motion language adopted across four products.
  \u00b7 Led the editor migration to a custom ProseMirror schema at Linear.

Open to a 15-minute chat next week? Either way \u2014 really enjoying what your team has been shipping lately.

\u2014 Marta`,
  },
  followup: {
    kind: 'followup', label: 'Follow-up', crumbHint: 'Email \u00b7 scheduled',
    paperTitle: null, voice: 'Warm', lang: 'English', words: 102,
    angles: ['Polite re-surface', 'One concrete artefact'],
    revisePrompts: ['Even shorter', 'Add a specific question', 'Less apologetic', 'Add a closing date'],
    body: `Hi Sarah,

Circling back on my application for the Frontend Engineer role from a couple of weeks ago. I wanted to flag two things you might find useful as you work through candidates:

A small write-up I did on streaming UI for AI surfaces (the bit that I think is most relevant to what you\u2019re building) \u2014 happy to share if it\u2019s of interest.

I\u2019m in CET, fully remote-friendly, and free for a call most afternoons next week.

Thanks for considering \u2014 looking forward to hearing from you.

\u2014 Marta`,
  },
  prep: {
    kind: 'prep', label: 'Interview prep', crumbHint: 'STAR drafts',
    paperTitle: 'Interview prep \u2014 Anthropic, Design Engineer',
    voice: 'Direct', lang: 'English', words: 312,
    angles: ['STAR structure', 'Cite master CV bullets'],
    revisePrompts: ['Add a STAR for project ownership', 'Quantify Mercury bullets', 'Add a Q I could ask back', 'Tighten answers to 60s each'],
    body: `LIKELY QUESTION 1
  \u201cTell me about a time you took an ambiguous brief and turned it into a shipped feature.\u201d

  S \u00b7 At Mercury, the team wanted a redesign of the Treasury dashboard but the spec was three Figma frames and a list of feature wishes.
  T \u00b7 I owned the front-end implementation end-to-end and had ~6 weeks.
  A \u00b7 I built a prototype in week one that the team could argue with, ran a tight motion-review loop, and shipped behind a feature flag with telemetry.
  R \u00b7 Rolled out company-wide; initial paint 1.6s \u2192 380ms; motion language adopted by 4 product surfaces.

LIKELY QUESTION 2
  \u201cHow do you partner with designers in practice?\u201d

  S \u00b7 At Linear, designers and engineers were on the same Slack channel but the seam was lossy.
  T \u00b7 I wanted to remove the hand-off entirely for small features.
  A \u00b7 Built a daily 30-min \u201cpolish\u201d co-working session, plus a shared component library both sides could push to.
  R \u00b7 Cut Figma-to-prod time for small UI changes from a week to ~1 day.

LIKELY QUESTION 3
  \u201cWhy this role, specifically?\u201d

  Anthropic ships product-grade AI surfaces \u2014 Artifacts, Projects, the conversation UI.
  That\u2019s the exact intersection I\u2019ve been working at, and the only team I\u2019ve found doing it with comparable taste.`,
  },
  'cold-questions': {
    kind: 'cold-questions', label: 'Cold-contact questions', crumbHint: 'Before applying \u00b7 email',
    paperTitle: 'Questions for a first email \u2014 Anthropic, Design Engineer',
    voice: 'Warm', lang: 'English', words: 168,
    angles: ['Low-pressure opener', 'Specific, researched questions'],
    revisePrompts: ['Make it warmer', 'Just 2 questions', 'Add a question about comp', 'Tighten the opener'],
    body: `A short, low-pressure note to open a conversation before applying \u2014 with 3 specific questions that show you\u2019ve done the homework and help you decide if the role is worth a full application.

Subject: Quick question about the Design Engineer role

Hi {name},

I came across the Design Engineer opening on the Product team and I\u2019m weighing whether it\u2019s the right fit before I put together a full application. Three quick questions, if you have a moment:

  1. How is the design\u2013engineering split handled day to day \u2014 do design engineers own features end to end, or pair tightly with a product designer?

  2. The role mentions prototyping novel AI interactions \u2014 how much of the work is exploratory prototyping vs. shipping production surfaces?

  3. Is the team remote-friendly for someone in CET, or is there an expectation of overlap with SF hours?

Thanks a lot \u2014 happy to share my CV and a short portfolio if it sounds like a fit.

Best,
Marta`,
  },
};

@Component({
  selector: 'app-document-view',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, DiffViewerComponent],
  templateUrl: './document-view.component.html',
})
export class DocumentViewComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);

  private kind = signal('recruiter');
  doc = signal<DocPreset>(DOC_PRESETS['recruiter']);
  refining = signal(false);
  originalText = signal('');
  refinedText = signal('');
  customRevisePrompt = '';
  mobileShowSidebar = signal(false);

  ngOnInit(): void {
    const kind = this.route.snapshot.paramMap.get('kind') || 'recruiter';
    this.kind.set(kind);
    this.doc.set(DOC_PRESETS[kind] || DOC_PRESETS['recruiter']);
  }

  refineWith(prompt: string): void {
    if (!prompt?.trim() || this.refining()) return;
    const currentContent = this.doc().body;
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
      error: () => this.refining.set(false)
    });
  }

  acceptRefinement(): void {
    const refined = this.refinedText();
    if (refined) {
      const current = this.doc();
      this.doc.set({ ...current, body: refined });
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

  goToJob(): void {
    this.router.navigate(['/jobs', 'sample']);
  }
}
