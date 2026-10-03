import {
  LucideIconInput, LucideZap, LucideStar, LucideAward, LucideTarget, LucideTrendingUp, LucideLightbulb,
  LucideCircleCheck, LucideShield, LucideUsers, LucideMessageCircle, LucideCode, LucideCpu, LucideDatabase,
  LucideLayoutDashboard, LucideSmartphone,
} from '@lucide/angular';

export interface StrengthIcon {
  key: string;
  label: string;
  icon: LucideIconInput;
}

export const STRENGTH_ICONS: StrengthIcon[] = [
  { key: 'zap',            label: 'Zap',     icon: LucideZap },
  { key: 'star',           label: 'Star',    icon: LucideStar },
  { key: 'award',          label: 'Award',   icon: LucideAward },
  { key: 'target',         label: 'Target',  icon: LucideTarget },
  { key: 'trending-up',    label: 'Growth',  icon: LucideTrendingUp },
  { key: 'lightbulb',      label: 'Ideas',   icon: LucideLightbulb },
  { key: 'check-circle',   label: 'Check',   icon: LucideCircleCheck },
  { key: 'shield',         label: 'Shield',  icon: LucideShield },
  { key: 'users',          label: 'People',  icon: LucideUsers },
  { key: 'message-circle', label: 'Comms',   icon: LucideMessageCircle },
  { key: 'code',           label: 'Code',    icon: LucideCode },
  { key: 'cpu',            label: 'Tech',    icon: LucideCpu },
  { key: 'database',       label: 'Data',    icon: LucideDatabase },
  { key: 'layout',         label: 'Layout',  icon: LucideLayoutDashboard },
  { key: 'smartphone',     label: 'Mobile',  icon: LucideSmartphone },
];

export const STRENGTH_ICON_MAP = new Map(STRENGTH_ICONS.map(i => [i.key, i]));

export function getStrengthIcon(key: string): LucideIconInput {
  return STRENGTH_ICON_MAP.get(key)?.icon ?? LucideStar;
}
