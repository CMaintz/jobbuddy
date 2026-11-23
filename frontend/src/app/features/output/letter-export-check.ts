import { LetterReview, StructuredDocument } from '../../core/models/structured-document.model';

/** Where a letter stands against the letter gate at the moment it is exported. */
export type LetterCheckState = 'passed' | 'failed' | 'noVerdict' | 'stale' | 'notApplicable';

/** The document types the backend gates (PromptCompositionBuilder.isProseLetter). */
const GATED_TYPES = new Set(['APPLICATION_TEXT', 'COVER_LETTER', 'UNSOLICITED_APPLICATION']);

/**
 * The gate's standing on a letter's current text.
 *
 * @param editedSinceSave true while the user has unsaved edits: the stored review cannot know
 *     about them yet, so a pass no longer describes what gets exported
 * @returns 'notApplicable' for messages the gate never judges and for a letter written without a
 *     posting, which has nothing to be judged against; 'noVerdict' for a letter the gate never
 *     finished or never saw
 */
export function letterCheckState(
  doc: { documentType: string; structuredContent?: string | null },
  editedSinceSave: boolean,
): LetterCheckState {
  if (!GATED_TYPES.has(doc.documentType)) return 'notApplicable';
  const review = readReview(doc.structuredContent);
  if (review && !review.gate) return 'notApplicable';
  if (editedSinceSave || review?.stale) return 'stale';
  if (review?.gate?.verdict === 'PASSED') return 'passed';
  return review?.gate?.verdict === 'FAILED' ? 'failed' : 'noVerdict';
}

/** The states an export goes ahead in without asking. */
export function exportsWithoutWarning(state: LetterCheckState): boolean {
  return state === 'passed' || state === 'notApplicable';
}

function readReview(structuredContent: string | null | undefined): LetterReview | undefined {
  if (!structuredContent) return undefined;
  try {
    return (JSON.parse(structuredContent) as StructuredDocument).letterReview ?? undefined;
  } catch {
    return undefined;
  }
}
