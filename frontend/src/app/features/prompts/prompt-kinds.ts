import { PromptCategory } from '../../core/models/prompt-template.model';

export interface PromptKind { key: PromptCategory; label: string; icon: string; color: string }

export const PROMPT_KINDS: PromptKind[] = [
  { key: 'APPLICATION', label: 'prompts.kind.application', icon: 'layers', color: 'var(--jb-accent)' },
  { key: 'COVER_LETTER', label: 'prompts.kind.coverLetter', icon: 'doc', color: 'var(--jb-info)' },
  { key: 'UNSOLICITED_APPLICATION', label: 'prompts.kind.unsolicited', icon: 'compass', color: '#f2c97d' },
  { key: 'RECRUITER_MESSAGE', label: 'prompts.kind.recruiterEmail', icon: 'mail', color: '#7df2a8' },
  { key: 'FOLLOW_UP_MESSAGE', label: 'prompts.kind.followUp', icon: 'clock', color: '#7dd3f2' },
  { key: 'CV_TAILORING', label: 'prompts.kind.tailoredCv', icon: 'doc', color: 'var(--jb-violet)' },
  { key: 'CV_ANALYSIS', label: 'prompts.kind.cvAnalysis', icon: 'doc', color: 'var(--jb-text-dim)' },
  { key: 'GENERAL', label: 'prompts.kind.general', icon: 'lightbulb', color: 'var(--jb-text-dim)' },
  { key: 'CV_PROFILE', label: 'prompts.kind.cvProfile', icon: 'user', color: 'var(--jb-violet)' },
  { key: 'CV_SKILLS', label: 'prompts.kind.cvSkills', icon: 'bolt', color: 'var(--jb-violet)' },
  { key: 'CV_EXPERIENCE', label: 'prompts.kind.cvExperience', icon: 'briefcase', color: 'var(--jb-violet)' },
  { key: 'CV_PROJECTS', label: 'prompts.kind.cvProjects', icon: 'git-branch', color: 'var(--jb-violet)' },
  { key: 'CV_EDUCATION', label: 'prompts.kind.cvEducation', icon: 'bookmark', color: 'var(--jb-violet)' },
  { key: 'CV_CERTIFICATIONS', label: 'prompts.kind.cvCertifications', icon: 'check-circle', color: 'var(--jb-violet)' },
];

/**
 * The toast for a failed save. A full kind (409) says what to do about it; anything else is the
 * caller's generic failure.
 */
export function saveErrorKey(err: { status?: number } | null | undefined, fallback: string): string {
  return err?.status === 409 ? 'prompts.toast.limitReached' : fallback;
}
