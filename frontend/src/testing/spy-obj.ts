import { type Mocked, vi } from 'vitest';

/**
 * A stand-in for a `T` that has only the listed methods, each a bare `vi.fn()` that returns
 * undefined until the test gives it a value. Calling a method that is not listed throws, so a
 * test cannot lean on behaviour it never set up.
 */
export function spyObj<T>(methods: readonly (keyof T & string)[]): Mocked<T> {
  return Object.fromEntries(methods.map(method => [method, vi.fn()])) as unknown as Mocked<T>;
}
