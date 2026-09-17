# AutoApplicant — Architecture Notes

> Last updated: 2026-05-21

---

## Current Feature Inventory

### Auth & Onboarding
- Firebase authentication (email/password + Google)
- LinkedIn OAuth callback — exchanges auth code, resolves user, returns Firebase custom token
- CV PDF upload + AI-powered parsing into structured profile

### Profile (fully implemented)
- Basic profile: headline, summary, years of experience, salary expectations, remote/employment-type preference
- Work experience, projects, education, certifications — each with per-entry skill links from taxonomy
- Spoken languages with proficiency levels (NATIVE → ELEMENTARY)
- Social links with drag reorder (`profile_social` table, V027)
- Profile strengths / personal highlights with icon picker (`profile_strength` table, V028)
- Profile photo upload (local filesystem, `/uploads/profile-photos/`)
- Private info split from profile (`profile_private_info` table, V026): full name, phone, location, municipality

### Job Discovery
- Browse scraped jobs (Careerjet, Greenhouse, Lever, Teamtailor, Jobindex, ITJobBank, TheHub, CompanyCareer)
- Full-text + semantic (embedding) search with filters (remote type, employment type, seniority, salary)
- AI-powered job recommendations based on embedding match score
- Save / ignore jobs; manual job entry for off-platform postings

### AI Document Generation (core feature)
- Generates CV, Cover Letter, Application Text, or Recruiter Message tailored to a job
- Real-time split editor + chat-based refinement panel
- 10 templates across 5 families (Classic, Modern, Minimal, Creative, Executive)
- ATS vs. Designed export mode derived from template key
- ATS compliance report: keyword coverage %, matched/missing keywords, per-check results
- PDF export; save directly as an application

### Application Tracking
- Table view + Kanban pipeline
- 10 statuses: SAVED → PREPARING → APPLIED → RECRUITER_CONTACT → INTERVIEW → TECHNICAL_TEST → FINAL_ROUND → OFFER → REJECTED → ARCHIVED
- Attach generated documents to applications; recruiter contact tracking; notes per job/application

### Analytics
- Dashboard: funnel summary, applied this week, active applications, upcoming interviews, recommendations
- Detailed metrics: response rate, interview rate, offer rate, top companies, applied this week/month
- Week-over-week comparison + 14-day sparkline bar chart (gray = prior week, blue = current week)

### Interview Prep (fully implemented)
- AI-generated interview questions per job (BEHAVIORAL, TECHNICAL, SITUATIONAL, COMPANY categories)
- STAR answer drafting (inline editor per question)
- Practiced checkbox tracking with progress counter
- Manual question add with category selector
- Backend: `interview_questions` table (V031), full hexagonal stack

### Follow-up Reminders (fully implemented)
- Per-application reminders with due date, note, and completed state
- Dashboard widget showing reminders due within 24 hours (color-coded: red = overdue, yellow = due soon)
- Quick date shortcuts (+1/3/7/14 days) and datetime picker
- Backend: `follow_up_reminders` table (V032), full hexagonal stack

### Supporting Features
- Prompt template management (CRUD, duplicate, categories)
- CV version history with primary designation; CV quality analysis
- Writing style profile (stored, not yet wired into generation)
- Resume builder: 6 layouts, full form editor, draft save/load via JSONB (`resume_draft` table, V029)
- Document template browser (10 templates)
- Admin crawler trigger endpoint (`/api/v1/admin/crawler`, ADMIN role required)

---

## Architecture Violations Found (and Status)

### Hexagonal — `ProfilePhotoController` file I/O  ✅ FIXED
`Files.createDirectories()` and `file.transferTo()` were called directly inside the controller.
**Fix:** Extracted `FileStoragePort` (port/out) + `LocalFileStorageAdapter` (adapter/storage). Controller now calls `ManageProfilePrivateInfoUseCase.uploadPhoto(userId, file)`.

### Hexagonal — `AiController` concrete service injection  ✅ FIXED
`AiController` injected `StructuredDocumentService`, `StructuredGeneratedDocumentService`,
`CvVersionRepositoryPort`, `JobRepositoryPort`, and `GeneratedDocumentRepositoryPort` directly
(concrete classes + port/out interfaces — both are forbidden in a controller).

**Fix:**
- Added `GetCvRenderModelUseCase`, `GenerateTailoredCvUseCase`, `PersistGeneratedDocumentUseCase` to `port/in/document`.
- Extended `AnalyzeCvUseCase` with `analyze(userId, cvVersionId, jobId)` — ID resolution moved into `AiService`.
- `AiController` now depends only on `port/in` interfaces.

### Hexagonal — `AiService` use-case-to-use-case concrete injection  ✅ FIXED
`AiService` (a use case) directly injected `StructuredDocumentService` and
`StructuredGeneratedDocumentService` (also use cases) as concrete classes.

**Fix:** Added `BuildApplicationDocumentPort` and `PersistGeneratedDocumentPort` to
`port/out/document/`. Both existing services implement these ports.
`AiService` now depends only on the port interfaces.

### SOLID — `UserService` mixing auth and profile concerns  ✅ FIXED
`UserService` implemented `GetUserProfileUseCase`, `UpdateUserProfileUseCase`, and
`UpdatePreferencesUseCase` **and** contained `findOrCreateUserFromFirebase` + `setUserRole`
(Firebase provisioning + admin concerns). `FirebaseTokenFilter` injected the concrete
`UserService` class directly.

