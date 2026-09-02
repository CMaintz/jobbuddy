import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { JbPillComponent, PillTone } from '../jb-pill/jb-pill.component';
import { ApplicationStatus } from '../../../core/models/application.model';

/**
 * The single place an application stage turns into a pill. Every list, board and
 * detail view renders the stage through this, so a stage can only ever have one
 * colour and one translation across the app.
 */
export const STAGE_LABEL_KEY: Record<string, string> = {
  SAVED: 'pipeline.stage.saved',
  PREPARING: 'pipeline.stage.preparing',
  APPLIED: 'pipeline.stage.applied',
  RECRUITER_CONTACT: 'pipeline.stage.screen',
  INTERVIEW: 'pipeline.stage.interview',
  TECHNICAL_TEST: 'pipeline.stage.technical',
  FINAL_ROUND: 'pipeline.stage.final',
  OFFER: 'pipeline.stage.offer',
  REJECTED: 'pipeline.stage.rejected',
  ARCHIVED: 'pipeline.stage.archived'
};

export const STAGE_TONE: Record<string, PillTone> = {
  SAVED: 'neutral',
  PREPARING: 'neutral',
  APPLIED: 'info',
  RECRUITER_CONTACT: 'violet',
  INTERVIEW: 'accent',
  TECHNICAL_TEST: 'accent',
  FINAL_ROUND: 'accent',
  OFFER: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'neutral'
};

export function stageLabelKey(status: ApplicationStatus | string): string {
  return STAGE_LABEL_KEY[status] ?? status;
}

export function stageTone(status: ApplicationStatus | string): PillTone {
  return STAGE_TONE[status] ?? 'neutral';
}

@Component({
  selector: 'app-status-chip',
  standalone: true,
  imports: [TranslateModule, JbPillComponent],
  templateUrl: './status-chip.component.html'
})
export class StatusChipComponent {
  @Input() status: ApplicationStatus | string = '';

  get tone(): PillTone {
    return stageTone(this.status);
  }

  get labelKey(): string {
    return stageLabelKey(this.status);
  }
}
