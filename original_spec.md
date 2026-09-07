# Danish AI Job Platform — LLM Coding Agent Specification

## Project Goal

Build a Danish-focused AI-powered job aggregation and application platform.

The platform should:

- Aggregate Danish job listings from multiple sources
- Normalize and enrich listings
- Provide advanced filtering and semantic relevance ranking
- Assist users with CV refinement and application generation
- Track applications and job-search workflows
- Support reusable prompting strategies
- Eventually support semi-autonomous and autonomous job application agents

**Primary market:** Danish IT/software job seekers initially

---

## Core Functional Domains

- `jobs`
- `crawler`
- `matching`
- `search`
- `applications`
- `users`
- `documents`
- `prompts`
- `ai`
- `analytics`
- `notifications`
- `companies`

---

## High-Level Architecture

Use **Hexagonal Architecture (Ports & Adapters)**.

### Architectural Layers

```
Frontend (Angular)
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

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Maven or Gradle
- Spring Data JPA
- Hibernate
- PostgreSQL
- pgvector

**Optional:**

- Spring WebFlux
- Redis
- Quartz Scheduler

### Frontend

- Angular
- TypeScript
- Angular Material or Tailwind
- RxJS

### Infrastructure

**Initial recommendation:**

- Supabase PostgreSQL (pgvector is supported natively in Supabase — no additional setup required)
- Docker
- Fly.io / Railway / Render

---

## Job Sources

**Initial required sources:**

- LinkedIn Jobs
- Jobindex
- Computerworld / IT Jobbank
- The Hub
- Ofir
- Jobnet
- WorkinDenmark
- Company career pages

### Source Connector Architecture

Each source must be isolated.

```
JobSourceConnector
 ├── LinkedInConnector
 ├── JobindexConnector
 ├── ComputerworldConnector
 ├── TheHubConnector
 └── CompanyCareerConnector
