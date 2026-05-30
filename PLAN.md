# AutoApplicant — Resume Builder & Application Wizard Plan

## Context

AutoApplicant needs a user-driven, interactive resume/CV editor that:
1. Presents a side-by-side editor + live preview with 6 visual templates (ported from `resume-canvas`)
2. Is the **UI layer for job-specific CVs** — AI-generated content flows into it for manual review/refinement
3. Integrates into an "Apply" wizard: job → CV editor → cover letter → persist both + download
4. Persists drafts and finalized documents server-side (not just localStorage)

This work also restructures the Profile data model to properly separate PII from professional data, and adds `Socials` (with platform icons) and `Strengths` as first-class backend entities.

The existing `features/cv/` page (AI-rendered read-only view) is **not touched**. This is a parallel, richer feature.

---

## Decisions on All Open Questions

| # | Question | Decision |
|---|---|---|
| 1 | Zustand action name parity | Natural Angular names (`setPersonalInfo`, `addExperience`, etc.) — matches Zustand names incidentally where obvious |
| 2 | Privacy categorization | **In scope**: split PII from Profile into `profile_private_info` table; `linkedin/github/websiteUrl` migrate into new `profile_social` table |
| 3 | AI → Editor flow | Two-path wizard: (a) "Apply" on any job, (b) manual job paste → AI generates CV → CV Builder → Cover Letter → persist both + download |
| 4 | JSON Resume standard | **Not adopted**. AutoApplicant's model is richer; align naming sensibly (e.g., `url` not `website`, `profiles` → `socials`) without committing to the schema |
| 5 | Socials + Strengths end-to-end | **In scope**: migration, Java entity, domain record, repository, service, controller, DTO, frontend model, API service, forms, preview rendering |
| 6 | Skills on experience/education/projects | Display as compact tag chips under each item in both editor forms and resume templates |
| 7 | Skill expertise level display | Show level (1–5 dots) in Skills section for Designed templates; hidden by default for ATS. Toggle in settings. Tags on experience items are name-only |
| 8 | Company logos | **Out of scope entirely** |
| 9 | Social platform icons | **In scope** — SVG icon per platform (LinkedIn, GitHub, Twitter/X, Website, Behance, Dribbble, Stack Overflow, Email). Rendered in resume preview next to each link |
| 10 | Server-side persistence | New `resume_draft` table. Drafts auto-saved; finalized docs stored as `GeneratedDocument` with `applicationId` link |
| 11 | Responsive | Tailwind responsive prefixes throughout; mobile: tab-based editor/preview toggle |

---

## Part 1: Backend Changes

### 1.1 New Flyway Migrations

**V026__profile_private_info.sql**
```sql
CREATE TABLE profile_private_info (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name       TEXT,
    phone           TEXT,
    photo_url       TEXT,
    location        TEXT,
    municipality    TEXT,
    contact_email   TEXT,   -- added: public-facing email for CV, separate from login email
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Migrates existing PII columns from profile and removes them
```

**V027__profile_social.sql**
```sql
CREATE TABLE profile_social (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform       TEXT NOT NULL,
    url            TEXT NOT NULL,
    username       TEXT,
    icon_key       TEXT NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Migrates linkedin_url, github_url, website_url from profile and removes those columns
```

