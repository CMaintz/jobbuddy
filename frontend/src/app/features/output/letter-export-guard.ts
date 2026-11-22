import { signal } from '@angular/core';
import { Observable } from 'rxjs';
import { exportsWithoutWarning, LetterCheckState } from './letter-export-check';

export type ExportKind = 'designed' | 'ats';

/**
 * Holds a letter's export back until the user has seen why the gate does not stand behind it.
 * The user decides: export anyway, check the letter again, or cancel.
 */
export class LetterExportGuard {
  /** The export waiting on the user's answer; null when none is. */
  readonly pending = signal<ExportKind | null>(null);
  readonly rechecking = signal(false);

  /**
   * @param state the active letter's standing, read when an export is asked for and after a re-check
   * @param run performs the export
   * @param recheckLetter re-checks the active letter and stores the new verdict where
   *     {@code state} reads it
   */
  constructor(
    private readonly state: () => LetterCheckState,
    private readonly run: (kind: ExportKind) => void,
    private readonly recheckLetter: () => Observable<unknown>,
  ) {}

  request(kind: ExportKind): void {
    if (exportsWithoutWarning(this.state())) {
      this.run(kind);
    } else {
      this.pending.set(kind);
    }
  }

  exportAnyway(): void {
    const kind = this.pending();
    this.pending.set(null);
    if (kind) this.run(kind);
  }

  cancel(): void {
    this.pending.set(null);
  }

  /** Re-checks the letter; a pass releases the held export, anything else keeps holding it. */
  recheck(): void {
    if (this.rechecking()) return;
    this.rechecking.set(true);
    this.recheckLetter().subscribe({
      next: () => {
        this.rechecking.set(false);
        if (this.state() === 'passed') this.exportAnyway();
      },
      error: () => this.rechecking.set(false),
    });
  }
}
