# Danish AI Job Platform — LLM Coding Agent Specification

# Project Goal

Build a Danish-focused AI-powered job aggregation and application platform.

The platform should:

- Aggregate Danish job listings from multiple sources
- Normalize and enrich listings
- Provide advanced filtering and semantic relevance ranking
- Assist users with CV refinement and application generation
- Track applications and job-search workflows
- Support reusable prompting strategies
- Eventually support semi-autonomous and autonomous job application agents

Primary market:
- Danish IT/software job seekers initially

---

# Core Functional Domains

```text
jobs
crawler
matching
search
applications
users
profiles
cv
projects
documents
prompts
ai
analytics
notifications
companies
personalization
recommendations
```

---

# High-Level Architecture

Use Hexagonal Architecture (Ports & Adapters).

## Architectural Layers

```text
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

# Tech Stack

## Backend

- Java 21
- Spring Boot
- Maven or Gradle
- Spring Data JPA
- Hibernate
- PostgreSQL
- pgvector

Optional:
- Spring WebFlux
- Redis
- Quartz Scheduler

---

## Frontend

- Angular
- TypeScript
- Angular Material or Tailwind
- RxJS

---

## Infrastructure

Initial recommendation:

- Supabase PostgreSQL
- Docker
- Fly.io / Railway / Render

---

# Job Sources

Initial required sources:

- LinkedIn Jobs
- Jobindex
- IT Jobbank
- The Hub
- Ofir
- WorkinDenmark
- Company career pages

---

# Source Connector Architecture

Each source must be isolated.

Example:

```text
JobSourceConnector
 ├── LinkedInConnector
 ├── JobindexConnector
 ├── TheHubConnector
 └── CompanyCareerConnector
```

Requirements:

- independent scraping logic
- retry support
- anti-fragile extraction
- source isolation
- testability

---

# Job Ingestion Pipeline

```text
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

# Core Database Entities

## Users

```text
users
profiles
preferences
```

---

## Jobs

```text
jobs
job_sources
job_embeddings
job_tags
```

---

## Companies

```text
companies
company_metadata
```

---

## User Workflow

```text
applications
saved_jobs
ignored_jobs
notes
```

---

## AI & Documents

```text
prompt_templates
cv_versions
generated_documents
writing_profiles
```

---

## Analytics

```text
application_metrics
interview_metrics
response_metrics
```

---

# Core Job Schema

```ts
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

# Search & Filtering Requirements

## Standard Filters

- title
- company
- location
- remote/hybrid/on-site
- salary
- technologies
- languages
- seniority
- employment type

---

## Advanced Filters

### Positive Preferences

- startups
- backend-heavy
- remote-first
- product companies
- international companies

### Negative Preferences

- consulting firms
- recruiter agencies
- specific companies
- outdated tech stacks

---

## Semantic Search

Examples:

- “backend-heavy Java jobs”
- “modern cloud-native companies”
- “low bureaucracy startups”

---

# Matching Engine

Use multi-layer scoring.

## Layer 1 — Hard Constraints

- location
- salary
- work authorization
- remote preference
- employment type

---

## Layer 2 — Semantic Matching

Embedding similarity between:

- user profile
- CV
- job descriptions
- saved jobs
- previous applications

---

## Layer 3 — Behavioral Learning

Learn from:

- saved jobs
- ignored jobs
- applied jobs
- interview conversions

---

## Match Display

Internal:

```text
0-100 score
```

User-facing:

- Excellent Match
- Strong Match
- Moderate Match
- Weak Match

---

# Structured Career Profile Architecture

Use a canonical structured “Master / Brutto CV” profile.

Store:

- experience
- technical skills
- projects
- certifications
- education
- achievements
- portfolio links
- headshots/photos

Generate from this profile:

- tailored CVs
- cover letters
- application texts
- recruiter messages

Projects are first-class entities and should support:

- descriptions
- technologies
- architecture details
- GitHub links
- business impact
- measurable outcomes

Projects should be usable as AI grounding/context.

---

# AI Features

# CV Analysis

Features:

- ATS optimization
- keyword analysis
- quantified achievement suggestions
- readability improvements
- role-specific tailoring

---

# Application Generation

Inputs:

- selected job
- selected CV
- prompt template
- user profile
- writing style memory
- custom instructions

Outputs:

- editable CV
- editable cover letter
- editable application text
- recruiter outreach message
- follow-up messages

---

# Document Types

The system should model:

```text
CV
CoverLetter
ApplicationText
RecruiterMessage
FollowUpMessage
```

Cover letters and application texts are separate concepts and should be generated differently.

---

# Language Handling

The platform should support:

- Danish generation
- English generation
- bilingual workflows

Default language should be inferred from:

- job listing language
- company locale
- posting metadata

Users should always be able to override language.

---

# Prompt Template System

Users must be able to:

- create templates
- save templates
- version templates
- duplicate templates
- categorize templates
- use long-form instructions

---

# Prompt Composition Model

```text
System Prompt
+ User Prompt Template
+ CV Context
+ Job Description
+ Writing Style Memory
+ Output Constraints
= Final Prompt
```

---

# Writing Style Memory

Store and analyze:

- previous applications
- accepted applications
- user phrasing patterns
- vocabulary preferences
- tone preferences

Goal:

Generate applications that sound like the user.

---

# Application Tracking

## Pipeline

```text
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

