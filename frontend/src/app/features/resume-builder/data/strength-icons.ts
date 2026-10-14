import {
  LucideIconInput, LucideZap, LucideStar, LucideAward, LucideTarget, LucideTrendingUp, LucideLightbulb,
  LucideCircleCheck, LucideShield, LucideUsers, LucideMessageCircle, LucideCode, LucideCpu, LucideDatabase,
  LucideLayoutDashboard, LucideSmartphone,
} from '@lucide/angular';

export interface StrengthIcon {
  key: string;
  /** Translation key of the tooltip. */
  label: string;
  icon: LucideIconInput;
}

export const STRENGTH_ICONS: StrengthIcon[] = [
  { key: 'zap',            label: 'resumeBuilder.icon.zap', icon: LucideZap },
  { key: 'star',           label: 'resumeBuilder.icon.star', icon: LucideStar },
  { key: 'award',          label: 'resumeBuilder.icon.award', icon: LucideAward },
  { key: 'target',         label: 'resumeBuilder.icon.target', icon: LucideTarget },
  { key: 'trending-up',    label: 'resumeBuilder.icon.growth', icon: LucideTrendingUp },
  { key: 'lightbulb',      label: 'resumeBuilder.icon.ideas', icon: LucideLightbulb },
  { key: 'check-circle',   label: 'resumeBuilder.icon.check', icon: LucideCircleCheck },
  { key: 'shield',         label: 'resumeBuilder.icon.shield', icon: LucideShield },
  { key: 'users',          label: 'resumeBuilder.icon.people', icon: LucideUsers },
  { key: 'message-circle', label: 'resumeBuilder.icon.comms', icon: LucideMessageCircle },
  { key: 'code',           label: 'resumeBuilder.icon.code', icon: LucideCode },
  { key: 'cpu',            label: 'resumeBuilder.icon.tech', icon: LucideCpu },
  { key: 'database',       label: 'resumeBuilder.icon.data', icon: LucideDatabase },
  { key: 'layout',         label: 'resumeBuilder.icon.layout', icon: LucideLayoutDashboard },
  { key: 'smartphone',     label: 'resumeBuilder.icon.mobile', icon: LucideSmartphone },
];

export const STRENGTH_ICON_MAP = new Map(STRENGTH_ICONS.map(i => [i.key, i]));

export function getStrengthIcon(key: string): LucideIconInput {
  return STRENGTH_ICON_MAP.get(key)?.icon ?? LucideStar;
}
