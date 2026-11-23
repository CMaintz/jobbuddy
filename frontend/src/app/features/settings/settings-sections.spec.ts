import { settingsSectionsFor } from './settings-sections';

describe('settingsSectionsFor', () => {
  const keys = (role: 'USER' | 'ADMIN' | undefined) => settingsSectionsFor(role).map(s => s.key);

  it('keeps AI usage and the skill vocabulary away from a regular user', () => {
    expect(keys('USER')).not.toContain('usage');
    expect(keys('USER')).not.toContain('taxonomy');
  });

  it('still gives a regular user their own settings', () => {
    expect(keys('USER')).toEqual(['match', 'gen', 'style', 'account', 'privacy']);
  });

  it('gives an admin the developer sections too', () => {
    expect(keys('ADMIN')).toContain('usage');
    expect(keys('ADMIN')).toContain('taxonomy');
  });
});
