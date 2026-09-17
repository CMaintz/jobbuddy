# Testing Strategy

# Purpose

Define testing standards and quality assurance strategy for:

- crawlers
- matching engine
- AI generation
- document generation
- APIs
- frontend flows

---

# Testing Pyramid

## Unit Tests

Focus:

- domain logic
- matching algorithms
- prompt composition
- filtering
- normalization
- deduplication

Recommended:

- JUnit 5
- Mockito
- AssertJ

---

## Integration Tests

Focus:

- PostgreSQL integration
- crawler pipelines
- AI providers
- search indexing
- PDF generation

Recommended:

- Spring Boot Test
- Testcontainers

---

## End-to-End Tests

Focus:

- job search flow
- application flow
- CV generation flow
- dashboard flow
- authentication flow

Recommended:

- Playwright

---

# Scraper Testing

Requirements:

- fixture-based tests
- HTML snapshot tests
- selector drift detection
- extraction validation

Store sanitized HTML snapshots for:

- LinkedIn
- Jobindex
- Computerworld
- The Hub

---

# AI Regression Testing

Store:

- prompts
- context
- expected constraints
- output examples

Goals:

- prevent quality regressions
- detect hallucinations
- maintain output consistency

---

# Matching Engine Testing

Test:

- score weighting
- embedding similarity
- hard constraints
- ranking stability
- personalization behavior

---

# PDF & Document Testing

Validate:

- PDF rendering
- ATS readability
- layout consistency
- Unicode support
- export stability

---

# CI/CD Recommendations

Pipeline:

```text
lint
→ unit tests
→ integration tests
→ frontend tests
→ build
→ Docker validation
```

---

# Recommended Tooling

## Backend

- Spotless
- Checkstyle
- SonarQube
- JaCoCo

## Frontend

- ESLint
- Prettier
- Angular strict mode

---

# Repository Structure

```text
/backend
/frontend
/infrastructure
/docs
/prompts
/test-fixtures
```



---

# Additional Product Features & Strategic Improvements

# Skill Taxonomy Layer

The platform should maintain normalized technical skill relationships.

Example:

```text
Java
→ Spring
→ Spring Boot
→ Hibernate
```

And:

```text
JavaScript
→ TypeScript
→ Angular
→ RxJS
```

Purpose:

- improved semantic matching
- inferred skill relationships
- missing skill analysis
- transferability scoring
- stronger personalization

---

# AI Explainability

Matching results should explain WHY a job is recommended.

Example:

- strong Java overlap
- Kubernetes experience matches
- backend-heavy role
- remote preference aligned

Purpose:

Improve user trust.

---

# User Feedback Loops

Users should be able to:

- like/dislike recommendations
- hide recommendation types
- request more jobs like specific postings
- request fewer jobs like specific postings

Purpose:

Improve personalization quality.

---

# Interview Tracking

Potential features:

- interview notes
- coding challenge tracking
- follow-up reminders
- recruiter impressions
- AI interview preparation

---

# Recruiter / Contact CRM

Potential future features:

- recruiter tracking
- hiring manager tracking
- networking notes
- referral tracking
- communication history

---

# Document Types

Important distinction:

Cover letters and application texts are not always the same.

Recommended document types:

```text
CV
CoverLetter
ApplicationText
RecruiterMessage
FollowUpMessage
```

---

# Language Handling

The system should support:

- Danish generation
- English generation
- bilingual workflows

Default behavior:

Infer language from:

- job listing language
- company locale
- posting metadata

Users should always be able to override output language.

---

# Canonical Career Profile (Master CV)

Recommended architecture:

Users maintain a structured master profile containing:

- all experience
- all projects
- all skills
- all certifications
- all education
- all achievements

Then:

```text
Master Profile
+ Job Listing
+ User Strategy
+ AI Tailoring
→ Job-Specific CV
```

This should become the source-of-truth career profile.

---

# CV Data Import

Potential import methods:

- CV PDF upload
- LinkedIn PDF export upload
- manual onboarding wizard
- future browser-extension extraction

Recommended flow:

```text
Upload CV
→ AI Extraction
→ Structured Parsing
→ Profile Normalization
→ Editable Master Profile
```

---

# Software Developer CV Optimization

The platform should strongly support technical/software engineering CVs.

Important structured sections:

## Technical Skills

Grouped by:

- languages
- frameworks
- cloud
- databases
- DevOps
- tooling
- frontend/backend

---

## Projects

Projects should support:

- descriptions
- tech stacks
- architecture details
- business impact
- GitHub links
- screenshots optionally

---

## Technical Experience Metadata

Potential fields:

- years of experience
- proficiency
- production experience
- recent usage

---

# ATS-Friendly Two-Column Templates

Carefully designed two-column templates can still be ATS-friendly.

Requirements:

- clean hierarchy
- semantic structure
- no image-based text
- minimal decorative complexity
- proper headings
- simple typography

The platform should support:

- ATS-safe templates
- modern visual templates

And communicate tradeoffs clearly.

---

# Recommended Rendering Architecture

Recommended approach:

```text
Structured CV Data
→ Angular Components
→ HTML/CSS Templates
→ Playwright/Puppeteer PDF Rendering
```

---

# Recommended Editor & Rendering Libraries

## Rich Text Editing

- TipTap
- Lexical
- ProseMirror

---

## PDF Rendering

- Puppeteer
- Playwright

---

## Drag & Drop (Future)

- Angular CDK Drag/Drop

