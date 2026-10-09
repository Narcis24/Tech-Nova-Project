import { defineConfig } from 'vitest/config';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig({
  // Resolves the path aliases declared in tsconfig.json, including the ones
  // added by `nest g library`.
  plugins: [tsconfigPaths()],
  test: {
    globals: true,
    root: './',
    // e2e specs use an in-memory user store, so they run here too and count toward coverage
    include: ['**/*.spec.ts', '**/*.e2e-spec.ts'],
    // lcov feeds SonarQube
    coverage: { reporter: ['text', 'lcov'], include: ['src/**/*.ts'] },
  },
});
