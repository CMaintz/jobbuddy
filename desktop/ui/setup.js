'use strict';
const shell = window.jobbuddyShell;
const editing = new URLSearchParams(window.location.search).has('edit');
const $ = (id) => document.getElementById(id);

function renderChecks(checks, hasSettings) {
  const list = $('checks');
  list.replaceChildren(...checks.map((c) => {
    const li = document.createElement('li');
    li.className = c.ok ? 'ok' : 'bad';
    const mark = document.createElement('span');
    mark.className = 'mark';
    mark.textContent = c.ok ? '✓' : '✗';
    const title = document.createElement('span');
    title.textContent = c.title;
    li.append(mark, title);
    if (!c.ok && c.fix) {
      const fix = document.createElement('span');
      fix.className = 'fix';
      fix.textContent = c.fix;
      li.append(fix);
    }
    return li;
  }));
  $('continue').hidden = !(hasSettings && !editing && checks.some((c) => !c.ok));
}

async function load() {
  const state = await shell.state();
  renderChecks(state.checks, state.hasSettings);
  // With a saved connection and nothing to fix, only an edit brings her here.
  $('connection').hidden = state.hasSettings && !editing;
  if (state.saved) {
    $('dbUrl').value = state.saved.dbUrl;
    $('dbUser').value = state.saved.dbUser;
    $('embeddingProvider').value = state.saved.embeddingProvider;
    $('keepPassword').hidden = false;
    $('keepKey').hidden = false;
  }
}

$('recheck').addEventListener('click', async () => {
  $('checks').replaceChildren(Object.assign(document.createElement('li'), { textContent: 'Tjekker...' }));
  const state = await shell.state();
  renderChecks(await shell.recheck(), state.hasSettings);
});
$('continue').addEventListener('click', () => shell.continueAnyway());
$('connection').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  const result = await shell.save(Object.fromEntries(form.entries()));
  $('errors').textContent = result.errors.join(' ');
});

load();
