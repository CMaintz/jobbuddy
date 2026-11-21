'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { parseRegQuery, freshEnv } = require('../src/fresh-env');

const BS = String.fromCharCode(92);
const win = (...parts) => parts.join(BS);

const MACHINE = 'machine';
const USER = 'user';

test('reads the value out of reg query output, plain or expandable', () => {
  const out = [
    '',
    win('HKEY_CURRENT_USER', 'Environment'),
    `    Path    REG_EXPAND_SZ    ${win('%USERPROFILE%', '.local', 'bin')};${win('C:', 'Tools')}`,
    '',
  ].join('\r\n');
  assert.equal(parseRegQuery(out), `${win('%USERPROFILE%', '.local', 'bin')};${win('C:', 'Tools')}`);
  assert.equal(parseRegQuery('    Path    REG_SZ    C:\\x'.replace('\\', BS)), win('C:', 'x'));
  assert.equal(parseRegQuery('ERROR: The system was unable to find the specified registry key'), null);
});

function reader(values) {
  return async (scope) => {
    if (values[scope] instanceof Error) throw values[scope];
    return values[scope] ?? null;
  };
}

const LAUNCH_ENV = {
  Path: win('C:', 'Windows', 'System32'),
  USERPROFILE: win('C:', 'Users', 'her'),
  SystemRoot: win('C:', 'Windows'),
  OTHER: 'kept',
};

test('a program installed after launch is on the PATH the checks and the backend get', async () => {
  // Windows never updates PATH in a running process: Git installed while JobBuddy sits in the
  // tray is only on the PATH in the registry.
  const env = await freshEnv({
    env: LAUNCH_ENV,
    readRegistryPath: reader({
      [MACHINE]: `%SystemRoot%${BS}System32;${win('C:', 'Program Files', 'Git', 'cmd')}`,
      [USER]: win('%USERPROFILE%', '.local', 'bin'),
    }),
  });
  assert.equal(env.Path, [
    win('C:', 'Windows', 'System32'),
    win('C:', 'Program Files', 'Git', 'cmd'),
    win('C:', 'Users', 'her', '.local', 'bin'),
  ].join(';'));
  assert.equal(env.OTHER, 'kept');
});

test('there is one PATH variable, whatever case it had', async () => {
  const env = await freshEnv({
    env: { PATH: 'old' },
    readRegistryPath: reader({ [MACHINE]: 'm', [USER]: 'u' }),
  });
  assert.deepEqual(Object.keys(env).filter((k) => k.toUpperCase() === 'PATH'), ['PATH']);
  assert.equal(env.PATH, 'm;u');
});

test('an unreadable registry keeps the PATH it started with', async () => {
  const env = await freshEnv({
    env: LAUNCH_ENV,
    readRegistryPath: reader({ [MACHINE]: new Error('access denied'), [USER]: new Error('nope') }),
  });
  assert.equal(env.Path, LAUNCH_ENV.Path);
});

test('the launch environment itself is left alone', async () => {
  const launch = { ...LAUNCH_ENV };
  await freshEnv({ env: launch, readRegistryPath: reader({ [MACHINE]: 'm', [USER]: 'u' }) });
  assert.deepEqual(launch, LAUNCH_ENV);
});