---

# Recommendation Explainability & Personalization

The matching engine should:

- explain recommendation reasoning
- support like/dislike feedback
- support “more like this” / “less like this” flows
- maintain normalized skill relationships
- learn writing style preferences

Example skill taxonomy:

```text
Java
→ Spring
→ Spring Boot
→ Hibernate
```

---

# Document Rendering

Support:

- ATS-safe templates
- modern two-column templates
- executive templates
- startup/creative templates

Recommended rendering pipeline:

```text
Structured CV Data
→ Angular Components
→ HTML/CSS Templates
→ Playwright/Puppeteer PDF Rendering
```

---

# Dashboard Requirements

Widgets:

- recommended jobs
- saved jobs
- pending applications
- upcoming interviews
- AI recommendations
- analytics
- weekly insights

---

# Search Engine Recommendation

## Phase 1

Typesense

## Phase 2+

OpenSearch or Elasticsearch

---

# AI Provider Abstraction

Use provider ports.

```text
AIProviderPort
 ├── OpenAIAdapter
 ├── AnthropicAdapter
 ├── LocalLLMAdapter
```

Goal:

Avoid vendor lock-in.

---

# Recommended APIs

## Internal APIs

```text
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

# Authentication

Initial recommendation:

- JWT auth
- email/password
- Google login
- LinkedIn OAuth later

---

# Key Non-Functional Requirements

## Performance

- fast search responses
- async crawling
- async AI generation
- background job queues

---

## Scalability

- independent crawler scaling
- AI queue isolation
- modular adapters
- provider abstraction

---

## Reliability

- retryable scraping
- source fault isolation
- idempotent ingestion
- deduplication safety

---

# Important Product Principles

## 1. Data Quality First

High-quality normalization and matching are core differentiators.

---

## 2. Human Oversight Initially

Applications should remain user-approved initially.

---

## 3. Prompting Is a Product Feature

Prompt templates and reusable strategies are core functionality.

---

## 4. Optimize for Iteration Speed

Scraping and AI integrations will evolve constantly.

Architecture should support rapid experimentation.

---

# Future Features

## Chrome Extension

- save jobs
- import jobs
- autofill applications
- inline AI analysis

---

## Email Integration

- parse recruiter emails
- detect interview invites
- update application pipeline automatically

---

## Semi-Autonomous Agent

- generate applications automatically
- user approves submission

---

## Fully Autonomous Agent

Potential future capability:

- autonomous applications
- optimization loops
- application strategy learning
- response-rate optimization

