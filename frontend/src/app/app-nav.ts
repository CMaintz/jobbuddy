import { User } from './core/models/user.model';

export interface NavItem {
  id: string;
  label: string;
  icon: string;
  route: string;
  kbd?: string;
  badge?: string;
  count?: number;
  accent?: boolean;
  hot?: boolean;
  /** Developer tooling a job seeker has no use for; the route is admin-guarded too. */
  adminOnly?: boolean;
}

/** The nav items this user may see. An unknown role sees what a regular user sees. */
export function visibleNav(items: NavItem[], role: User['role'] | undefined): NavItem[] {
  return role === 'ADMIN' ? items : items.filter(item => !item.adminOnly);
}
