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
          bg:           { DEFAULT: '#f8f9fa', dark: '#0a0c0f' },
          surface:      { DEFAULT: '#ffffff', dark: '#14171c' },
          'surface-2':  { DEFAULT: '#f1f3f5', dark: '#1a1e24' },
          'surface-3':  { DEFAULT: '#e9ecef', dark: '#21262d' },
          hover:        { DEFAULT: '#f1f3f5', dark: '#1f242b' },

          /* ── Borders ─────────────────────────────────── */
          border:       { DEFAULT: '#dee2e6', dark: '#262b32' },
          'border-strong': { DEFAULT: '#ced4da', dark: '#353b44' },
          'border-faint':  { DEFAULT: '#e9ecef', dark: '#1e2228' },

          /* ── Text ────────────────────────────────────── */
          text:         { DEFAULT: '#1a1d21', dark: '#e6e8ec' },
          'text-mid':   { DEFAULT: '#495057', dark: '#a8acb3' },
          'text-dim':   { DEFAULT: '#868e96', dark: '#6b7079' },
          'text-faint': { DEFAULT: '#adb5bd', dark: '#464a52' },

          /* ── Accent (amber/gold) ─────────────────────── */
          accent:       { DEFAULT: '#e09000', dark: '#f5a623' },
          'accent-2':   { DEFAULT: '#d4850a', dark: '#ffc15c' },
          'accent-soft': { DEFAULT: 'rgba(224, 144, 0, 0.10)', dark: 'rgba(245, 166, 35, 0.14)' },
          'accent-border': { DEFAULT: 'rgba(224, 144, 0, 0.28)', dark: 'rgba(245, 166, 35, 0.32)' },

          /* ── Semantic ────────────────────────────────── */
          success:      { DEFAULT: '#37b24d', dark: '#4ade80' },
          'success-soft': { DEFAULT: 'rgba(55, 178, 77, 0.10)', dark: 'rgba(74, 222, 128, 0.12)' },
          info:         { DEFAULT: '#4c6ef5', dark: '#7aa2f7' },
          'info-soft':  { DEFAULT: 'rgba(76, 110, 245, 0.10)', dark: 'rgba(122, 162, 247, 0.13)' },
          danger:       { DEFAULT: '#e03131', dark: '#f87171' },
          'danger-soft': { DEFAULT: 'rgba(224, 49, 49, 0.10)', dark: 'rgba(248, 113, 113, 0.12)' },
          violet:       { DEFAULT: '#7950f2', dark: '#b48ce8' },
          'violet-soft': { DEFAULT: 'rgba(121, 80, 242, 0.10)', dark: 'rgba(180, 140, 232, 0.13)' },
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
