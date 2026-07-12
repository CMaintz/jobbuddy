# AutoApplicant

An AI-powered job application platform that helps candidates go from job posting to polished, tailored application documents. AutoApplicant crawls and imports job postings, maintains a structured career profile, and uses AI to generate tailored CVs, cover letters, and ATS (Applicant Tracking System) reports — exported as professionally rendered PDFs.

## Features

- **Career profile management** — structured profile with experience, education, skills, and projects; can be bootstrapped by parsing an existing CV
- **Job discovery** — job posting crawler and full-text/semantic search powered by Typesense
- **AI document generation** — tailored CVs and cover letters generated with OpenAI (`gpt-4o`), matched against a specific job posting
- **ATS reports** — automated analysis of how well a generated document matches the target posting
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
| Search | Typesense (keyword + vector search, `text-embedding-3-small`) |
| AI | OpenAI API (`gpt-4o`, embeddings) |
| Auth | Firebase Authentication (JWT), optional LinkedIn OAuth |
| Docs | Springdoc OpenAPI / Swagger UI |
| Infra | Docker Compose (Postgres, Typesense, backend, frontend) |

## Architecture

The backend strictly follows hexagonal architecture — every cross-boundary interaction goes through a port interface:

```
adapter/            Spring controllers, JPA adapters, AI client, crawler, PDF renderer
  web/controller/   REST endpoints — depend on port/in interfaces only
  persistence/      JPA entities + adapters implementing port/out
  ai/               OpenAI client implementing AiProviderPort
  crawler/          Job posting crawler
  pdf/              PDF rendering
  search/           Typesense adapter
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
docker compose --env-file .env -f infra/docker-compose.yml up postgres typesense -d

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
| Typesense | http://localhost:8108 |

### Required configuration

- `OPENAI_API_KEY` — OpenAI key with access to `gpt-4o` and `text-embedding-3-small`
- Firebase service account JSON at `.secrets/firebase-service-account.json`

Optional: `DB_*`, `TYPESENSE_*`, `LINKEDIN_CLIENT_ID`/`SECRET`, `ALLOWED_ORIGINS` (all have local defaults).

## Tests

```powershell
./gradlew :backend:test          # backend
cd frontend; npm test            # frontend
cd frontend; npm run lint        # lint
```
