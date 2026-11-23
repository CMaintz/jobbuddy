import { letterCheckState } from './letter-export-check';
import { LetterGateResult, LetterReview } from '../../core/models/structured-document.model';

/**
 * Which letters get a warning on export. Too few and a failed letter goes out silently; too many
 * and a warning on every download gets clicked away unread.
 */
describe('letterCheckState', () => {
  const gate = (verdict: LetterGateResult['verdict']): LetterGateResult => ({ verdict, points: [], rounds: 1 });

  function letter(review: LetterReview | undefined, documentType = 'COVER_LETTER') {
    return { documentType, structuredContent: JSON.stringify({ letterReview: review }) };
  }

  it('lets a passed letter through', () => {
    expect(letterCheckState(letter({ needs: [], gate: gate('PASSED'), stale: false }), false)).toBe('passed');
  });

  it('reports a failed letter as failed', () => {
    // Bug: export only looks for a review at all, so a failed verdict exports like a pass.
    expect(letterCheckState(letter({ needs: [], gate: gate('FAILED'), stale: false }), false)).toBe('failed');
  });

  it('reports a gate that ran out of time as no verdict, not as a pass', () => {
    // Bug: "not failed" is read as passed, so a letter the gate never finished goes out unchecked.
    expect(letterCheckState(letter({ needs: [], gate: gate('NOT_COMPLETED'), stale: false }), false))
      .toBe('noVerdict');
  });

  it('reports a letter that was never checked as no verdict', () => {
    // Bug: a letter from before the gate has no review and slips through as if it had passed.
    expect(letterCheckState(letter(undefined, 'APPLICATION_TEXT'), false)).toBe('noVerdict');
  });

  it('reports a passed letter edited since the check as stale', () => {
    // Bug: the old pass is trusted after the user rewrote paragraphs the gate never saw.
    expect(letterCheckState(letter({ needs: [], gate: gate('PASSED'), stale: true }), false)).toBe('stale');
  });

  it('reports unsaved edits as stale, since the server cannot have flagged them yet', () => {
    // Bug: the ATS download exports the text being edited while the stored review still says passed.
    expect(letterCheckState(letter({ needs: [], gate: gate('PASSED'), stale: false }), true)).toBe('stale');
  });

  it('does not warn for a letter with no posting to check against, or a message the gate never judges', () => {
    // Bug: these can never pass, so they would warn on every download and the re-check could not clear it.
    expect(letterCheckState(letter({ needs: ['Behov'], gate: null, stale: false }), false)).toBe('notApplicable');
    expect(letterCheckState(letter(undefined, 'RECRUITER_MESSAGE'), false)).toBe('notApplicable');
    expect(letterCheckState(letter(undefined, 'FOLLOW_UP_MESSAGE'), true)).toBe('notApplicable');
  });
});
