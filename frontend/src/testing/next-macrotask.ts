/**
 * Resolves on the next macrotask, by which time every microtask queued so far, and every one
 * those queue in turn, has run. A single awaited promise would let only the first link of a
 * promise chain through. Never await it after `vi.useFakeTimers()`: its `setTimeout` is then
 * fake and the promise never resolves.
 */
export function nextMacrotask(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve));
}
