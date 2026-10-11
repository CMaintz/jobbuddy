/**
 * Section headings and fixed labels for the ATS export.
 *
 * <p>The builder's own model has no headings, unlike a StructuredDocument, which carries the
 * localised ones the backend chose. So they are passed in: left in English, a Danish CV exported
 * from the builder would be headed "Skills" and "Education" while the same CV generated through
 * the backend is headed "Kompetencer" and "Uddannelse".
 */
export interface AtsHeadings {
  profile: string;
  strengths: string;
  experience: string;
  skills: string;
  projects: string;
  education: string;
  certifications: string;
  languages: string;
  /** Shown for a running role or study instead of an end date. */
  present: string;
  /** Header text when the CV carries no name. */
  untitled: string;
}

export const ENGLISH_HEADINGS: AtsHeadings = {
  profile: 'Profile', strengths: 'Strengths', experience: 'Experience', skills: 'Skills',
  projects: 'Projects', education: 'Education', certifications: 'Certifications',
  languages: 'Languages', present: 'Present', untitled: 'Curriculum Vitae',
};

/**
 * These keys are the frontend half of the CV heading vocabulary; the backend half is
 * CvSectionLabels, and the two have to agree or the same CV reads differently depending on
 * which path exported it.
 */
const HEADING_KEYS: AtsHeadings = {
  profile: 'resumeBuilder.cv.profile',
  strengths: 'resumeBuilder.section.strengths',
  experience: 'resumeBuilder.section.experience',
  skills: 'resumeBuilder.section.skills',
  projects: 'resumeBuilder.section.projects',
  education: 'resumeBuilder.section.education',
  certifications: 'resumeBuilder.section.certifications',
  languages: 'resumeBuilder.section.languages',
  present: 'resumeBuilder.cv.present',
  untitled: 'resumeBuilder.cv.untitledName',
};

/** Headings in the interface language, resolved through the given translate function. */
export function translatedAtsHeadings(translate: (key: string) => string): AtsHeadings {
  const entries = Object.entries(HEADING_KEYS).map(([field, key]) => [field, translate(key)]);
  return Object.fromEntries(entries) as AtsHeadings;
}
