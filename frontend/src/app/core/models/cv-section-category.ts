import { PromptCategory, PromptTemplate, byPreference } from './prompt-template.model';

/** The CV sections that carry their own template category, in the order a CV lists them. */
export const CV_SECTION_KEYS = ['profile', 'skills', 'experience', 'projects', 'education', 'certifications'] as const;

export type CvSectionKey = typeof CV_SECTION_KEYS[number];

/** Mirrors the backend's section-key to category mapping; the refine endpoint relies on it. */
const CV_SECTION_CATEGORIES: Record<CvSectionKey, PromptCategory> = {
  profile: 'CV_PROFILE',
  skills: 'CV_SKILLS',
  experience: 'CV_EXPERIENCE',
  projects: 'CV_PROJECTS',
  education: 'CV_EDUCATION',
  certifications: 'CV_CERTIFICATIONS',
};

const SECTION_CATEGORY_SET: ReadonlySet<PromptCategory> = new Set(Object.values(CV_SECTION_CATEGORIES));

/** Section keys arrive as plain strings from templates, so an unknown key means "no section". */
export function sectionCategory(sectionKey: string | null | undefined): PromptCategory | undefined {
  return sectionKey ? CV_SECTION_CATEGORIES[sectionKey as CvSectionKey] : undefined;
}

export function isSectionCategory(category: PromptCategory | null | undefined): boolean {
  return !!category && SECTION_CATEGORY_SET.has(category);
}

/** A section's templates with the one in use first, so the default is the first thing offered. */
export function templatesForSection(all: PromptTemplate[], sectionKey: string | null | undefined): PromptTemplate[] {
  const category = sectionCategory(sectionKey);
  if (!category) return [];
  return all
    .filter(t => t.category === category)
    .sort(byPreference);
}
