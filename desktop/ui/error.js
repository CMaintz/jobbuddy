'use strict';
const params = new URLSearchParams(window.location.search);
document.getElementById('message').textContent = params.get('message') ?? '';
document.getElementById('log').textContent = params.get('log') ?? '';
const shell = window.jobbuddyShell;
document.getElementById('restart').addEventListener('click', () => shell.restart());
document.getElementById('edit').addEventListener('click', () => shell.editConnection());
document.getElementById('logs').addEventListener('click', () => shell.openLogFolder());
document.getElementById('quit').addEventListener('click', () => shell.quit());
