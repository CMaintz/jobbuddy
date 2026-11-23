import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

// The Angular unit-test builder owns test discovery and the build; this file carries what it has
// no option for. Specs share one module graph (isolate: false), so a spy or a stubbed global left
// behind by one spec would leak into whichever spec runs next.
export default defineConfig({
  test: {
    // A real browser, not jsdom: some specs check the global stylesheet (pill tones Tailwind must
    // keep) and layout (Buddy must not move), which jsdom renders as no styles and all-zero boxes.
    // The installed Chrome channel needs no Playwright browser download, locally or on the CI runner.
    browser: {
      enabled: true,
      headless: true,
      provider: playwright({ launchOptions: { channel: 'chrome' } }),
      instances: [{ browser: 'chromium' }],
    },
    // Random order surfaces ordering dependencies between specs instead of hiding them.
    sequence: { shuffle: true },
    restoreMocks: true,
    unstubGlobals: true,
    // `ng test --coverage` writes coverage/lcov.info, the path coverage tools look for by default,
    // rather than a folder named after the Angular project.
    coverage: { reportsDirectory: 'coverage' },
  },
});
