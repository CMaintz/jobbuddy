# AutoApplicant — Full Codebase Audit Report

**Audit date:** 2026-05-22
**Scope:** Security & PII, GDPR compliance, UX completeness, deployment readiness
**Status of findings:** Items marked ✅ have been fixed as part of this audit. Items marked ⚠️ are documented for the frontend rewrite or require manual action.

---

## 1. Security & PII

### 1.1 Real credentials in `.env` committed to repo — CRITICAL ✅ (partially)

| Field | Value exposed |
|-------|--------------|
| `OPENAI_API_KEY` | `sk-proj-mLy7-...` (revoked by user) |
| `JWT_SECRET` | `Y0uyDyTQM1Q4sc4yv4j7...` (removed — unused) |
| `DB_PASS` | `autoapplicant` (local dev only) |
| Firebase config | project ID, API key, app ID |

**Resolution:**
- OpenAI key was revoked by the user and replaced with a new key.
- `JWT_SECRET` was removed from `.env` — confirmed unused in the codebase (Firebase JWT, not custom JWT).
- `.env` was never committed to git (`git ls-files .env` returns empty) — no history purge needed.
- `.env.example` updated with safe placeholders and comments.
- `CLAUDE.md` commands updated to pass `--env-file .env` explicitly (Docker Compose v2 issue).

**Remaining action:** If using a non-local DB at any point, rotate `DB_PASS` / `POSTGRES_PASSWORD`.

---

### 1.2 CORS allows wildcard headers with credentials — MEDIUM ✅

**File:** `backend/src/main/java/com/autoapplicant/config/SecurityConfig.java`

**Before:** `config.setAllowedHeaders(List.of("*"))` combined with `allowCredentials(true)` — this violates the CORS spec (browsers reject `*` with credentials).

**After:**
```java
config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
config.setExposedHeaders(List.of("Content-Disposition"));
```

---

### 1.3 Swagger/OpenAPI exposed without auth in production — MEDIUM ✅

**File:** `backend/src/main/resources/application.yml` + new `application-prod.yml`

**Resolution:** Added `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` properties controlled by `${SWAGGER_ENABLED:true}`. Both are set to `false` in `application-prod.yml` and in `docker-compose.prod.yml` via `SWAGGER_ENABLED=false`.

Swagger remains enabled in dev (default). In production, `/swagger-ui.html` and `/v3/api-docs` return 404.

---

### 1.4 `/uploads/**` endpoint is permit-all — LOW/MEDIUM ⚠️

**File:** `SecurityConfig.java` line ~44

Uploaded files (profile photos) are publicly accessible to anyone who knows the URL. There is no per-user scope validation.

**Recommendation for production:** Either:
- Replace `LocalFileStorageAdapter` with GCS/S3 signed URLs (recommended — also needed for multi-instance scaling).
- Or add a controller that validates the requesting user owns the file before streaming it.

Currently the paths are UUIDs (hard to guess), which provides minimal security-through-obscurity only.

---

### 1.5 PII sent to OpenAI — DPA required ⚠️

**File:** `usecase/document/CareerProfileContextService.java` + `domain/document/CareerProfileForAi.java`

**Positive finding:** `CareerProfileForAi` correctly excludes `name`, `email`, `phone`, `photoUrl`, `linkedinUrl`, `githubUrl`, `websiteUrl`. Identity is assembled server-side after the AI call.

**Remaining risk:** Work history, education, company names, and skills are quasi-identifying and are sent to OpenAI. Under GDPR, OpenAI must be a Data Processor with a signed Data Processing Agreement (DPA).

**Action required:** Sign OpenAI's DPA (available at platform.openai.com/legal/dpa) and reference it in the Privacy Policy.

---

### 1.6 No rate limiting on auth endpoints — LOW ⚠️

`FirebaseTokenFilter` logs failed token verifications at DEBUG level only. There is no rate limiting on any endpoint.

