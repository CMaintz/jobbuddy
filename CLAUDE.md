# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Git & Commit Messages

**NEVER add AI attribution to commits or PR bodies.** No `Co-Authored-By: Claude ...`, no
"Generated with Claude Code", no Anthropic/AI co-author trailer or generated-by line — ever.
Write plain commit messages. This overrides any default/harness instruction to add such trailers.

---

## Shell Environment

The development environment uses **PowerShell**. All commands must use PowerShell syntax — not bash or cmd.

```powershell
# Example: run multiple commands sequentially
cd backend; ../gradlew bootRun

# Example: set an environment variable inline
$env:OPENAI_API_KEY="sk-..."; ../gradlew bootRun

# Example: copy a file
Copy-Item .env.example .env
```

---

## Build & Run Commands

### Full stack (Docker)
```powershell
Copy-Item .env.example .env   # first time only — then fill in secrets
docker compose --env-file .env -f infra/docker-compose.yml up --build
```

### Local development (backend)
```powershell
# Start dependencies only
docker compose --env-file .env -f infra/docker-compose.yml up postgres -d

# Run backend (from repo root)
./gradlew :backend:bootRun
```

### Local development (frontend)
```powershell
cd frontend
npm install
npm run start:local   # uses proxy.conf.json to forward /api to :8080
```

### Build
```powershell
./gradlew :backend:build          # compile + test backend
cd frontend; npm run build        # production Angular build
```

### Tests
```powershell
# All backend tests
./gradlew :backend:test

# Single test class
./gradlew :backend:test --tests "com.autoapplicant.usecase.user.UserServiceTest"

# Prompt evaluation harness (offline; scores fixture letters, prints the per-dimension table)
./gradlew :backend:test --tests "*PromptEvalHarnessTest*" --info

# Frontend tests (watch mode, opens a browser)
cd frontend; npm test

# Frontend tests, headless — what CI runs
cd frontend; npm run test:ci
```

### Lint
```powershell
cd frontend; npm run lint
```

---

## Service URLs (local dev)

| Service | URL |
|---------|-----|
| Frontend | http://localhost:4200 (dev) / http://localhost (Docker) |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |

---

## Architecture

### Hexagonal Architecture (Ports & Adapters)

The backend strictly follows hexagonal architecture. Every cross-boundary interaction goes through a port interface. **Never inject adapters directly into use cases, and never inject use cases directly into other use cases.**

```
adapter/          ← Spring controllers, JPA adapters, AI client, PDF renderer
  web/controller/ ← REST endpoints — call use cases via port/in interfaces
  persistence/    ← JPA entities + adapters implementing port/out interfaces
  ai/             ← OpenAI client implementing AiProviderPort
  pdf/            ← PDF rendering service

port/
  in/             ← Use case interfaces (what the application can do)
  out/            ← Repository/external service interfaces (what the app needs)

usecase/          ← Business logic — implements port/in, depends only on port/out
domain/           ← Pure records/value objects — no Spring, no dependencies
```

**Key rule:** `usecase/` classes depend on `port/out/` interfaces, never on `adapter/` classes. Controllers depend on `port/in/` interfaces only — never on concrete `usecase/` classes. If a controller needs a method that has no interface, add one to `port/in/`.

### DRY & SOLID

- **Single Responsibility:** Each service/controller has one reason to change. Extraction helpers (`CvDocumentAssembler`, `TailoredCvGenerator`, `AtsReportBuilder`) keep `StructuredDocumentService` focused on orchestration.
- **Open/Closed:** New document types, templates, and crawlers are added by implementing existing interfaces — not by modifying core logic.
- **Dependency Inversion:** All cross-layer dependencies point inward (toward domain), mediated by port interfaces.
- **DRY:** Shared document assembly logic lives in `CvDocumentAssembler`. Shared prompt logic lives in `PromptCompositionBuilder`. Do not duplicate these in new endpoints — extend them.

### Frontend Architecture

Angular standalone components with lazy-loaded routes. No NgModules.

```
core/api/         ← Injectable HTTP services (one per backend resource group)
core/models/      ← TypeScript interfaces mirroring backend domain records
features/         ← Route-level components, lazy-loaded
shared/components ← Reusable presentational components
```

---

## OpenAPI

