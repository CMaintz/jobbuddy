# Revised Specification Addendum (v2)

# Purpose

This document consolidates newer architectural decisions, product refinements, and feature discoveries that supersede or extend the earlier specifications.

This document should be treated as:

> The canonical addendum to the original specification.

It exists to:

- avoid rewriting earlier specs entirely
- preserve compatibility with existing generated code
- document evolved architectural decisions
- clarify refined product direction
- provide updated guidance for future development and AI coding agents

---

# 1. Canonical Career Profile (“Master / Brutto CV”)

# Previous Assumption

The earlier specs implied users would primarily manage separate CV documents.

---

# Revised Direction

The platform should instead revolve around:

```text
A structured master career profile.
```

Users maintain ONE comprehensive source-of-truth profile containing:

- all experience
- all projects
- all skills
- all certifications
- all education
- all achievements
- portfolio links
- technical competencies

The system then generates:

- job-specific CVs
- cover letters
- application texts
- recruiter messages

from this structured profile.

---

# Recommended Flow

```text
Master Profile
+ Job Listing
+ User Strategy Prompt
+ AI Tailoring Layer
→ Tailored CV
→ Cover Letter
→ Application Text
```

---

# Important Architectural Clarification

The platform should primarily store:

```text
structured career data
```

rather than:

```text
standalone manually edited documents
```

Documents become generated outputs.

This is a major architectural and product distinction.

---

# 2. CV Import & Parsing Workflow

# Supported Input Methods

## Option A — Existing CV Upload

Supported formats:

- PDF
- DOCX

---

## Option B — Guided Onboarding

Users manually enter:

- skills
- projects
- experience
- education
- achievements

through structured forms.

---

## Option C — External Import (Future)

Potential integrations:

- LinkedIn
- Jobindex
- GitHub

Likely implementation methods:

- PDF import
- browser-extension extraction
- scraping-based enrichment

Official APIs may be restrictive.

---

# Recommended Parsing Pipeline

```text
CV Upload
→ Text Extraction
→ AI Parsing
→ Structured Normalization
→ Editable Master Profile
```

---

# 3. Software Developer CV Optimization

The platform should strongly optimize for software engineering CVs initially.

This is now considered a core product assumption.

---

# Recommended Structured Skill Categories

## Languages

- Java
- TypeScript
- Python

---

## Backend

- Spring Boot
- Hibernate
- Node.js

---

## Frontend

- Angular
- React
- RxJS

---

## Cloud & DevOps

- Docker
- Kubernetes
- AWS
- CI/CD

---

## Databases

- PostgreSQL
- MongoDB
- MySQL

---

# Project Support

Projects are considered a first-class part of the master career profile.

This is especially important for:

- software engineers
- self-taught developers
- freelancers
- startup-oriented candidates
- candidates with limited formal work experience

Projects should also function as contextual grounding for AI-generated:

- CVs
- cover letters
- application texts
- recruiter outreach
- interview preparation

The AI should be able to reference projects when relevant to the target role.

Example:

A Kubernetes-heavy backend role could automatically emphasize:

- distributed systems projects
- container orchestration work
- cloud infrastructure projects

while suppressing less relevant projects.

---

Projects should support:

- descriptions
- technologies
- architecture details
- GitHub links
- screenshots optionally
- measurable outcomes
- business impact

---

# Technical Experience Metadata

Potential fields:

- years of experience
- proficiency
- production experience
- recent usage

---

# 4. Document Types Clarification

Cover letters and application texts are NOT the same thing.

This distinction should exist in the domain model.

---

# Recommended Document Types

```text
GeneratedDocumentType
- CV
- CoverLetter
- ApplicationText
- RecruiterMessage
- FollowUpMessage
```

---

# Clarifications

## Cover Letter

Typically:

- longer
- attached document
- narrative-driven
- company-focused

---

## Application Text

Typically:

- shorter
- pasted into forms
- more concise
- custom-question oriented

---

# 5. Language Handling

The platform should support:

- Danish generation
- English generation
- bilingual workflows

---

# Default Behavior

Infer language from:

- job listing language
- company locale
- posting metadata

Users should always be able to override language.

---

# 6. ATS-Friendly Two-Column Templates

# Previous Concern

Earlier discussion assumed two-column layouts might inherently be problematic for ATS systems.

---

# Revised Clarification

Modern ATS systems can generally handle carefully designed two-column layouts.

The real problem is:

- poor semantic structure
- excessive decoration
- image-based text
- broken hierarchy

—not the existence of two columns themselves.

---

# Recommended Template Categories

## ATS-Safe Minimal

Simple and parser-friendly.

---

## Modern Two-Column

Professional and visually balanced.

---

## Executive

Enterprise-focused styling.

---

## Startup / Creative

More expressive and personality-oriented.

---

# ATS Requirements

Templates should:

- use semantic structure
- avoid image-based text
- avoid excessive graphics
- maintain readable hierarchy
- use proper headings

---

# 7. Recommended Rendering Architecture

# Strong Recommendation

Use:

```text
HTML/CSS-driven document rendering
```

instead of:

- low-level PDF coordinate systems
- Word automation
- Canva embedding as core infrastructure

---

# Recommended Pipeline

```text
Structured CV Data
→ Angular Components
→ HTML/CSS Templates
→ Playwright/Puppeteer PDF Rendering
→ Downloadable PDF
```

---

# Advantages

- reusable templates
- maintainable layouts
- easier theming
- easier ATS optimization
- pixel-consistent exports
- reusable frontend rendering

---

# 8. Recommended Libraries & Tooling

# Rich Text Editing

Recommended:

- TipTap
- Lexical
- ProseMirror

---

# PDF Rendering

Recommended:

- Puppeteer
- Playwright

---

# Future Drag-and-Drop Support

Potential:

- Angular CDK Drag/Drop

---

# 9. Skill Taxonomy Layer

A normalized skill relationship layer is now strongly recommended.

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

---

# Purpose

Enable:

- semantic matching
- inferred skill relationships
- transferability scoring
- stronger recommendations
- missing-skill analysis

---

# 10. Explainable Recommendations

The matching engine should explain WHY jobs are recommended.

Example:

- strong Java overlap
- Kubernetes experience aligned
- backend-heavy role
- remote preference matched

Purpose:

Increase user trust and transparency.

---

# 11. User Feedback Loops

The platform should support:

- liking/disliking recommendations
- hiding recommendation types
- requesting more jobs like a posting
- requesting fewer jobs like a posting

Purpose:

Improve personalization quality.

---

# 12. Writing Style Memory

The system should learn:

- phrasing patterns
- vocabulary preferences
- sentence structure
- successful application styles

Goal:

Generate applications that genuinely sound like the user.

---

# 13. Additional Workflow Features

Potential future support:

## Interview Tracking

- interview notes
- coding challenge tracking
- recruiter impressions
- AI interview preparation

---

## Recruiter CRM

- recruiter tracking
- networking notes
- referrals
- communication history

---

## Browser Extension

- save jobs instantly
- import jobs
- autofill applications
- inline AI analysis

---

# 14. Strategic Clarification

The product direction has evolved from:

```text
AI-generated applications
```

toward:

```text
A complete AI-assisted career operating system.
```

This distinction should guide future architectural and UX decisions.

