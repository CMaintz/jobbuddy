export type PromptCategory =
  | 'COVER_LETTER'
  | 'APPLICATION'
  /** Speculative application to a company with no posted vacancy ("uopfordret ansøgning"). */
  | 'UNSOLICITED_APPLICATION'
  | 'RECRUITER_MESSAGE'
  /** The short nudge after an application or an unsolicited approach has gone quiet. */
  | 'FOLLOW_UP_MESSAGE'
  | 'CV_TAILORING'
  | 'CV_ANALYSIS'
  | 'GENERAL'
  /** Per-CV-section writing guidance; only the user prompt is used, never a system prompt. */
  | 'CV_PROFILE'
  | 'CV_SKILLS'
  | 'CV_EXPERIENCE'
  | 'CV_PROJECTS'
  | 'CV_EDUCATION'
  | 'CV_CERTIFICATIONS';

export interface PromptTemplate {
  id: string;
  userId?: string;
  name: string;
  category?: PromptCategory;
  description?: string;
  systemPrompt?: string;
  userPrompt: string;
  outputConstraints?: string;
  isPublic: boolean;
  /** The app shipped it. */
  isSystem: boolean;
  /**
   * App-origin: duplicable, never editable or deletable. Distinct from isSystem, which no longer
   * decides what the user may do, and from isDefault, which is about selection rather than rights.
   */
  isProtected: boolean;
  /** The app's seeded starting point for this category. */
  isDefault: boolean;
  /** The prompt this user actually gets for the category right now. */
  isSelectedDefault: boolean;
  parentTemplateId?: string;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
  tags?: string[];
  /** How many generations used this template. */
  usageCount?: number;
  /** Per-user star. */
  favourite?: boolean;
  /** i18n key of the explanation for an app-shipped template; user templates use description. */
  explainerKey?: string;
}
