import { LanguageProficiency } from '../../core/models/profile-section.model';
import { ProfileSkill } from '../../core/models/skill-taxonomy.model';

export type ProfileTab = 'overview' | 'experience' | 'projects' | 'education' | 'certifications' | 'skills' | 'languages' | 'socials' | 'strengths';

export const PROFILE_TABS: Array<{ key: ProfileTab; label: string }> = [
  { key: 'overview', label: 'Overview' },
  { key: 'experience', label: 'Experience' },
  { key: 'projects', label: 'Projects' },
  { key: 'education', label: 'Education' },
  { key: 'certifications', label: 'Certifications' },
  { key: 'skills', label: 'Skills' },
  { key: 'languages', label: 'Languages' },
  { key: 'socials', label: 'Social Links' },
  { key: 'strengths', label: 'Strengths' },
];

export function splitCsv(value?: string | null): string[] {
  return (value || '').split(',').map(s => s.trim()).filter(Boolean);
}

export function profileSkillChipClass(skill: ProfileSkill): string {
  const level = skill.proficiencyLevel;
  if (level === 'EXPERT') return 'bg-purple-50 border-purple-300 text-purple-800 hover:bg-purple-100';
  if (level === 'ADVANCED') return 'bg-blue-50 border-blue-300 text-blue-800 hover:bg-blue-100';
  if (level === 'INTERMEDIATE') return 'bg-green-50 border-green-300 text-green-800 hover:bg-green-100';
  return 'bg-gray-50 border-gray-300 text-gray-700 hover:bg-gray-100';
}

export function profileProficiencyBadgeClass(level: string): string {
  const map: Record<string, string> = {
    BEGINNER: 'bg-gray-100 text-gray-600',
    INTERMEDIATE: 'bg-blue-100 text-blue-700',
    ADVANCED: 'bg-green-100 text-green-700',
    EXPERT: 'bg-purple-100 text-purple-700',
  };
  return map[level] ?? 'bg-gray-100 text-gray-600';
}

export function languageProficiencyLabel(proficiency: LanguageProficiency): string {
  const map: Record<LanguageProficiency, string> = {
    NATIVE: 'Native',
    FLUENT: 'Fluent',
    PROFESSIONAL: 'Professional working proficiency',
    CONVERSATIONAL: 'Conversational',
    ELEMENTARY: 'Elementary',
  };
  return map[proficiency] ?? proficiency;
}
