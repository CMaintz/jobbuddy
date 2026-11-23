import { Component, ChangeDetectionStrategy, computed, input, output, signal } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ApplicationStatus } from '../../../core/models/application.model';
import { StatusChipComponent, stageLabelKey } from '../status-chip/status-chip.component';

/**
 * Lets the user move an application on. It offers only the moves the server accepts, since the
 * status rules are strict and a refused move would just look like a broken control.
 */
@Component({
  selector: 'app-status-picker',
  imports: [TranslateModule, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (options().length > 1) {
      <select class="input py-1 text-sm" [value]="shown()" [disabled]="pending()" (change)="pick($event)"
          (click)="$event.stopPropagation()" (keydown.enter)="$event.stopPropagation()"
          [attr.aria-label]="'pipeline.changeStatus' | translate">
        @for (option of options(); track option) {
          <option [value]="option" [selected]="option === shown()">{{ label(option) | translate }}</option>
        }
      </select>
    } @else {
      <app-status-chip [status]="status()" />
    }
  `,
})
export class StatusPickerComponent {
  readonly status = input.required<ApplicationStatus>();
  readonly nextStatuses = input<ApplicationStatus[]>([]);
  /** True while the parent waits for the server to accept the move. */
  readonly pending = input(false);
  readonly changed = output<ApplicationStatus>();

  private readonly picked = signal<ApplicationStatus | null>(null);

  readonly options = computed(() => [this.status(), ...this.nextStatuses()]);

  /**
   * While the move is pending the control shows the pick and is locked, so the user sees it
   * registered and cannot fire a second move into a stale status. Afterwards it shows whatever
   * status the parent holds: the new one on success, the old one when the move was refused.
   */
  readonly shown = computed(() => (this.pending() && this.picked()) || this.status());

  label(status: ApplicationStatus): string {
    return stageLabelKey(status);
  }

  pick(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const next = select.value as ApplicationStatus;
    // Reset the DOM value: if the parent never marks the move pending or the status never
    // changes, no binding would update and the control would show a move that did not happen.
    select.value = this.status();
    if (next === this.status()) return;
    this.picked.set(next);
    this.changed.emit(next);
  }
}
