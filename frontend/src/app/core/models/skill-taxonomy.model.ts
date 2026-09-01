export interface SkillTaxonomy {
  id: string;
  name: string;
  normalizedName: string;
  parentId?: string;
  category: string;
  aliases?: string[];
}

export interface ProfileSkill {
  id?: string;
  userId?: string;
  skillName: string;
  taxonomyId?: string;
  proficiencyLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  yearsExperience?: number;
  usedInProduction: boolean;
  displayOrder: number;
  category?: string;
}

/**
 * Categories that count as a technology rather than a way of working.
 *
 * Mirrors SkillCategories.TECHNICAL on the backend, which derives the technologies list
 * sent to the AI. Nothing enforces that the two agree, so changing one means changing the
 * other.
 */
export const TECH_CATEGORIES = new Set([
  'Programming Language', 'Framework', 'Library', 'Database', 'Cloud',
  'DevOps', 'Tool', 'API', 'AI/ML', 'Architecture', 'Testing', 'Security'
]);
