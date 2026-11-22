import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { LetterGateReportComponent } from './letter-gate-report.component';
import { LetterCheckState } from './letter-export-check';

/** Asks before a letter the gate does not stand behind is exported, and shows what failed. */
@Component({
  selector: 'app-letter-export-warning',
  imports: [TranslateModule, JbModalComponent, JbButtonComponent, LetterGateReportComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <jb-modal [width]="560" (closed)="cancelled.emit()">
      <span jb-modal-header class="text-md font-medium">{{ 'output.exportCheck.title' | translate }}</span>
      <p class="m-0 text-sm text-jb-text">{{ 'output.exportCheck.' + state() | translate }}</p>
      <app-letter-gate-report class="-mx-[18px]" [structuredContent]="structuredContent()" />
      <ng-container jb-modal-footer>
        @if (canRecheck()) {
          <jb-button small icon="check" [disabled]="rechecking()" (clicked)="recheck.emit()">
            {{ (rechecking() ? 'output.exportCheck.rechecking' : 'output.exportCheck.recheck') | translate }}
          </jb-button>
        }
        <span class="ml-auto"></span>
        <jb-button small ghost (clicked)="cancelled.emit()">{{ 'common.cancel' | translate }}</jb-button>
        <jb-button small primary [disabled]="rechecking()" (clicked)="exportAnyway.emit()">
          {{ 'output.exportCheck.exportAnyway' | translate }}
        </jb-button>
      </ng-container>
    </jb-modal>
  `,
})
export class LetterExportWarningComponent {
  readonly state = input.required<LetterCheckState>();
  readonly structuredContent = input<string | null | undefined>();
  /** False for unsaved edits (the server would judge the saved text) and letters with no posting. */
  readonly canRecheck = input(false);
  readonly rechecking = input(false);

  readonly recheck = output<void>();
  readonly exportAnyway = output<void>();
  readonly cancelled = output<void>();
}
