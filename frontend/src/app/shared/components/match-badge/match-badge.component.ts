import { Component, Input } from '@angular/core';

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

/** The score ring shown next to a role in any list of matched jobs. */
@Component({
  selector: 'app-match-badge',
  standalone: true,
  templateUrl: './match-badge.component.html'
})
export class MatchBadgeComponent {
  @Input() score = 0;
  @Input() label: MatchLabel | undefined;
  /** Ring diameter in px. */
  @Input() size = 28;

  get color() {
    return matchColor(this.label);
  }
}
