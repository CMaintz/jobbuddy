import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

export type MatchLabel = 'EXCELLENT' | 'STRONG' | 'MODERATE' | 'WEAK';

/**
 * Colour for a match label. The label itself is decided by the backend
 * (MatchLabel.fromScore) — the frontend must not re-derive it from the score,
 * or the two drift the moment a threshold moves.
 */
export function matchColor(label: MatchLabel | undefined): { fg: string; bg: string; border: string } {
  switch (label) {
    case 'EXCELLENT':
      return { fg: 'var(--jb-success)', bg: 'var(--jb-success-soft)', border: 'rgba(74,222,128,0.3)' };
    case 'STRONG':
      return { fg: 'var(--jb-accent-2)', bg: 'var(--jb-accent-soft)', border: 'var(--jb-accent-border)' };
    case 'MODERATE':
      return { fg: 'var(--jb-text-mid)', bg: 'var(--jb-surface-3)', border: 'var(--jb-border-strong)' };
    default:
      return { fg: 'var(--jb-text-dim)', bg: 'var(--jb-surface-3)', border: 'var(--jb-border)' };
  }
}

export function matchLabelKey(label: MatchLabel | undefined): string {
  return 'match.grade.' + (label ?? 'WEAK').toLowerCase();
}

/**
 * How well a role fits, shown as the grade rather than a bare number — a grade is
 * what a glance down a list can actually use. The score itself is a hover away for
 * when the ranking is what you're questioning.
 */
@Component({
  selector: 'app-match-badge',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './match-badge.component.html'
})
export class MatchBadgeComponent {
  @Input() score = 0;
  @Input() label: MatchLabel | undefined;

  get color() {
    return matchColor(this.label);
  }

  get labelKey(): string {
    return matchLabelKey(this.label);
  }
}
