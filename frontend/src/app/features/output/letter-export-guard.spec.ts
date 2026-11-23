import { Observable, of, throwError } from 'rxjs';
import { ExportKind, LetterExportGuard } from './letter-export-guard';
import { LetterCheckState } from './letter-export-check';

/**
 * The export is held until the user has seen why the check does not stand behind the letter.
 * Export stays possible, never silent.
 */
describe('LetterExportGuard', () => {
  let state: LetterCheckState;
  let exported: ExportKind[];
  let recheckResult: () => Observable<unknown>;
  let guard: LetterExportGuard;

  beforeEach(() => {
    state = 'failed';
    exported = [];
    recheckResult = () => of(null);
    guard = new LetterExportGuard(() => state, kind => exported.push(kind), () => recheckResult());
  });

  it('exports a passed letter straight away', () => {
    state = 'passed';
    guard.request('ats');

    expect(exported).toEqual(['ats']);
    expect(guard.pending()).toBeNull();
  });

  it('exports a message the gate does not judge straight away', () => {
    // Bug: every short pitch asks for a confirmation that can never go away.
    state = 'notApplicable';
    guard.request('designed');

    expect(exported).toEqual(['designed']);
  });

  for (const held of ['failed', 'noVerdict', 'stale'] as LetterCheckState[]) {
    it(`holds a ${held} letter until the user chooses to export it anyway`, () => {
      // Bug: the download starts before, or instead of, the warning.
      state = held;
      guard.request('designed');

      expect(exported).toEqual([]);
      expect(guard.pending()).toBe('designed');

      guard.exportAnyway();

      expect(exported).toEqual(['designed']);
      expect(guard.pending()).toBeNull();
    });
  }

  it('exports nothing when the user cancels', () => {
    // Bug: cancel only hides the warning, and the held export fires on the next confirmation.
    guard.request('ats');
    guard.cancel();
    guard.exportAnyway();

    expect(exported).toEqual([]);
  });

  it('exports the held letter once a re-check passes it', () => {
    // Bug: after a passing re-check the user has to find the download again, or the export
    // happens for the format they did not pick.
    guard.request('ats');
    recheckResult = () => {
      state = 'passed';
      return of(null);
    };

    guard.recheck();

    expect(exported).toEqual(['ats']);
    expect(guard.pending()).toBeNull();
    expect(guard.rechecking()).toBeFalse();
  });

  it('keeps holding the letter when the re-check fails it again', () => {
    // Bug: any finished re-check releases the export, so a letter that fails again goes out.
    guard.request('ats');

    guard.recheck();

    expect(exported).toEqual([]);
    expect(guard.pending()).toBe('ats');
  });

  it('keeps holding the letter when the re-check cannot run', () => {
    // Bug: an error leaves the re-check marked as running, so its button stays disabled.
    guard.request('ats');
    recheckResult = () => throwError(() => new Error('provider down'));

    guard.recheck();

    expect(exported).toEqual([]);
    expect(guard.pending()).toBe('ats');
    expect(guard.rechecking()).toBeFalse();
  });
});
