import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WritingProfileApiService, WritingProfile } from '../../../../core/api/writing-profile.api';

const TONES = ['Professional', 'Conversational', 'Confident', 'Humble', 'Enthusiastic', 'Formal'];

@Component({
  selector: 'app-writing-style',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './writing-style.component.html'
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
