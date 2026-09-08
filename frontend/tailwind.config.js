/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Geist', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SF Mono', 'Menlo', 'monospace'],
      },
      colors: {
        /* ── Surfaces ─────────────────────────────────── */
        jb: {
          bg:             'var(--jb-bg)',
          surface:        'var(--jb-surface)',
          'surface-2':    'var(--jb-surface-2)',
          'surface-3':    'var(--jb-surface-3)',
          hover:          'var(--jb-hover)',

          /* ── Borders ─────────────────────────────────── */
          border:         'var(--jb-border)',
          'border-strong': 'var(--jb-border-strong)',
          'border-faint':  'var(--jb-border-faint)',

          /* ── Text ────────────────────────────────────── */
          text:           'var(--jb-text)',
          'text-mid':     'var(--jb-text-mid)',
          'text-dim':     'var(--jb-text-dim)',
          'text-faint':   'var(--jb-text-faint)',

          /* ── Accent (amber/gold) ─────────────────────── */
          accent:         'var(--jb-accent)',
          'accent-2':     'var(--jb-accent-2)',
          'accent-soft':  'var(--jb-accent-soft)',
          'accent-border': 'var(--jb-accent-border)',

          /* ── Semantic ────────────────────────────────── */
          success:        'var(--jb-success)',
          'success-soft': 'var(--jb-success-soft)',
          info:           'var(--jb-info)',
          'info-soft':    'var(--jb-info-soft)',
          danger:         'var(--jb-danger)',
          'danger-soft':  'var(--jb-danger-soft)',
          violet:         'var(--jb-violet)',
          'violet-soft':  'var(--jb-violet-soft)',
        },
      },
      fontSize: {
        '2xs': ['10.5px', { lineHeight: '1.4' }],
        xs:    ['11px', { lineHeight: '1.45' }],
        sm:    ['12px', { lineHeight: '1.45' }],
        base:  ['12.5px', { lineHeight: '1.45' }],
        md:    ['13px', { lineHeight: '1.45' }],
        lg:    ['13.5px', { lineHeight: '1.45' }],
        xl:    ['14px', { lineHeight: '1.5' }],
        '2xl': ['16px', { lineHeight: '1.4' }],
        '3xl': ['19px', { lineHeight: '1.35' }],
        '4xl': ['22px', { lineHeight: '1.3' }],
        '5xl': ['26px', { lineHeight: '1.25' }],
        '6xl': ['28px', { lineHeight: '1.2' }],
      },
      letterSpacing: {
        tighter: '-0.02em',
        tight: '-0.015em',
        snug: '-0.01em',
        normal: '-0.005em',
        wide: '0.01em',
        wider: '0.04em',
        widest: '0.06em',
      },
      borderRadius: {
        sm: '4px',
        DEFAULT: '6px',
        md: '6px',
        lg: '8px',
        xl: '14px',
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
  ],
};
