import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbPillComponent, PillTone } from '../../shared/components/jb-pill/jb-pill.component';
import {
  LetterGatePoint, LetterGateResult, LetterReview, StructuredDocument,
} from '../../core/models/structured-document.model';

const VERDICT_KEY: Record<string, string> = {
  PASSED: 'output.gate.passed',
  FAILED: 'output.gate.failed',
  NOT_COMPLETED: 'output.gate.noVerdict',
};

const VERDICT_TONE: Record<string, PillTone> = { PASSED: 'success', FAILED: 'danger', NOT_COMPLETED: 'neutral' };

@Component({
  selector: 'app-letter-gate-report',
  imports: [TranslateModule, JbIconComponent, JbPillComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './letter-gate-report.component.html',
})
export class LetterGateReportComponent {
  readonly structuredContent = input<string | null | undefined>();

  readonly review = computed<LetterReview | null>(() => {
    const raw = this.structuredContent();
    if (!raw) return null;
    try {
      return (JSON.parse(raw) as StructuredDocument).letterReview ?? null;
    } catch {
      return null;
    }
  });

  readonly failedPoints = computed<LetterGatePoint[]>(() =>
    (this.review()?.gate?.points ?? []).filter(p => !p.passed));

  verdictKey(gate: LetterGateResult): string {
    return VERDICT_KEY[gate.verdict] ?? VERDICT_KEY['NOT_COMPLETED'];
  }

  verdictTone(gate: LetterGateResult): PillTone {
    return VERDICT_TONE[gate.verdict] ?? 'neutral';
  }
}
