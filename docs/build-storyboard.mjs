// Generates docs/storyboard.svg — a looping, animated walkthrough of the Jobbuddy
// flow (job posting -> AI-tailored CV -> ATS match report). Pure SVG + SMIL, so it
// animates as an <img> on GitHub. Run: node docs/build-storyboard.mjs
import { writeFileSync } from 'node:fs';

const W = 880;
const H = 400;
const c = {
  bg: '#0d1117',
  card: '#161b22',
  border: '#30363d',
  text: '#e6edf3',
  dim: '#8b949e',
  green: '#3fb950',
  blue: '#58a6ff',
  purple: '#bc8cff',
};
const cardW = 246;
const cardH = 232;
const cardY = 104;
const xs = [30, 317, 604];

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const line = (x, y, w, fill = c.dim, h = 7) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="3" fill="${fill}"/>`;
const txt = (x, y, s, fill = c.text, size = 14, weight = 400) =>
  `<text x="${x}" y="${y}" font-family="-apple-system,Segoe UI,Roboto,sans-serif" font-size="${size}" font-weight="${weight}" fill="${fill}">${esc(s)}</text>`;

// A panel that fades in at `appear` (fraction of the 9s loop) and holds, then all fade out together.
const panel = (appear, inner) => `<g opacity="0">
    <animate attributeName="opacity" dur="9s" begin="0s" repeatCount="indefinite"
      values="0;0;1;1;0" keyTimes="0;${appear};${(appear + 0.03).toFixed(2)};0.92;1"
      calcMode="spline" keySplines="0 0 1 1;.4 0 .2 1;0 0 1 1;.4 0 .2 1"/>
    ${inner}
  </g>`;

const card = (x, badge, badgeColor, title, body) => `
    <rect x="${x}" y="${cardY}" width="${cardW}" height="${cardH}" rx="12" fill="${c.card}" stroke="${c.border}"/>
    <rect x="${x}" y="${cardY}" width="${cardW}" height="34" rx="12" fill="${badgeColor}" opacity="0.14"/>
    ${txt(x + 16, cardY + 22, badge, badgeColor, 12, 700)}
    ${txt(x + 16, cardY + 62, title, c.text, 15, 600)}
    ${body}`;

const arrow = (appear, x) => panel(
  appear,
  `<path d="M${x} ${cardY + cardH / 2} h26" stroke="${c.dim}" stroke-width="2" fill="none"/>
   <path d="M${x + 22} ${cardY + cardH / 2 - 5} l6 5 -6 5" stroke="${c.dim}" stroke-width="2" fill="none"/>`,
);

const posting = card(
  xs[0],
  '1 · JOB POSTING',
  c.blue,
  'Senior Frontend Engineer',
  `${txt(xs[0] + 16, cardY + 82, 'Acme · Remote', c.dim, 12)}
   ${line(xs[0] + 16, cardY + 100, 200)}
   ${line(xs[0] + 16, cardY + 116, 214)}
   ${line(xs[0] + 16, cardY + 132, 180)}
   ${line(xs[0] + 16, cardY + 148, 206)}
   ${line(xs[0] + 16, cardY + 164, 150)}`,
);

const cv = card(
  xs[1],
  '2 · TAILORED CV',
  c.purple,
  'Rewritten for the role',
  `${line(xs[1] + 16, cardY + 92, 200)}
   ${line(xs[1] + 16, cardY + 108, 210, c.green)}
   ${line(xs[1] + 16, cardY + 124, 170, c.green)}
   ${line(xs[1] + 16, cardY + 140, 200)}
   ${line(xs[1] + 16, cardY + 156, 186, c.green)}
   ${txt(xs[1] + 16, cardY + 196, '+ cover letter', c.dim, 12)}`,
);

const ats = card(
  xs[2],
  '3 · ATS REPORT',
  c.green,
  'Match score',
  `${txt(xs[2] + 16, cardY + 128, '87%', c.green, 52, 800)}
   ${txt(xs[2] + 120, cardY + 108, 'keywords', c.dim, 12)}
   ${txt(xs[2] + 120, cardY + 126, 'covered', c.dim, 12)}
   ${txt(xs[2] + 16, cardY + 168, '✓ React  ✓ TypeScript  ✓ CI', c.green, 12)}
   ${txt(xs[2] + 16, cardY + 190, '→ exported as PDF', c.dim, 12)}`,
);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" font-family="-apple-system,Segoe UI,Roboto,sans-serif">
  <rect width="${W}" height="${H}" rx="14" fill="${c.bg}"/>
  ${txt(30, 46, 'Jobbuddy', c.text, 26, 800)}
  ${txt(30, 72, 'from job posting to a tailored, ATS-ready application — powered by AI', c.dim, 14)}
  ${panel(0.05, posting)}
  ${arrow(0.24, xs[0] + cardW + 8)}
  ${panel(0.3, cv)}
  ${arrow(0.5, xs[1] + cardW + 8)}
  ${panel(0.57, ats)}
</svg>`;

writeFileSync(new URL('./storyboard.svg', import.meta.url), svg);
console.log('wrote docs/storyboard.svg');
