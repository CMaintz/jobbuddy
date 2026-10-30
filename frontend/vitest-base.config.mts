import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Randomised order surfaces ordering dependencies between specs instead of hiding them.
    sequence: { shuffle: true },
  },
});
