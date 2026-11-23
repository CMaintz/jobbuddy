import { User } from '../../core/models/user.model';

export type Section = 'match' | 'gen' | 'style' | 'usage' | 'account' | 'privacy' | 'taxonomy';

export interface SettingsSection {
  key: Section;
  label: string;
  icon: string;
}

const USER_SECTIONS: SettingsSection[] = [
  { key: 'match', label: 'settings.section.match', icon: 'target' },
  { key: 'gen', label: 'settings.section.gen', icon: 'wand' },
  { key: 'style', label: 'settings.section.style', icon: 'edit' },
  { key: 'account', label: 'settings.section.account', icon: 'user' },
  { key: 'privacy', label: 'settings.section.privacy', icon: 'key' },
];

/**
 * AI usage (tokens, provider operations) and the shared skill taxonomy are operator concerns. A job
 * seeker gets them as dead or baffling tabs, so they are left out of the nav rather than refused.
 */
const ADMIN_SECTIONS: SettingsSection[] = [
  { key: 'usage', label: 'settings.section.usage', icon: 'chart-up' },
  { key: 'taxonomy', label: 'settings.section.taxonomy', icon: 'sparkle' },
];

export function settingsSectionsFor(role: User['role'] | undefined): SettingsSection[] {
  return role === 'ADMIN' ? [...USER_SECTIONS, ...ADMIN_SECTIONS] : USER_SECTIONS;
}