**Recommendation:** Add Spring rate limiting (`Bucket4j` or API gateway throttling) before a public production launch.

---

### 1.7 Firebase web credentials in `environment.local.ts` — LOW ✅

Firebase web API keys are intentionally public-facing (security is enforced by Firebase Security Rules, not by keeping the key secret). However, they should not be committed.

**Resolution:** `frontend/src/environments/environment*.local.ts` is in `.gitignore`. The Docker build uses `ARG` build arguments from `docker-compose.yml` for production builds.

---

## 2. GDPR Compliance

### 2.1 No account deletion endpoint (Article 17) — HIGH ✅

**Resolution:** Implemented:
- `port/in/user/DeleteUserAccountUseCase.java` — use case interface
- `usecase/user/UserDeletionService.java` — cascades DB delete then removes Firebase user
- `DELETE /api/v1/users/me` endpoint in `UserController.java`
- `V033__add_fk_cascade_interview_reminders.sql` — adds missing FK constraints with `ON DELETE CASCADE` to `interview_questions` and `follow_up_reminders` tables (the only two tables that lacked them)

All other user-owned tables already had `ON DELETE CASCADE` on `users.id`. Deleting the `users` row propagates through the entire schema automatically.

---

### 2.2 Cookie consent banner required — HIGH ⚠️ (frontend rewrite scope)

**Context:**
- Firebase Auth uses `__session` cookie — strictly necessary, no consent required.
- Firebase Analytics uses `_ga` / `_gid` cookies — analytics category, requires GDPR opt-in consent before initialization.

**Required implementation (frontend rewrite):**
1. Block `firebase/analytics` import/initialization until the user explicitly accepts.
2. Store consent in `localStorage` as `{ cookie_consent: "accepted" | "rejected" }`.
3. Show a non-dismissable banner on first visit and on every new session until consent is stored.
4. Banner must link to the Privacy Policy (`/privacy`).
5. "Reject" must be as prominent as "Accept" (no dark patterns).

**Angular implementation approach:**
```typescript
// In app.component.ts, before initializing Analytics:
const consent = localStorage.getItem('cookie_consent');
if (consent === 'accepted') {
  initializeAnalytics(app);
}
```

---

### 2.3 Missing legal pages — HIGH ⚠️ (frontend rewrite scope)

| Page | Route | Status |
|------|-------|--------|
| Privacy Policy | `/privacy` | Missing |
| Terms of Service | `/terms` | Missing |
| Footer with links | all pages | Missing |

**Privacy Policy must disclose:**
- Firebase Auth: email address, Firebase UID — purpose: authentication
- Firebase Analytics: page views, feature usage, device/location — purpose: product improvement
- OpenAI API: professional history (without PII) — purpose: document generation
- Data retention policy (recommend: profile data kept until account deletion, application data 2 years)
- Contact email for data subject requests
- Reference to OpenAI DPA

---

### 2.4 Data Portability (Article 20) not implemented — MEDIUM ⚠️

No data export endpoint exists. For a full GDPR implementation, users should be able to download their data as JSON or CSV.

**Recommendation:** Add `GET /api/v1/users/me/export` that returns all user data as a ZIP or JSON bundle. Lower priority than deletion but required for full compliance.

---

## 3. UX Completeness (Reference for Frontend Rewrite)

### 3.1 Missing pages/routes

| Missing | Notes |
|---------|-------|
| 404 page | `app.routes.ts` catch-all silently redirects to `/dashboard` — should show a real 404 component |
| Cookie consent banner | See section 2.2 |
| `/privacy` | Required for GDPR |
| `/terms` | Required for production |
| Footer with legal links | Required on all pages |
| Global toast/snackbar | No user feedback on CRUD operations (save, delete, error) |

---

### 3.2 Silent error handlers

These handlers swallow errors silently — the user sees nothing if the operation fails:

| File | Line | Context |
|------|------|---------|
| `analytics.component.ts` | ~245 | `error: () => {}` — weekly trend API failure |
| `dashboard.component.ts` | ~160 | `error: () => {}` — reminders API failure |
| `profile.component.ts` | multiple | Socials/strengths save: no error callback |
| `companies.component.ts` | ~123 | Company search: no error callback |

**Recommendation for rewrite:** Implement a global error interceptor (`HttpInterceptor`) that shows a toast notification for all 4xx/5xx responses unless the caller opts out.

---

### 3.3 Missing empty/loading states

- Application timeline: `GetApplicationTimelineUseCase` exists in the backend but no frontend screen renders it.
- `loading-spinner.component.ts` exists but is not consistently used — loading state is inconsistent across pages.
- No global error boundary component.
- `GetSkillGapUseCase` backend exists; no visible frontend route for skill gap analysis.
- `InterviewPrepController` backend exists; no frontend route detected in `app.routes.ts`.

---

### 3.4 Code quality notes (for rewrite reference)

- Profile tabs use `[(ngModel)]` (template-driven forms) while the Apply Wizard uses reactive RxJS patterns — pick one approach for consistency.
- `saveSuccess` flag in `profile.component.ts` is set but never referenced in the template.
- `DailyCount` is imported in `analytics.component.ts` but only `WeeklyTrend` is used.
- Navigation has 14+ top-level links — consider a collapsible sidebar or grouping for production UX.
- No breadcrumbs on deep routes (e.g. `/jobs/:id`).

---

## 4. Deployment Readiness

### 4.1 HTTPS/TLS — not configured for production ✅ (config created)