**Fix:**
- Extracted `ProvisionFirebaseUserUseCase` (port/in/auth) + `FirebaseUserProvisioningService` (usecase/auth).
- Extracted `ManageUserRoleUseCase` (port/in/auth) — implemented by same service.
- `FirebaseTokenFilter` now injects `ProvisionFirebaseUserUseCase`.
- `UserService` retains only profile/preferences CRUD and drops `FirebaseAuth` dependency.

### SOLID — `AuthController` injecting `UserRepositoryPort` (port/out in a controller)  ✅ FIXED
`AuthController.me()` called `userRepo.findById(userId)` directly.
**Fix:** Added `getUser(UUID userId) → Optional<User>` to `GetUserProfileUseCase`.
`AuthController` now injects `GetUserProfileUseCase`.

### Technical debt — `StructuredDocumentService` deprecated `buildCv` overload  ✅ REMOVED
`buildCv(UUID, String exportMode, String templateId)` was annotated `@Deprecated` with a
migration note. No external call sites existed. Removed.
Same for deprecated `generateTailoredCv(UUID, UUID, String, String, String, String, String)`.

---

## Missing Features / Product Backlog

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 1 | **Job alerts / push notifications** | ❌ Not started | `notificationEnabled` + `notificationFrequency` stored in preferences. No scheduler, no email sender, no WebSocket. Full delivery mechanism needed. |
| 2 | **Week-over-week analytics + graph** | ✅ Done | Backend time-series endpoint + frontend sparkline chart card added. |
| 3 | **Interview prep** | ✅ Done | AI question generation, STAR answers, practiced tracking — full hexagonal stack. |
| 4 | **Follow-up reminders** | ✅ Done | Per-application reminder UI + dashboard due-reminders widget — full hexagonal stack. |
| 5 | **Company research page** | ✅ Done | `GetCompaniesUseCase` + `CompanyService` + `CompanyController` (`/api/v1/companies`). Frontend: `companies-list.component.ts` with debounced search + card grid. |
| 6 | **Skill gap analysis** | ✅ Done | `GET /api/v1/jobs/{jobId}/skill-gap` compares job skills/technologies vs user profile skills. Job detail shows color-coded matched/missing chips + coverage bar. |
| 7 | **Crawler CLI command** | ✅ Done | `ApplicationRunner` with `--crawl` flag added. |
| 8 | **Writing style wired into generation** | ✅ Already done | `PromptCompositionBuilder.buildStyleMemory()` reads tone, vocabularyNotes, phrasingPatterns. |
| 9 | **Job duplicate deduplication UI** | ✅ Done | `duplicateGroupId` added to frontend `Job` model. Job list shows "N similar" amber badge when multiple jobs share a duplicate group. |
| 10 | **Resume builder visual polish** | ✅ Done | All 6 layouts (Classic, Modern 1-col, Modern 2-col, Minimal, Executive, Creative) fully implemented. |
| 11 | **Apply wizard accessibility** | ✅ Done | Wizard fully implemented at `/apply/:jobId`; accessible from job detail "Apply Now" button and `/jobs/add` quick-apply flow. |
| 12 | **Icons with imported library** | ✅ Done | `lucide-angular` installed. Job detail meta badges use lucide icons (MapPin, Briefcase, Monitor, TrendingUp, Banknote). Resume builder all 6 layouts: phone/email/location contact icons via `CONTACT_ICONS` SVG paths (consistent with social icon approach). PDF rendering: inline SVG contact icons (email, phone, location, LinkedIn, GitHub, globe) added to `PdfRenderingService`. |

---

## Key Domain Invariants (quick reference)

- **Privacy invariant:** `CareerProfileForAi` never contains `name`, `email`, `phone`, `photoUrl`, `linkedinUrl`, `githubUrl`, `websiteUrl`. Identity assembled server-side in `CvDocumentAssembler.buildIdentity()` **after** the AI call.
- **Template → export mode:** `exportModeFromTemplate()` in `StructuredDocumentService` is the single source of truth. Never accept `exportMode` as a separate user input.
- **Profile record:** Adding a field to `Profile` requires updating `UserService`, `ProfilePrivateInfoService` (if PII-related), `ProfilePersistenceAdapter`, and all test files. Search for `new Profile(` first.
- **Migrations:** Never modify existing Flyway files. Always add a new `V{NNN}__{description}.sql`.
- **Hexagonal rule:** Controllers → `port/in` only. Use cases → `port/out` only. No concrete class injection across layers. Use cases must not inject other use cases directly — use a `port/out` interface.
- **Embedding dimensions:** The `job_embeddings.embedding` column dimension must match the active model. Current: `vector(3072)` for `gemini-embedding-001`. Switching models always requires a migration (drop + re-add column) because embedding spaces are incomparable across models. OpenAI `text-embedding-3-large` is 3072-compatible; `text-embedding-3-small` needs 1536.

---

## Technical Debt / Upgrade Notes

| # | Item | Notes |
|---|------|-------|
| 1 | **Upgrade Spring Boot 3.2.5 → 3.3+** | Fixes `@JdbcTypeCode(SqlTypes.VECTOR)` on `float[]` so it works out of the box — removes the need for `VectorUserType.java`. Currently on 3.2.5 / Hibernate 6.4. |
| 2 | **Talentech / HR-Manager connector** | `candidate.hr-manager.net` is a Talentech ATS (used widely in Nordics). No public JSON API — the B2B API at `api.hr-manager.net` requires an API key. Candidate site is ASP.NET HTML with `?cid=<companyId>` params. Crawlable via HTML scraping if we collect company `cid` values, but no clean public endpoint like Greenhouse. Revisit if demand warrants it. |
