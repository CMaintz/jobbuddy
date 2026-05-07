# System Architecture

# Architectural Style

Use Hexagonal Architecture (Ports & Adapters).

Goals:

- modularity
- provider isolation
- testability
- replaceable integrations
- maintainability

---

# High-Level Architecture

```text
Angular Frontend
    ↓
REST API
    ↓
Application Layer
    ↓
Domain Layer
    ↓
Ports
    ↓
Adapters
 ├── PostgreSQL
 ├── Search Engine
 ├── AI Providers
 ├── Scrapers
 ├── Email Integration
 └── External APIs
```

---

# Backend Stack

## Recommendation

- Java 21
- Spring Boot
- Spring Data JPA
- Hibernate
- PostgreSQL
- pgvector

Optional:

- Redis
- Spring WebFlux
- Quartz Scheduler

---

# Frontend Stack

## Recommendation

- Angular
- TypeScript
- Angular Material or Tailwind
- RxJS

---

# Infrastructure

## Initial Recommendation

- Supabase PostgreSQL
- Docker
- Fly.io / Railway / Render

---

# Domain Modules

```text
jobs
crawler
search
matching
applications
users
prompts
ai
analytics
notifications
companies
profiles
cv
projects
documents
personalization
recommendations
```

---

# Additional Architectural Decisions

# Structured Career Profile Architecture

The platform should revolve around a structured master career profile (“Brutto CV”) rather than standalone manually edited CV documents.

The system should store:

- structured experience
- projects
- technical skills
- certifications
- education
- achievements
- portfolio links

Then generate:

- tailored CVs
- cover letters
- application texts
- recruiter messages

from that structured profile.

---

# Document Rendering Architecture

Recommended rendering pipeline:

```text
Structured Career Data
→ Angular Components
→ HTML/CSS Templates
→ Playwright/Puppeteer PDF Rendering
→ Downloadable PDF
```

Avoid using Canva/Word automation as core infrastructure.

---

# ATS-Friendly Template Strategy

Support:

- ATS-safe templates
- modern two-column templates
- executive templates
- startup/creative templates

Modern two-column layouts can still be ATS-safe if semantic structure is preserved.

---

# Language Handling

The system should support:

- Danish document generation
- English document generation
- bilingual workflows

Default language should be inferred from:

- job listing language
- company locale
- posting metadata

Users should always be able to override language.

---

# Skill Taxonomy Layer

A normalized technical skill relationship layer is strongly recommended.

Example:

```text
Java
→ Spring
→ Spring Boot
→ Hibernate
```

Purpose:

- semantic matching
- inferred skill relationships
- transferability scoring
- missing-skill analysis

---

# Recommendation Explainability

The matching engine should explain why jobs are recommended.

Example:

- strong Java overlap
- backend-heavy role
- Kubernetes alignment
- remote preference matched

---

# Recommendation Feedback Loops

Users should be able to:

- like/dislike recommendations
- hide recommendation categories
- request more jobs like a posting
- request fewer jobs like a posting

---

# Projects As First-Class Entities

Projects should be treated as first-class entities in the domain model.

This is especially important for:

- software engineers
- self-taught developers
- startup-oriented candidates
- freelancers

Projects should function as contextual grounding for:

- AI-generated CVs
- cover letters
- application texts
- recruiter messages
- interview preparation

---

# AI Provider Abstraction

```text
AIProviderPort
 ├── OpenAIAdapter
 ├── AnthropicAdapter
 ├── LocalLLMAdapter
```

Purpose:

- avoid vendor lock-in
- simplify experimentation
- support fallback models

---

# Search Engine

## Phase 1
Typesense

## Phase 2+
OpenSearch or Elasticsearch

---

# Core Persistence Recommendation

Use PostgreSQL.

Reasons:

- relational structure fits domain well
- strong filtering support
- analytics support
- vector support via pgvector
- transactional consistency
- better normalization

Avoid Firestore initially.

