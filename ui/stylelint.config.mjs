// Colours live in theme variables, so light and dark mode change in one place (src/styles.css).
// Components use var(--…); transparent, currentColor and color-mix() are still allowed.
/** @type {import('stylelint').Config} */
export default {
  rules: {
    'color-no-hex': true,
    'color-named': 'never',
    'function-disallowed-list': ['rgb', 'rgba', 'hsl', 'hsla'],
  },
  overrides: [
    {
      // Where the theme variables are defined
      files: ['src/styles.css'],
      rules: { 'color-no-hex': null, 'color-named': null, 'function-disallowed-list': null },
    },
    {
      // The sign-in and register pages keep their own light-only palette from the design PDF
      files: ['src/app/shared/auth-page.css', 'src/app/features/register/register.component.css'],
      rules: { 'color-no-hex': null, 'color-named': null, 'function-disallowed-list': null },
    },
  ],
};
