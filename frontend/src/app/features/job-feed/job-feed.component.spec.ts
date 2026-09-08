import { yearsDemanded } from './job-feed.component';

/**
 * The card's experience chip is read straight off the posting's own wording, so the
 * parsing has to be right in both languages — and silent when the posting names no figure.
 */
describe('yearsDemanded', () => {
  it('reads a plain Danish demand', () => {
    expect(yearsDemanded('Mindst 5 års erfaring med backend-udvikling')).toBe(5);
    expect(yearsDemanded('3 år i en lignende rolle')).toBe(3);
  });

  it('reads the English forms', () => {
    expect(yearsDemanded('5+ years of experience with Java')).toBe(5);
    expect(yearsDemanded('At least 2 years experience')).toBe(2);
    expect(yearsDemanded('1 year of professional experience')).toBe(1);
  });

  it('takes the lower bound of a range — that is the threshold', () => {
    expect(yearsDemanded('5-7 års erfaring')).toBe(5);
    expect(yearsDemanded('3–5 years of experience')).toBe(3);
  });

  it('stays silent when the posting names no figure', () => {
    expect(yearsDemanded('Solid erfaring med Kubernetes')).toBeNull();
    expect(yearsDemanded('Several years of experience')).toBeNull();
    expect(yearsDemanded('')).toBeNull();
    expect(yearsDemanded(undefined)).toBeNull();
  });

  it('does not read a number that is not counting years', () => {
    expect(yearsDemanded('Erfaring med Java 21')).toBeNull();
  });
});
