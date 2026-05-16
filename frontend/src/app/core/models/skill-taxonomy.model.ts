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

export const TECH_CATEGORIES = new Set([
  'Language', 'Framework', 'Library', 'Database', 'Cloud',
  'DevOps', 'Tool', 'API', 'AI/ML', 'Architecture', 'Testing', 'Security'
]);