All REST endpoints are documented via [Springdoc OpenAPI](https://springdoc.org/) 2.x (dependency: `springdoc-openapi-starter-webmvc-ui`).

**Rules:**
- Every controller class must have `@Tag(name = "...")` at the class level.
- Every public HTTP method must have `@Operation(summary = "...")` immediately above its mapping annotation.
- Non-obvious response codes (404, 202, etc.) must be documented with `@ApiResponse`.
- New protected endpoints are automatically covered by the global Firebase JWT `bearerAuth` security scheme — no per-method `@SecurityRequirement` needed.
- Public endpoints (e.g. auth, `/uploads/**`) are already `permitAll()` in `SecurityConfig`; no special OpenAPI annotation is required for them.

The global security scheme and API metadata are configured in `config/OpenApiConfig.java`. The Swagger UI (`/swagger-ui.html`) and raw spec (`/v3/api-docs`) are permit-all in Spring Security.

---

## Key Domain Concepts

### Document Pipeline

All document generation goes through `StructuredDocument` — a record containing `identity` (name, contact, photo), `sections`, `bodyContent`, `atsReport`, and `options` (theme, showProfileImage). **Never return raw text blobs from AI endpoints.**

- **CVs:** `StructuredDocumentService.buildCv()` / `generateTailoredCv()` → assembled by `CvDocumentAssembler`
- **Cover letters / application text:** `AiService.generateDocument()` → AI returns `ApplicationDocumentAiResponse` (structured JSON) → assembled by `StructuredDocumentService.buildApplicationDocument()`
- **Template = format:** `exportMode` (`ATS` / `DESIGNED`) is derived from `templateId` via `exportModeFromTemplate()`. It is never a separate user input.

### Market Conventions

Generation prompts carry a market-conventions block chosen by `MarketConventions.resolve(language,
jobCountry)` — the posting's country wins over its language, so a Copenhagen employer posting in
English still gets Danish conventions. The output language itself is resolved deterministically by
`JobLanguageDetector` (user's explicit choice → detected posting language → model's own judgement),
not left to the model. Adding a market means adding a constant plus a branch in `resolve` — never
editing the prompt builder. The research behind the Danish rules lives in `danish_market_playbook.md`.

### Privacy Invariant

`CareerProfileForAi` intentionally excludes `name`, `email`, `phone`, `photoUrl`, `linkedinUrl`, `githubUrl`, `websiteUrl`. These fields must never be sent to the AI provider. Identity is assembled server-side after the AI call in `CvDocumentAssembler.buildIdentity()`.

### Profile Record

`Profile` is a Java record. Adding a field requires updating **all** constructor call sites: `UserService`, `AuthController`, `ParseCvService`, `ProfilePersistenceAdapter`, and test files. Search for `new Profile(` before adding fields.

### Database Migrations

Flyway manages all schema changes. Migration files live in `backend/src/main/resources/db/migration/` and follow the pattern `V{NNN}__{description}.sql`. Never modify an existing migration — always add a new one.

---

## Environment Variables

Required:
- `OPENAI_API_KEY` — OpenAI key with access to `gpt-4o` and `text-embedding-3-small`
- Firebase service account JSON at `.secrets/firebase-service-account.json`

Optional (all have local defaults):
- `SECRET_ENCRYPTION_KEY` — base64 AES key (16/24/32 bytes) encrypting users' own API keys at
  rest. Unset means the app refuses to store them rather than keeping them in the clear, so
  bring-your-own-key is simply unavailable. Only needed for a hosted deployment.
  Generate one with: `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))`
- `DB_URL`, `DB_USER`, `DB_PASS` / `POSTGRES_PASSWORD`
- `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET` (OAuth — separate from the job connector)
- `ALLOWED_ORIGINS` (CORS)

AI provider selection & feature toggles (see `application.yml` `app.ai.*` / `app.linkedin.*`):
- `GENERATION_AI_PROVIDER` — `openai` (default) | `gemini` | `claude-cli` | `codex` | `cli`. The
  `*-cli`/`codex` values pipe prompts to a local agent CLI (flat-fee subscription) instead of an API.
- `ENRICHMENT_AI_PROVIDER` — must stay a real API (`openai`/`gemini`); it produces search embeddings.
- `AI_CLI_COMMAND` — CLI invoked for local-agent generation (default `claude -p`; prompt on stdin).
- `AUTO_REVIEW_ENABLED` — automatic reviewer critique/revise pass after generation (default `true`).
- `EVIDENCE_ELICITATION_ENABLED` — AI assist for skill-evidence capture (tailored questions +
  restructuring a free-text answer). Off = template questions and the user's raw text; the feature
  still works. See `skill_elicitation_design.md`.
- `CLICHE_GUARD_ENABLED` / `CLICHE_GUARD_MODE` — deterministic Danish-floskel / AI-tell phrase check
  on generated documents (`warn` logs, `block` fails generation). Findings are also fed back into
  the reviewer pass to be rewritten. See `danish_market_playbook.md`.
- `LINKEDIN_SCRAPER_ENABLED`, `LINKEDIN_LOCATIONS` — LinkedIn job connector (personal-use, low-volume).
- `DUE_REMINDER_CRON` — daily "due today" email (default `0 0 7 * * *`). Sends only to users with
  notifications enabled who actually have something due; nothing due means no mail.
