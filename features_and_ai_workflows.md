# Features & AI Workflows

# Job Sources

Initial sources:

- LinkedIn Jobs
- Jobindex
- IT Jobbank
- The Hub
- Ofir
- WorkinDenmark
- company career pages

---

# Job Ingestion Pipeline

```text
Crawler
→ Raw Data
→ Extraction
→ Normalization
→ Deduplication
→ AI Enrichment
→ Search Index
→ Matching Engine
```

---

# Search & Filtering

## Standard Filters

- title
- company
- location
- salary
- technologies
- languages
- remote/hybrid
- seniority

---

## Advanced Preferences

### Positive

- startups
- backend-heavy
- remote-first
- product companies
- modern tech stacks

### Negative

- consulting firms
- recruiter agencies
- specific companies
- outdated technologies

---

# Matching Engine

# Recommendation Explainability

The system should explain WHY jobs are recommended.

Example:

- strong Java overlap
- backend-heavy role
- Kubernetes alignment
- remote preference matched

---

# Recommendation Feedback Loops

Users should be able to:

- like/dislike recommendations
- request more jobs like a posting
- request fewer jobs like a posting
- hide recommendation categories

---

# Skill Taxonomy Layer

Maintain normalized technical skill relationships.

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
- missing-skill analysis
- transferability scoring

---

# Matching Engine

## Layer 1 — Hard Constraints

- location
- salary
- work authorization
- remote preferences

---

## Layer 2 — Semantic Matching

Compare:

- CVs
- user profiles
- job descriptions
- saved jobs
- previous applications

using embeddings.

---

## Layer 3 — Behavioral Learning

Learn from:

- saved jobs
- ignored jobs
- applied jobs
- interview conversions

---

# Structured Career Profile Workflow

The platform should revolve around a structured “Master / Brutto CV” profile.

Users maintain:

- experience
- technical skills
- projects
- certifications
- education
- achievements
- portfolio links

The AI should generate tailored outputs from this structured profile.

---

# Project-Centric AI Grounding

Projects should be treated as first-class profile entities.

Projects should support:

- descriptions
- technologies
- architecture details
- GitHub links
- measurable outcomes
- business impact

The AI should emphasize the most relevant projects per role.

---

# AI CV Features

- ATS optimization
- keyword analysis
- quantified achievement suggestions
- readability improvements
- role-specific tailoring

---

# AI Application Generation

## Inputs

- selected job
- selected CV
- prompt template
- writing style memory
- custom instructions

---

## Outputs

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

Users should be able to:

- create prompt templates
- save templates
- version templates
- categorize templates
- duplicate templates
- use long-form instructions

---

# Prompt Composition

```text
System Prompt
+ User Strategy Prompt
+ CV Context
+ Job Description
+ Writing Style Memory
+ Output Constraints
= Final Prompt
```

---

# Writing Style Memory

Store:

- previous applications
- successful applications
- tone preferences
- vocabulary patterns
- sentence structure

Goal:

Generate applications that sound authentic.

---

# Application Tracking

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

# Dashboard Widgets

- recommended jobs
- pending applications
- upcoming interviews
- AI recommendations
- analytics
- weekly insights

---

# Future Features

## Chrome Extension

- save jobs
- autofill applications
- inline AI analysis

---

## Email Integration

- parse recruiter emails
- detect interview invites
- auto-update application states

---

## Autonomous Agents

Potential future capabilities:

- automatic application generation
- semi-autonomous applying
- optimization loops
- autonomous prioritization