**V028__profile_strength.sql**
```sql
CREATE TABLE profile_strength (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          TEXT NOT NULL,
    description    TEXT,
    icon_key       TEXT NOT NULL DEFAULT 'star',
    display_order  INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**V029__resume_draft.sql**
```sql
CREATE TABLE resume_draft (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT NOT NULL DEFAULT 'My Resume',
    job_id          UUID REFERENCES jobs(id) ON DELETE SET NULL,
    application_id  UUID REFERENCES applications(id) ON DELETE SET NULL,
    resume_data     JSONB NOT NULL DEFAULT '{}',
    settings        JSONB NOT NULL DEFAULT '{}',
    status          TEXT NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**V030__application_recruiter_reply.sql** *(added during implementation)*
```sql
ALTER TABLE applications ADD COLUMN recruiter_reply TEXT;
```

---

### 1.2 New Java Entities & Domain Records

All created in previous sessions. Domain records follow Java 21 record style.

- `ProfilePrivateInfo` — 10 fields including `contactEmail`
- `ProfileSocial` — platform, url, username, iconKey, displayOrder
- `ProfileStrength` — title, description, iconKey, displayOrder
- `ResumeDraft` — JSONB blobs for resumeData and settings

---

### 1.3 Updated `Profile` Domain Record

PII fields and hardcoded URL fields removed. A `FullProfileResponse` composite DTO assembles all data for the resume builder prefill endpoint.

---

### 1.4 API Endpoints

**Profile Private Info** — `GET/PUT /api/v1/profile/private`, `POST /api/v1/profile/private/photo`

**Socials** — `GET/POST/PUT/DELETE /api/v1/profile/socials`, reorder endpoint

**Strengths** — `GET/POST/PUT/DELETE /api/v1/profile/strengths`, reorder endpoint

**Resume Drafts** — `GET/POST/PUT/DELETE /api/v1/resume-drafts`, publish endpoint

**Applications (extended)** — `PATCH /api/v1/applications/{id}/recruiter` *(added for cold outreach)*

---

## Part 2: Frontend — Feature Structure

### 2.1 Feature Location

`frontend/src/app/features/resume-builder/`

```
features/resume-builder/
├── resume-builder.component.ts          ✅ done
├── editor/
│   ├── resume-editor.component.ts       ✅ done
│   └── forms/
│       ├── personal-info-form.component.ts     ✅
│       ├── experience-form.component.ts        ✅
│       ├── education-form.component.ts         ✅
│       ├── skills-form.component.ts            ✅
│       ├── projects-form.component.ts          ✅
│       ├── languages-form.component.ts         ✅
│       ├── certifications-form.component.ts    ✅
│       ├── strengths-form.component.ts         ✅ (with icon picker)
│       ├── socials-form.component.ts           ✅
│       ├── settings-form.component.ts          ✅
│       └── layout-form.component.ts            ✅
├── preview/
│   ├── resume-preview.component.ts      ✅ (@defer on all layouts)
│   └── layouts/
│       ├── classic-layout.component.ts        ✅
│       ├── modern-1col-layout.component.ts    ✅
│       ├── modern-2col-layout.component.ts    ✅
│       ├── minimal-layout.component.ts        ✅
│       ├── executive-layout.component.ts      ✅
│       └── creative-layout.component.ts       ✅
├── services/
│   ├── resume-state.service.ts          ✅
│   ├── pdf-export.service.ts            ✅
│   └── resume-draft-api.service.ts      ✅
├── models/
│   └── resume-builder.models.ts         ✅
├── shared/
│   ├── debounced-textarea.component.ts  ✅
│   ├── month-year-picker.component.ts   ✅
│   ├── icon-picker.component.ts         ✅ (strengths editor only)
│   └── skill-chip-list.component.ts     ✅
└── data/
    ├── font-families.ts                 ✅
    ├── document-sizes.ts                ✅
    ├── language-proficiencies.ts        ✅
    ├── social-platforms.ts              ✅ (SVG paths for all 8 platforms)
    └── strength-icons.ts                ✅ (emoji map for editor picker)
```

---

## Part 3: Frontend State Management

`ResumeStateService` — signals store with auto-save debounce, prefill from profile, all CRUD methods for 9 sections, settings management. ✅ Done.

---

## Part 4: Models

All frontend models implemented:
- `resume-builder.models.ts` — ResumeData, ResumeSettings, all section interfaces ✅
- `core/models/application.model.ts` — extended with `recruiterReply` ✅
- `core/models/user.model.ts` — `ProfilePrivateInfo` with `contactEmail` ✅
- `core/models/profile-section.model.ts` — `ProfileSocial`, `ProfileStrength` ✅

---

## Part 5: Profile Feature Updates

**Status: DONE ✅**

- Overview tab: PII (`fullName`, `phone`, `location`, `photoUrl`) now loaded from `/api/v1/profile/private`; professional fields (`headline`, `summary`, `yearsExperience`, `technologies`, `skills`) from `/api/v1/users/me/profile`
- `saveProfile` uses `forkJoin` to save both endpoints in parallel
- Photo upload endpoint updated to `/api/v1/profile/private/photo`
- LinkedIn/GitHub/Website URL fields removed from overview form (migrated to Socials tab)
- New **Social Links** tab (`profile-socials-tab.component`) — platform select (8 options with auto-icon), URL, optional username; add/delete
- New **Strengths** tab (`profile-strengths-tab.component`) — title + optional description; add/delete
- Both new tabs appear in `PROFILE_TABS` and are routed in `profile.component.html`

---

## Part 6: Application Wizard Flow

**Status: DONE (Path A only)**

`features/apply/apply-wizard.component.ts` — 4-step wizard at `/apply/:jobId` ✅

- Step 1: AI generates tailored CV, creates resume draft + PREPARING application, animated loading screen ✅
- Step 2: Link to open in resume builder, or continue without editing ✅
- Step 3: Document type picker (Cover Letter vs Application Text), generate + edit in textarea ✅
- Step 4: Download CV, Mark as Applied, View Application ✅

**Path B (manual job paste) — NOT DONE.** No "paste a job description" modal or manual entry flow.

Wizard state persisted in `sessionStorage`. Resume builder shows wizard context banner. ✅

**Cold outreach (added beyond original plan):**
- Job detail has a Recruiter Outreach section: generate message, save recruiter name/email, paste reply ✅
- Recruiter reply stored in `applications.recruiter_reply` ✅
- Wizard picks up recruiter reply and passes it as context to AI generation ✅

---

## Part 7: PDF Export

`PdfExportService` using html2canvas + jsPDF. Download and Print wired in preview. ✅ Done.

---

## Part 8: Social Platform Icons

`social-platforms.ts` with SVG paths for 8 platforms (LinkedIn, GitHub, Website, Twitter/X, Behance, Dribbble, Stack Overflow, Email). ✅

`getSocialPlatformIcon(key)` helper used inline in all layout templates. ✅

Note: A separate `SocialIconComponent` was **not created** — icons are rendered inline in templates directly.

---

## Part 9: Skills Display

- `showSkillLevel` toggle in settings ✅
- 5-dot level indicators in Classic and Modern 2-col layouts ✅
- `SkillChipListComponent` used in all layouts for experience/education/project items ✅

---

## Part 10: npm Packages

- `@angular/cdk` — DragDropModule for layout reorder ✅
- `html2canvas` + `jspdf` — PDF export ✅
- `@ng-icons` — **not installed** (icons done inline with SVG paths instead)
- `uuid` — check `package.json`; crypto.randomUUID() used instead

---

## Implementation Status by Phase

### Phase 1 — Backend data model ✅ COMPLETE
- V026–V030 migrations ✅
- All entities, domain records, repositories ✅
- ProfilePrivateInfo, ProfileSocial, ProfileStrength, ResumeDraft services + controllers ✅
- Profile entity and domain record stripped of PII + URL fields ✅
- AI prompt builders updated to exclude PII ✅
- `contactEmail` added to ProfilePrivateInfo (beyond original plan) ✅
- `recruiter_reply` added to Application (beyond original plan) ✅

### Phase 2 — Frontend core model updates ✅ COMPLETE
- `user.model.ts`, `profile-section.model.ts`, `application.model.ts` updated ✅
- `profile-private.api.ts`, `profile-social.api.ts`, `profile-strength.api.ts` created ✅
- Profile overview tab now loads PII from `/api/v1/profile/private`, professional data from `/api/v1/users/me/profile` ✅
- `saveProfile` writes to both endpoints via `forkJoin` ✅
- Photo upload moved to `/api/v1/profile/private/photo` ✅
- LinkedIn/GitHub URL fields removed from overview tab (now in Socials tab) ✅
- New **Social Links** tab with add/delete UI using `ProfileSocialApiService` ✅
- New **Strengths** tab with add/delete UI using `ProfileStrengthApiService` ✅

### Phase 3 — Resume builder foundation ✅ COMPLETE
- `resume-builder.models.ts` ✅
- `ResumeStateService` ✅
- `ResumeDraftApiService` ✅
- `ResumeBuilderComponent` shell with mobile tabs + wizard banner ✅
- `social-platforms.ts` with SVG icons ✅

### Phase 4 — Editor forms ✅ COMPLETE
All 11 form components created and wired in `ResumeEditorComponent` ✅

### Phase 5 — Preview + templates ✅ COMPLETE
All 6 layouts implemented with skill chips, social icons, photo style, skill level dots ✅
(`resume-section.component.ts` was not created separately — dispatch is inline in `resume-preview.component.ts`)

### Phase 6 — PDF export ✅ COMPLETE
`PdfExportService` + download/print buttons in preview ✅

### Phase 7 — Application wizard ✅ COMPLETE
4-step wizard, wizard state service, resume builder integration ✅
Path B: `/jobs/add` → "Apply Now with AI" → `/apply/:jobId` ✅
Cold outreach flow added (beyond original plan) ✅
Draft publish: `publishDraftSilently()` fires on step 4 ✅

### Phase 8 — Polish ✅ COMPLETE
- Mobile responsive tabs ✅
- `@defer` on all 6 preview layouts ✅
- Photo style toggle (square/rounded/circle) in settings + all layouts ✅
- Skill level toggle ✅
- Icon picker (visual emoji grid) for strengths editor ✅
- Executive layout photo style bug fixed ✅

---

## What's Left / Not Done

| Item | Notes |
|---|---|
| **Profile page UI update** (Phase 2 / Part 5) | ✅ Done — split API wired, Socials + Strengths tabs added |
| **Apply wizard Path B** | ✅ Done — `/jobs/add` now has "Apply Now with AI" as primary CTA; "Paste & Apply" nav link added; existing-job found state also offers "Apply Now" |
| **`SocialIconComponent`** | Plan mentioned a reusable component; instead icons are rendered inline in templates. Functionally equivalent, but no standalone component |
| **`FullProfileResponse` endpoint** | Backend DTO exists but no dedicated `/api/v1/profile/full` endpoint was created; the resume builder prefills by calling the full profile API which assembles from multiple sources |
| **Draft publish flow** | ✅ Done — `publishDraftSilently()` called when wizard reaches step 4 (via "Save & Continue" and "Skip this step"), marks draft as PUBLISHED in DB |
| **Jobs list "applied" status** | Implemented (badges + hide applied filter) ✅ |
| **Recruiter outreach flow** | Implemented in job detail + wired to wizard context ✅ |

---

## Verification Checklist

- [ ] PII split: profile page still shows all data from new split endpoints
- [ ] Socials: create/edit/reorder in profile; appears in resume builder prefill; icon renders in preview
- [ ] Strengths: add in profile; appears in resume builder; renders in Classic + Modern 2-col
- [ ] Resume builder prefill: first visit populates from profile; second visit loads draft
- [ ] Auto-save: manual edit triggers server save within 1.5s
- [ ] Templates: switch all 6; drag reorder sections; preview updates immediately
- [ ] Skill chips: add skill to work experience; renders as chip in preview
- [ ] Skill level dots: toggle showSkillLevel; dots appear/disappear in Skills section
- [ ] Photo style: upload photo; switch square/rounded/circle; renders correctly
- [ ] PDF: download A4 and Letter; verify photo, theme color, fonts, social icons
- [ ] Apply wizard: "Apply" on job → wizard → AI generates → editor → cover letter → done → application linked
- [ ] Cold outreach: generate recruiter message → paste reply → wizard uses reply as AI context
- [ ] No regression: existing /cv page, profile page, application list all work
