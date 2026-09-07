import {
  Zap, Star, Award, Target, TrendingUp, Lightbulb, CircleCheck,
  Shield, Users, MessageCircle, Code, Cpu, Database, LayoutDashboard, Smartphone,
} from 'lucide-angular';

export interface StrengthIcon {
  key: string;
  label: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  icon: any;
}

export const STRENGTH_ICONS: StrengthIcon[] = [
  { key: 'zap',            label: 'Zap',     icon: Zap },
  { key: 'star',           label: 'Star',    icon: Star },
  { key: 'award',          label: 'Award',   icon: Award },
  { key: 'target',         label: 'Target',  icon: Target },
  { key: 'trending-up',    label: 'Growth',  icon: TrendingUp },
  { key: 'lightbulb',      label: 'Ideas',   icon: Lightbulb },
  { key: 'check-circle',   label: 'Check',   icon: CircleCheck },
  { key: 'shield',         label: 'Shield',  icon: Shield },
  { key: 'users',          label: 'People',  icon: Users },
  { key: 'message-circle', label: 'Comms',   icon: MessageCircle },
  { key: 'code',           label: 'Code',    icon: Code },
  { key: 'cpu',            label: 'Tech',    icon: Cpu },
  { key: 'database',       label: 'Data',    icon: Database },
  { key: 'layout',         label: 'Layout',  icon: LayoutDashboard },
  { key: 'smartphone',     label: 'Mobile',  icon: Smartphone },
];

export const STRENGTH_ICON_MAP = new Map(STRENGTH_ICONS.map(i => [i.key, i]));

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getStrengthIcon(key: string): any {
  return STRENGTH_ICON_MAP.get(key)?.icon ?? Star;
}
