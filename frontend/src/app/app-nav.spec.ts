import { visibleNav } from './app-nav';

describe('visibleNav', () => {
  const items = [
    { id: 'master', label: 'nav.masterCv', icon: 'doc', route: '/cv' },
    { id: 'prompts', label: 'nav.prompts', icon: 'lightbulb', route: '/prompts', adminOnly: true },
  ];

  it('hides developer screens from a regular user', () => {
    expect(visibleNav(items, 'USER').map(i => i.id)).toEqual(['master']);
  });

  it('hides developer screens while the role is still unknown', () => {
    expect(visibleNav(items, undefined).map(i => i.id)).toEqual(['master']);
  });

  it('shows them to an admin', () => {
    expect(visibleNav(items, 'ADMIN').map(i => i.id)).toEqual(['master', 'prompts']);
  });
});
