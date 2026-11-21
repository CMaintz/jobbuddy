The backend could only run as a hosted Firebase app. A `desktop` profile lets it run on one user's own Windows PC, started by the desktop shell (#274), with AI generation through that user's local `claude -p` login.

- No Firebase. A fixed local user (role USER, not ADMIN, so a leaked token grants less) is created at startup and re-created after account deletion, safe under parallel requests.
- Auth: the shell generates a per-launch token (at least 32 chars), passes it as `JOBBUDDY_DESKTOP_TOKEN` and sends it in `X-JobBuddy-Token`. It is compared in constant time and only accepted from loopback. Nothing is public, uploads and health included.
- The server is bound to 127.0.0.1 with forwarded headers off. Startup refuses any other bind or forward-header setting, so an env override can't let a LAN host pose as local. CORS allows only the shell origin, and rate-limit 429s carry it.
- Data and logs go to `APP_DATA_DIR` (default `%LOCALAPPDATA%\JobBuddy`); hosted paths are unchanged.
- Schedules on the PC: LinkedIn (its plans need Claude, and it is safer from a home IP), enrichment and reminders. The general crawl, link checks and expiry run in the GitHub Actions crawl (#265-#269, #272).
- Hosted mode is unchanged. The refactor moved account deletion behind an `IdentityProviderPort` and the LinkedIn callback to a hosted-only controller; the existing security tests, including #263's async-dispatch test, are green.
- Reviewed at high effort with a red-team pass: no blockers. Its hardening points are in.

Closes #273
