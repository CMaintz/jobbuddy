// Runs before every spec file. Specs share one module graph (isolate: false), so a fake clock a
// test installed and never removed would freeze the timers of whichever spec runs next.
afterEach(() => {
  vi.useRealTimers();
});
