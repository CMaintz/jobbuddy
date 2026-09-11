# AutoApplicant — Commands Reference

## Terminal

**Shell:** PowerShell 7
**Profile:** `C:\Users\akash\Documents\PowerShell\Microsoft.PowerShell_profile.ps1`
Reload after edits: `. $PROFILE`

All `aac-*` functions and the `aac` alias are defined in that profile and require PowerShell 7.

---

## Docker — `aac`

Alias for `docker compose` pointed at `infra/docker-compose.yml` + `.env`.

```powershell
aac up --build          # build images and start all services (frontend + backend + DBs)
aac up -d               # start all services in background (no rebuild)
aac up postgres typesense -d   # start only the databases (for local dev / crawling)
aac down                # stop all services, keep volumes (data preserved)
aac down -v             # stop all services and wipe ALL volumes (DB, Typesense, uploads)
aac logs -f backend     # tail logs for a specific service
aac ps                  # show running containers
```

**When to rebuild (`--build`):** only when you change a `Dockerfile` or want to ship code changes into the Docker image (production-like test). Local dev never needs it.

---

## Crawler — `aac-crawl`

Compiles and runs the backend with `--spring.profiles.active=crawler` (no HTTP server, one-shot CLI process). Logs to `crawler.log` in the repo root and to the terminal simultaneously.

```powershell
aac-crawl                      # incremental crawl all sources (stops when caught up)
aac-crawl jobindex             # incremental crawl one source
aac-crawl -f                   # force-crawl all sources (never stops early)
aac-crawl jobindex -f          # force-crawl one source
aac-crawl it_jobbank -f        # force-crawl IT Jobbank
```

**Sources:** `jobindex` `it_jobbank` `greenhouse` `lever` `teamtailor` `careerjet` `cornerstone_ondemand`
**Case-insensitive** — `JOBINDEX` and `jobindex` both work.

**What it does:** crawls → cleans text → classifies job category (RSS tags → keyword match → AI fallback) → saves to DB → async AI enrichment → Typesense indexing → embeddings.

**Prerequisites:** `aac up postgres typesense -d` must be running. `aac-backend` is NOT needed.

**Force mode (`-f`):** disables the "caught-up" early stop — the crawl only halts when the feed returns an empty page or the 10,000-page safety ceiling is hit (~200k jobs). Use for first-time backfills or dev re-crawls.

**Log file:** `C:\Users\akash\IdeaProjects\AutoApplicant\crawler.log` (overwritten each run)

---

## Backend API server — `aac-backend`

Compiles and runs the backend with the default profile (full HTTP server on port 8080).

```powershell
aac-backend
```

**When to use:** when you need the REST API live for frontend development. Not needed for crawling or enrichment.

---

## Enrichment sweep — `aac-enrich`

Runs the enrichment profile to process jobs that were saved without AI enrichment (e.g. after a crawl that crashed mid-way through enrichment).

```powershell
aac-enrich              # enrich up to 500 unenriched jobs
aac-enrich 100          # limit to 100 jobs this run
```

**Prerequisites:** `aac up postgres typesense -d` must be running.

---

## Gradle (manual / CI)

Run from the repo root (`C:\Users\akash\IdeaProjects\AutoApplicant`).

```powershell
.\gradlew.bat :backend:build                         # compile + run all tests
.\gradlew.bat :backend:bootRun                       # run backend (default profile)
.\gradlew.bat :backend:test                          # all backend tests
.\gradlew.bat :backend:test --tests "com.autoapplicant.usecase.user.UserServiceTest"  # single class
```

`aac-crawl` and `aac-backend` call `gradlew.bat` internally and compile automatically — you do not need a separate build step before running them.

---

## Frontend

```powershell
cd frontend
npm install             # first time or after package.json changes
npm run start:local     # dev server on :4200 with proxy to backend :8080
npm run build           # production build
npm test                # unit tests
npm run lint            # ESLint
```

---

## What builds what

| Command | Compiles backend? | Compiles frontend? | Needs Docker? |
|---|---|---|---|
| `aac-crawl` | Yes (Gradle, on-the-fly) | No | postgres + typesense |
| `aac-enrich` | Yes (Gradle, on-the-fly) | No | postgres + typesense |
| `aac-backend` | Yes (Gradle, on-the-fly) | No | postgres + typesense |
| `aac up --build` | Yes (Docker image) | Yes (Docker image) | Full stack |
| `aac up -d` | No (uses existing image) | No (uses existing image) | Full stack |
| `npm run start:local` | No | Yes (Angular) | `aac-backend` running |

---

## Service URLs (local dev)

| Service | URL |
|---|---|
| Frontend (dev) | http://localhost:4200 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Typesense | http://localhost:8108 |

---

## Admin REST endpoints (crawler)

Requires `ADMIN` role. Fire-and-forget (async).

```
POST /api/v1/admin/crawler/trigger                  # incremental crawl all sources
POST /api/v1/admin/crawler/trigger/{source}         # incremental crawl one source
POST /api/v1/admin/crawler/force-trigger            # force-crawl all sources
POST /api/v1/admin/crawler/force-trigger/{source}   # force-crawl one source
```

---

## Database migrations

Managed by Flyway. Files live in:
`backend/src/main/resources/db/migration/`

Naming: `V{NNN}__{description}.sql` — sequential, never modify an existing file, always add a new one.
Migrations run automatically on backend startup.