```

**Requirements:**

- Independent scraping logic
- Retry support
- Anti-fragile extraction
- Source isolation
- Testability

---

## Job Ingestion Pipeline

```
Crawler
→ Raw Source Data
→ Extraction
→ Normalization
→ Deduplication
→ AI Enrichment
→ Search Index
→ Matching Engine
```

---

## Core Database Entities

### Users

- `users`
- `profiles`
- `preferences`

### Jobs

- `jobs`
- `job_sources`
- `job_embeddings`
- `job_tags`

### Companies

- `companies`
- `company_metadata`

### User Workflow

- `applications`
- `saved_jobs`
- `ignored_jobs`
- `notes`

### AI & Documents

- `prompt_templates`
- `cv_versions`
- `generated_documents`
- `writing_profiles`

### Analytics

- `application_metrics`
- `interview_metrics`
- `response_metrics`

---

## Core Job Schema

```
Job {
  id
  source
  sourceJobId
  url

  title
  company
  descriptionRaw
  descriptionClean

  employmentType
  seniority
  remoteType

  location
  municipality
  region
  country

  salaryMin
  salaryMax
  currency

  technologies[]
  skills[]
  languages[]

  postedAt
  scrapedAt

  aiSummary
  aiTags[]
  aiSeniorityEstimate

  duplicateGroupId
}
```

---

## Search & Filtering Requirements

### Standard Filters

- title
- company
- location
- remote / hybrid / on-site
- salary
- technologies
- languages
- seniority
- employment type

### Advanced Filters

**Positive Preferences:**

- startups
- backend-heavy
- remote-first
- product companies
- international companies

**Negative Preferences:**

- consulting firms
- recruiter agencies
- specific companies
- outdated tech stacks

### Semantic Search

Natural language queries are converted to embeddings and scored against job description embeddings (see [Matching Engine — Layer 2](#layer-2--semantic-matching)). Results are combined with active standard filters and ranked by a weighted relevance score.

Examples:

- `"backend-heavy Java jobs"`
- `"modern cloud-native companies"`
- `"low bureaucracy startups"`

---

## Matching Engine

Use **multi-layer scoring**.

### Layer 1 — Hard Constraints

- location
- salary
- work authorization
- remote preference
- employment type

### Layer 2 — Semantic Matching

Embedding similarity between:

- user profile
- CV
- job descriptions
- saved jobs
- previous applications

### Layer 3 — Behavioral Learning

Learn from:

- saved jobs
- ignored jobs
- applied jobs
- interview conversions

### Match Display

**Internal:** 0–100 score

**User-facing:**

- Excellent Match
- Strong Match
- Moderate Match
- Weak Match

---

## AI Features

### CV Analysis

Features:

- ATS optimization
- Keyword analysis
- Quantified achievement suggestions
- Readability improvements
- Role-specific tailoring

### Application Generation

**Inputs:**

- Selected job
- Selected CV
- Prompt template
- User profile
- Writing style memory
- Custom instructions

**Outputs:**

- Editable cover letter
- Editable application text
- Recruiter outreach message

### Prompt Template System

Users must be able to:

- Create templates
- Save templates
- Version templates
- Duplicate templates
- Categorize templates
- Use long-form instructions

### Prompt Composition Model

```
System Prompt
+ User Prompt Template
+ CV Context
+ Job Description
+ Writing Style Memory
+ Output Constraints
= Final Prompt
```

### Writing Style Memory

Store and analyze:

- Previous applications
- Accepted applications
- User phrasing patterns
- Vocabulary preferences
- Tone preferences

**Goal:** Generate applications that sound like the user.

---

## Application Tracking

### Pipeline

```
Saved
→ Preparing
→ Applied
→ Recruiter Contact
→ Interview
→ Technical Test
→ Final Round
→ Offer
→ Rejected
→ Archived
```

### Dashboard Requirements

**Widgets:**

- Recommended jobs
- Saved jobs
- Pending applications
- Upcoming interviews
- AI recommendations
- Analytics
- Weekly insights

---

## Search Engine Recommendation

### Phase 1

- **Typesense** — recommended for initial development due to ease of setup, low operational overhead, and sufficient full-text + faceted search capabilities for early-stage scale.

### Phase 2+

- **OpenSearch or Elasticsearch** — migrate when any of the following thresholds are reached:
  - Job index exceeds ~1 million documents
  - Complex multi-field relevance tuning is required
  - Advanced semantic/hybrid search (dense + sparse vectors) is needed at scale
  - Typesense query performance or feature set becomes a bottleneck

---

## AI Provider Abstraction

Use **provider ports**.

```
AIProviderPort
 ├── OpenAIAdapter
 ├── AnthropicAdapter
 └── LocalLLMAdapter
```

**Goal:** Avoid vendor lock-in.

---

## Recommended APIs

### Internal APIs

```
/jobs
/jobs/search
/jobs/recommendations
/jobs/{id}

/applications
/applications/{id}

/cv
/prompts
/ai/generate
/ai/analyze

/dashboard
/analytics
```

---

## Authentication

**Initial recommendation:**

- JWT auth
- Email/password
- Google login
- LinkedIn OAuth later

---

## Key Non-Functional Requirements

### Performance

- Fast search responses
- Async crawling
- Async AI generation
- Background job queues

### Scalability

- Independent crawler scaling
- AI queue isolation
- Modular adapters
- Provider abstraction

### Reliability

- Retryable scraping
- Source fault isolation
- Idempotent ingestion
- Deduplication safety

---

## Important Product Principles

### 1. Data Quality First

High-quality normalization and matching are core differentiators.

### 2. Human Oversight Initially

Applications should remain user-approved initially.

### 3. Prompting Is a Product Feature

Prompt templates and reusable strategies are core functionality.

### 4. Optimize for Iteration Speed

Scraping and AI integrations will evolve constantly. Architecture should support rapid experimentation.

---

## Future Features

### Chrome Extension

- Save jobs
- Import jobs
- Autofill applications
- Inline AI analysis

### Email Integration

- Parse recruiter emails
- Detect interview invites
- Update application pipeline automatically

### Semi-Autonomous Agent

- Generate applications automatically
- User approves submission

### Fully Autonomous Agent

Potential future capability:

- Autonomous applications
- Optimization loops
- Application strategy learning
- Response-rate optimization
