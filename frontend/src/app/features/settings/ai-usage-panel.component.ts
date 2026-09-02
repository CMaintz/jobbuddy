import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AiApiService, AiUsageSummary } from '../../core/api/ai.api';

/**
 * What the user's generations have cost so far. There is no quota — the app is
 * single-user, so this is information, not a budget.
 */
@Component({
  selector: 'app-ai-usage-panel',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './ai-usage-panel.component.html'
})
export class AiUsagePanelComponent implements OnInit {
  private api = inject(AiApiService);

  usage = signal<AiUsageSummary | null>(null);
  loading = signal(true);
  loadError = signal(false);

  ngOnInit(): void {
    this.api.getUsage().subscribe({
      next: (u) => { this.usage.set(u); this.loading.set(false); },
      error: () => { this.loadError.set(true); this.loading.set(false); }
    });
  }

  /** True once anything at all has been generated. */
  hasHistory(): boolean {
    return (this.usage()?.allTime.requests ?? 0) > 0;
  }

  /** A flat-fee provider logs requests but no tokens; say so rather than showing a bare 0. */
  isMetered(): boolean {
    const all = this.usage()?.allTime;
    return !!all && (all.tokensIn + all.tokensOut) > 0;
  }

  tokens(t: { tokensIn: number; tokensOut: number }): number {
    return t.tokensIn + t.tokensOut;
  }

  /** Widest bar in the breakdown is 100%; everything else is relative to it. */
  barWidth(op: { tokensIn: number; tokensOut: number; requests: number }): number {
    const rows = this.usage()?.byOperation ?? [];
    const metric = (r: typeof op) => this.isMetered() ? this.tokens(r) : r.requests;
    const max = Math.max(...rows.map(metric), 1);
    return Math.round((metric(op) / max) * 100);
  }

  operationLabel(operation: string): string {
    return 'settings.usage.op.' + operation;
  }
}
