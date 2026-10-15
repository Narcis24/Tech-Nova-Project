import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';

export default tseslint.config(
  {
    ignores: [
      'src/app/generated/**',
      'src/app/api/auth-api.ts',
      'dist/**',
      'coverage/**',
      '.angular/**',
      'playwright-report/**',
      'test-results/**',
    ],
  },
  {
    files: ['src/**/*.ts'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, ...angular.configs.tsRecommended],
    processor: angular.processInlineTemplates,
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['src/**/*.html'],
    extends: [...angular.configs.templateRecommended],
    rules: { '@angular-eslint/template/eqeqeq': ['error', { allowNullOrUndefined: true }] },
  },
  {
    files: ['e2e/**/*.ts'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
  },
);
