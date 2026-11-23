import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

export type PillTone = 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet';

/**
 * Full class names, never built as 'pill-' + tone: the pill rules sit in Tailwind's components
 * layer, and Tailwind keeps such a rule only when its whole name appears in the source. A
 * concatenated name leaves a tone styled only while some other file happens to spell it out;
 * otherwise its pills render as bare text.
 */
export const PILL_CLASS: Record<PillTone, string> = {
  neutral: 'pill pill-neutral',
  accent: 'pill pill-accent',
  success: 'pill pill-success',
  info: 'pill pill-info',
  danger: 'pill pill-danger',
  violet: 'pill pill-violet',
};

@Component({
  selector: 'jb-pill',
  templateUrl: './jb-pill.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./jb-pill.component.css']
})
export class JbPillComponent {
  @Input() tone: PillTone = 'neutral';

  get pillClass(): string {
    return PILL_CLASS[this.tone];
  }
}
