# AutoApplicant

An AI-powered job application platform that helps candidates go from job posting to polished, tailored application documents. AutoApplicant crawls and imports job postings, maintains a structured career profile, and uses AI to generate tailored CVs, cover letters, and ATS (Applicant Tracking System) reports — exported as professionally rendered PDFs.

## Features

- **Career profile management** — structured profile with experience, education, skills, and projects; can be bootstrapped by parsing an existing CV
- **Job discovery** — multi-source job crawler (Jobindex, Jobnet, IT-Jobbank, Jobdanmark, + ATS boards) and Postgres full-text and pgvector semantic search
- **LinkedIn job connector** — personal-use, low-volume connector over LinkedIn's public `jobs-guest` endpoints, driven by LLM-generated per-user keyword plans; runs on its own jittered schedule off the shared crawl (see `docs/guides/db.md` / `application.yml` `app.linkedin.*`)
- **AI document generation** — tailored CVs and cover letters generated against a specific posting, with a configurable **automatic drafter→reviewer loop** that critiques and revises each draft before assembly
- **ATS reports** — automated analysis of how well a generated document matches the target posting
- **Prompt-safety hardening** — anti-fabrication rules (incl. tool-of-trade conflation), a prompt-injection guard treating scraped/posted job text as untrusted data, and a **deterministic fact gate** that flags invented/inflated metrics not supported by the profile (model-free, zero token cost)
- **Pluggable AI providers** — OpenAI, Gemini, or a **local CLI-agent** (Claude Code / Codex) for generation to run on a flat-fee subscription instead of API calls; embeddings always use a real API
- **Structured document pipeline** — all AI output is structured JSON (never raw text blobs), assembled server-side into a `StructuredDocument` with identity, sections, and rendering options
- **PDF export** — ATS-friendly and designed templates rendered server-side
- **Privacy by design** — personally identifying fields (name, email, phone, photo, links) are *never* sent to the AI provider; identity is merged into documents after the AI call
- **Authentication** — Firebase JWT; optional LinkedIn integration
- **API documentation** — full OpenAPI spec with Swagger UI

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot, Gradle (multi-module) |
| Architecture | Hexagonal (Ports & Adapters) — `domain` / `port` / `usecase` / `adapter` |
| Frontend | Angular (standalone components, lazy-loaded routes) |
| Database | PostgreSQL with Flyway migrations |
| Search | Postgres full-text (`danish` config) + pgvector semantic search (`text-embedding-3-small`) |
| AI | OpenAI / Gemini API, or a local CLI agent (Claude Code / Codex) for generation |
| Auth | Firebase Authentication (JWT), optional LinkedIn OAuth |
| Docs | Springdoc OpenAPI / Swagger UI |
| Infra | Docker Compose (Postgres, backend, frontend) |

## Architecture

The backend strictly follows hexagonal architecture — every cross-boundary interaction goes through a port interface:

```
adapter/            Spring controllers, JPA adapters, AI client, crawler, PDF renderer
  web/controller/   REST endpoints — depend on port/in interfaces only
  persistence/      JPA entities + adapters implementing port/out
  ai/               OpenAI client implementing AiProviderPort
  crawler/          Job posting crawler
  pdf/              PDF rendering
port/
  in/               Use case interfaces (what the application can do)
  out/              Repository/external service interfaces (what the app needs)
usecase/            Business logic — implements port/in, depends only on port/out
domain/             Pure records/value objects — no framework dependencies
```

The frontend mirrors this discipline: `core/api` (HTTP services), `core/models` (interfaces mirroring backend records), `features` (routed components), `shared/components` (presentational).

## Getting Started

### Full stack (Docker)

```powershell
Copy-Item .env.example .env   # then fill in secrets
docker compose --env-file .env -f infra/docker-compose.yml up --build
```

### Local development

```powershell
# Dependencies only
docker compose --env-file .env -f infra/docker-compose.yml up postgres -d

# Backend
./gradlew :backend:bootRun

# Frontend
cd frontend; npm install; npm run start:local
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

### Required configuration

- `OPENAI_API_KEY` — OpenAI key with access to `gpt-4o` and `text-embedding-3-small`
- Firebase service account JSON at `.secrets/firebase-service-account.json`

Optional: `DB_*`, `LINKEDIN_CLIENT_ID`/`SECRET`, `ALLOWED_ORIGINS` (all have local defaults).

AI provider / feature toggles (all optional, sensible defaults):

- `GENERATION_AI_PROVIDER` — `openai` (default) · `gemini` · `claude-cli` / `codex` / `cli` (local agent). `ENRICHMENT_AI_PROVIDER` must stay a real API (produces embeddings).
- `AI_CLI_COMMAND` — CLI invoked for generation when using a local agent (default `claude -p`; prompt piped to stdin).
- `AUTO_REVIEW_ENABLED` — automatic reviewer critique/revise pass after generation (default `true`; each pass is one extra LLM call).
- `FACT_GUARD_ENABLED` / `FACT_GUARD_MODE` — deterministic fact gate on generated metrics (`warn` default, or `block`).
- `LINKEDIN_SCRAPER_ENABLED`, `LINKEDIN_LOCATIONS` — LinkedIn job connector (see `app.linkedin.*` in `application.yml`).

## Documentation

Full docs live in [`docs/`](docs/) (see the [index](docs/README.md)): `specs/`,
`architecture/`, `guides/` (setup, commands, testing, db), `product/` (strategy,
features, the Danish-market playbook), and `archive/` for superseded material.
Agent guidance is in [`CLAUDE.md`](CLAUDE.md).

## Tests

```bash
./gradlew :backend:test          # backend
cd frontend && npm test          # frontend
cd frontend && npm run lint      # lint
```
