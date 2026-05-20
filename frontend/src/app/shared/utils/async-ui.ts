import { Observable, finalize } from 'rxjs';

export interface LoadingState<T = unknown> {
  loading: boolean;
  error: string;
  data?: T;
}

export function createLoadingState<T = unknown>(): LoadingState<T> {
  return { loading: false, error: '' };
}

export function runAction<T>(options: {
  action$: Observable<T>;
  setLoading?: (loading: boolean) => void;
  setError?: (error: string) => void;
  errorMessage?: string | ((error: unknown) => string);
  next?: (value: T) => void;
  error?: (error: unknown) => void;
}): void {
  options.setLoading?.(true);
  options.setError?.('');
  options.action$.pipe(
    finalize(() => options.setLoading?.(false))
  ).subscribe({
    next: value => options.next?.(value),
    error: error => {
      const message = typeof options.errorMessage === 'function'
        ? options.errorMessage(error)
        : options.errorMessage;
      options.setError?.(message ?? 'Something went wrong. Please try again.');
      options.error?.(error);
    }
  });
}

export function resetFlagAfter(setter: (value: boolean) => void, delayMs = 2000): void {
  setTimeout(() => setter(false), delayMs);
}

export function clearTextAfter(setter: (value: string) => void, delayMs = 2000): void {
  setTimeout(() => setter(''), delayMs);
}
