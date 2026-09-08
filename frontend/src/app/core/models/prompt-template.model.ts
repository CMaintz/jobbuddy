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
  isSystem: boolean;
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
