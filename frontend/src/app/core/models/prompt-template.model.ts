export type PromptCategory = 'COVER_LETTER' | 'APPLICATION' | 'RECRUITER_MESSAGE' | 'CV_TAILORING' | 'CV_ANALYSIS' | 'GENERAL';

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
}
