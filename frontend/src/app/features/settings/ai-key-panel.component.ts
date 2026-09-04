import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { AiApiService, AiCredentialStatus } from '../../core/api/ai.api';

/**
 * Bring your own generation key. With none stored, generation runs on whatever the
 * server is configured with — on a personal install that is the local CLI agent, so
 * leaving this empty keeps the terminal path.
 */
@Component({
  selector: 'app-ai-key-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbButtonComponent],
  templateUrl: './ai-key-panel.component.html'
})
export class AiKeyPanelComponent implements OnInit {
  private api = inject(AiApiService);

  status = signal<AiCredentialStatus | null>(null);
  loading = signal(true);
  saving = signal(false);
  error = signal('');

  provider: 'OPENAI' | 'GEMINI' = 'OPENAI';
  apiKey = '';
  model = '';

  readonly providers = [
    { value: 'OPENAI' as const, label: 'settings.aiKey.provider.openai' },
    { value: 'GEMINI' as const, label: 'settings.aiKey.provider.gemini' },
  ];

  ngOnInit(): void {
    this.api.getCredentialStatus().subscribe({
      next: s => { this.applyStatus(s); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  save(): void {
    if (!this.apiKey.trim() || this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    this.api.setCredential(this.provider, this.apiKey.trim(), this.model.trim() || undefined).subscribe({
      next: s => {
        this.applyStatus(s);
        // The key is never read back, so it must not linger in the form either.
        this.apiKey = '';
        this.saving.set(false);
      },
      error: () => { this.error.set('settings.aiKey.saveFailed'); this.saving.set(false); }
    });
  }

  clear(): void {
    this.saving.set(true);
    this.api.clearCredential().subscribe({
      next: () => {
        this.status.set({ ...this.status()!, configured: false, provider: null, hint: null, model: null });
        this.saving.set(false);
      },
      error: () => { this.error.set('settings.aiKey.clearFailed'); this.saving.set(false); }
    });
  }

  private applyStatus(s: AiCredentialStatus): void {
    this.status.set(s);
    if (s.provider) this.provider = s.provider;
    this.model = s.model ?? '';
  }
}
