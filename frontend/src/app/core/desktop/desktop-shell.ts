import { InjectionToken } from '@angular/core';

/** What the Windows app's preload exposes. The shell keeps the backend's token to itself. */
interface DesktopBridge {
  backendOrigin: string | null;
}

/**
 * Where the backend is when the app runs inside the Windows desktop shell, e.g.
 * `http://127.0.0.1:51234`; null in the browser. The page is served from app://jobbuddy there,
 * so a relative /api URL would never reach the backend.
 */
export const DESKTOP_BACKEND_ORIGIN = new InjectionToken<string | null>('DESKTOP_BACKEND_ORIGIN', {
  providedIn: 'root',
  factory: () =>
    (window as Window & { jobbuddyDesktop?: DesktopBridge }).jobbuddyDesktop?.backendOrigin ?? null,
});