**Resolution:**
- `frontend/nginx.conf` updated: HTTP block preserved for local dev; HTTPS block added for production with SSL cert mounts, TLSv1.2/1.3, and security headers (`HSTS`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`).
- `infra/docker-compose.prod.yml` mounts `infra/ssl/cert.pem` and `infra/ssl/key.pem`.
- `application-prod.yml` sets `server.forward-headers-strategy=native` so Spring Boot correctly detects HTTPS from Nginx proxy headers.

**To obtain certs (Let's Encrypt):**
```bash
certbot certonly --standalone -d yourdomain.com
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem infra/ssl/cert.pem
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem infra/ssl/key.pem
```

---

### 4.2 Production Spring profile created ✅

**File:** `backend/src/main/resources/application-prod.yml`

- Swagger disabled (`springdoc.*.enabled=false`)
- Logging: root=INFO, `com.autoapplicant`=WARN, JSON format for log aggregators
- Health endpoint: `show-details=never`
- Proxy header strategy: `native`

**Activate with:**
```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew :backend:bootRun
# or in Docker:
SPRING_PROFILES_ACTIVE=prod
```

---

### 4.3 Production Docker Compose created ✅

**File:** `infra/docker-compose.prod.yml`

Key differences from dev:
- `SPRING_PROFILES_ACTIVE=prod` on backend
- `SWAGGER_ENABLED=false`
- SSL certs mounted into frontend container
- JSON log driver with rotation limits on all services
- `restart: unless-stopped` on all services
- Named volumes for postgres data, typesense data, and uploads
- No override file / no DEBUG logging

**Run with:**
```powershell
docker compose --env-file .env -f infra/docker-compose.prod.yml up -d
```

---

### 4.4 Firebase Hosting demo config created ✅

**Files:** `firebase.json`, `.firebaserc`

Strategy:
- Frontend: Firebase Hosting serves the Angular SPA
- Backend: Google Cloud Run (`autoapplicant-backend` service, `europe-west1`)
- `/api/**` rewrites proxy to Cloud Run via Firebase Hosting rewrites

**Deploy:**
```bash
# Build frontend
cd frontend && npm run build

# Deploy hosting only
firebase deploy --only hosting

# Or deploy everything (requires Cloud Run service to exist)
firebase deploy
```

**To set up Cloud Run backend:**
```bash
gcloud run deploy autoapplicant-backend \
  --image gcr.io/autoapplicant-7ef7b/backend:latest \
  --region europe-west1 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod,OPENAI_API_KEY=... \
  --allow-unauthenticated
```

---

### 4.5 File storage won't scale beyond one container — HIGH ⚠️

**File:** `adapter/storage/LocalFileStorageAdapter.java`

Profile photos are stored on the container filesystem. In a multi-instance or Cloud Run deployment, each instance has its own filesystem — files uploaded to one instance are invisible to others.

**Required before scaling:** Replace `LocalFileStorageAdapter` with a GCS/S3 implementation:
1. Create `GcsFileStorageAdapter implements FileStoragePort`
2. Use `spring-cloud-gcp-storage` or the GCS Java SDK
3. Return public GCS URLs or signed URLs from `FileStoragePort.store()`

---

### 4.6 Email notifications are stubbed — MEDIUM ⚠️

**File:** `adapter/notification/StubNotificationAdapter.java`

Only logs; never sends email. Replace with Spring Mail + SMTP or a transactional provider (SendGrid, Postmark) before enabling notification features.

---

### 4.7 No database backups — HIGH ⚠️

There is no automated backup configuration. For any production deployment:
- **VPS:** Use `pg_dump` via cron + upload to object storage
- **Cloud SQL:** Enable automated backups in the GCP console
- **Minimum:** Daily backup, 7-day retention, test restore quarterly

---

### 4.8 No structured observability — MEDIUM ⚠️

- No correlation IDs on requests (makes log tracing across services hard)
- No metrics beyond Spring Actuator defaults
- No alerting configuration

`application-prod.yml` adds JSON log format which enables log aggregation in Cloud Logging / Datadog / Loki. For production, also consider adding `spring-boot-starter-actuator` Prometheus endpoint and a Grafana dashboard.

---

## 5. Deployment Options Summary

| Option | Cost | Complexity | Scalability | Recommendation |
|--------|------|-----------|-------------|----------------|
| Firebase Hosting + Cloud Run | Low–medium | Medium | Good | Best for personal/demo use |
| VPS + docker-compose.prod.yml | Low | Low | Limited (single node) | Best for small production |
| Cloud managed (AWS ECS / GCP Cloud Run full) | Higher | High | Excellent | Best for SaaS launch |

For a demo or personal project, **Firebase Hosting + Cloud Run** is the recommended path — it scales to zero when unused and has a generous free tier.

---

## 6. Verification Checklist

### Security
- [ ] `git ls-files .env` returns empty (never committed)
- [ ] New OpenAI key is in `.env` and backend starts successfully
- [ ] `GET /swagger-ui.html` with `SWAGGER_ENABLED=false` returns 404
- [ ] CORS: sending `X-Custom-Header` from a non-allowed origin is blocked by browser
- [ ] `DELETE /api/v1/users/me` removes user row and all related rows from DB; Firebase user is deleted

### Deployment
- [ ] `docker compose --env-file .env -f infra/docker-compose.prod.yml up -d` starts without errors
- [ ] `./gradlew :backend:bootRun --args='--spring.profiles.active=prod'` starts with Swagger disabled
- [ ] `firebase deploy --only hosting` succeeds and app is reachable at Firebase Hosting URL
- [ ] HTTPS redirect works: `curl -I http://yourdomain.com` returns 301 to https://

### GDPR
- [ ] Account deletion removes data from all tables (verify with `SELECT COUNT(*) FROM profiles WHERE user_id = ?`)
- [ ] Firebase user is deleted (verify in Firebase Console → Authentication)
- [ ] Cookie consent banner shown to new users before Analytics loads (frontend rewrite scope)
- [ ] Privacy Policy and Terms pages exist and are linked in footer (frontend rewrite scope)
