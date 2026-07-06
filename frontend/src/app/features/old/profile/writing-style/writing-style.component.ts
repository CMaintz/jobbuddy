import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WritingProfileApiService, WritingProfile } from '../../../core/api/writing-profile.api';

const TONES = ['Professional', 'Conversational', 'Confident', 'Humble', 'Enthusiastic', 'Formal'];

@Component({
  selector: 'app-writing-style',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <div>
        <h1 class="text-3xl font-bold text-gray-900">Writing Style</h1>
        <p class="text-sm text-gray-500 mt-1">
          This defines HOW you write — your voice, tone, and phrasing patterns.
          It's separate from prompt templates, which define WHAT to generate.
        </p>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else {
        <div class="card space-y-5">
          <div>
            <label class="label">Tone</label>
            <select [(ngModel)]="profile.tone" class="input">
              <option value="">— not set —</option>
              @for (t of tones; track t) {
                <option [value]="t">{{ t }}</option>
              }
            </select>
            <p class="text-xs text-gray-400 mt-1">The overall mood/register of your writing.</p>
          </div>

          <div>
            <label class="label">Vocabulary Notes</label>
            <textarea [(ngModel)]="profile.vocabularyNotes" class="input" rows="3"
                      placeholder="e.g. Prefer simple Anglo-Saxon words. Avoid jargon unless technical. Use active voice.">
            </textarea>
          </div>

          <div>
            <label class="label">Phrasing Patterns</label>
            <p class="text-xs text-gray-400 mb-2">One pattern per line — phrases or openings you like to use.</p>
            <textarea [(ngModel)]="phrasingText" class="input font-mono text-sm" rows="4"
                      placeholder="I thrive in environments where...&#10;My experience with X has taught me...&#10;I believe that...">
            </textarea>
          </div>

          <div>
            <label class="label">Example Excerpts</label>
            <p class="text-xs text-gray-400 mb-2">Paste snippets from past applications you liked. One per line.</p>
            <textarea [(ngModel)]="excerptsText" class="input font-mono text-sm" rows="5"
                      placeholder="Past application excerpt 1...&#10;---&#10;Past application excerpt 2...">
            </textarea>
          </div>

          <div class="flex items-center gap-3 pt-2">
            <button (click)="save()" [disabled]="saving" class="btn-primary">
              {{ saving ? 'Saving...' : 'Save Writing Style' }}
            </button>
            @if (saved) {
              <span class="text-sm text-green-600">Saved!</span>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class WritingStyleComponent implements OnInit {
  private api = inject(WritingProfileApiService);

  tones = TONES;
  profile: WritingProfile = {};
  phrasingText = '';
  excerptsText = '';
  loading = true;
  saving = false;
  saved = false;

  ngOnInit(): void {
    this.api.get().subscribe({
      next: p => {
        this.profile = p;
        this.phrasingText = (p.phrasingPatterns ?? []).join('\n');
        this.excerptsText = (p.exampleExcerpts ?? []).join('\n---\n');
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  save(): void {
    this.saving = true;
    const toSave: WritingProfile = {
      ...this.profile,
      phrasingPatterns: this.phrasingText.split('\n').map(s => s.trim()).filter(Boolean),
      exampleExcerpts: this.excerptsText.split('\n---\n').map(s => s.trim()).filter(Boolean)
    };
    this.api.update(toSave).subscribe({
      next: p => {
        this.profile = p;
        this.saving = false;
        this.saved = true;
        setTimeout(() => this.saved = false, 2000);
      },
      error: () => this.saving = false
    });
  }
}
