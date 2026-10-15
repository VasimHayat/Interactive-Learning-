/** Tailwind 3.x — tokens are CSS custom properties defined in src/scss/_tokens.scss
 *  so utilities and hand-written SCSS resolve to exactly one palette. */
export default {
  content: ['./src/templates/**/*.html', './src/sections/**/*.html', './src/js/**/*.js'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--lm-bg)',
        surface: 'var(--lm-surface)',
        'surface-2': 'var(--lm-surface-2)',
        line: 'var(--lm-border)',
        'line-strong': 'var(--lm-border-strong)',
        ink: 'var(--lm-text)',
        'ink-muted': 'var(--lm-text-muted)',
        'ink-subtle': 'var(--lm-text-subtle)',
        ok: 'var(--lm-ok)',
        bad: 'var(--lm-bad)',
        warn: 'var(--lm-warn)',
        info: 'var(--lm-info)',
        accent: 'var(--lm-accent)',
        'accent-weak': 'var(--lm-accent-weak)',
      },
      fontFamily: {
        sans: ['ui-sans-serif', 'system-ui', '-apple-system', '"Segoe UI"', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Consolas', 'monospace'],
      },
      fontSize: {
        caption: ['0.8125rem', { lineHeight: '1.45' }],
        sm: ['0.875rem', { lineHeight: '1.55' }],
        base: ['1rem', { lineHeight: '1.65' }],
        lead: ['1.125rem', { lineHeight: '1.6' }],
        h3: ['1.375rem', { lineHeight: '1.3' }],
        h2: ['1.75rem', { lineHeight: '1.25' }],
        h1: ['2.25rem', { lineHeight: '1.15' }],
      },
      maxWidth: { prose: '72ch' },
      borderRadius: { sm: '4px', md: '8px', lg: '12px' },
      boxShadow: {
        sm: 'var(--lm-shadow-sm)',
        md: 'var(--lm-shadow-md)',
      },
      transitionDuration: { DEFAULT: '160ms' },
    },
  },
  plugins: [],
}
