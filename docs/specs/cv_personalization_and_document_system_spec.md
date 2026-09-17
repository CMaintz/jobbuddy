# CV, Personalization & Document System Specification

# Purpose

Define:

- CV architecture
- master profile strategy
- document generation workflows
- personalization systems
- language handling
- ATS considerations
- rendering architecture
- software developer CV optimization
- future personalization features

This document focuses specifically on:

> Turning the platform into a complete AI-assisted job application workspace.

---

# 1. Canonical Career Profile (Master / “Brutto” CV)

# Core Concept

Users should maintain a structured master career profile containing ALL relevant information.

This becomes the source-of-truth profile.

The system should NOT rely primarily on manually edited standalone CV documents.

---

# Recommended Architecture

```text
Master Career Profile
+ Job Listing
+ User Strategy Prompt
+ AI Tailoring Layer
→ Tailored CV
→ Cover Letter
→ Application Text
```

---

# Master Profile Structure

## Personal Information

- name
- title
- email
- phone
- location
- LinkedIn
- GitHub
- portfolio links
- headshot/photo

---

## Professional Summary

Support:

- multiple summary variants
- AI-generated summaries
- role-specific summaries

---

## Technical Skills

Structured by category.

Example:

### Languages

- Java
- TypeScript
- Python

### Backend

- Spring Boot
- Hibernate
- Node.js

### Frontend

- Angular
- React
- RxJS

### Cloud & DevOps

- Docker
- Kubernetes
- AWS
- CI/CD

### Databases

- PostgreSQL
- MySQL
- MongoDB

---

## Work Experience

Each experience should support:

- company
- title
- dates
- responsibilities
- measurable achievements
- technologies used
- business impact
- role type

---

## Projects

Very important for software engineers.

Each project should support:

- title
- description
- architecture details
- technologies used
- GitHub links
- screenshots optionally
- measurable outcomes
- personal/team distinction

---

## Education

Support:

- degrees
- certifications
- online learning
- bootcamps

---

## Languages

Support:

- spoken proficiency
- written proficiency

---

## Additional Sections

Optional:

- publications
- talks
- volunteering
- patents
- awards
- leadership

---

# 2. CV Import & Parsing

# Recommended Input Methods

Users should be able to:

## Option A
Upload existing CVs.

Supported formats:

- PDF
- DOCX

---

## Option B
Complete a guided onboarding flow.

The system asks for:

- experience
- skills
- projects
- education
- achievements

---

## Option C (Future)
Import from external platforms.

Potential sources:

- LinkedIn
- Jobindex
- GitHub

Likely implementation:

- PDF import
- browser extension extraction
- scraping-based enrichment

Official APIs may be restrictive.

---

# Recommended Parsing Flow

```text
CV Upload
→ Text Extraction
→ AI Parsing
→ Structured Normalization
→ Editable Master Profile
```

---

# 3. Job-Specific CV Tailoring

# Core Goal

Generate “angled” / role-specific CVs automatically.

---

# Tailoring Inputs

- master profile
- selected job listing
- selected prompt strategy
- preferred tone
- language preference
- ATS optimization settings

---

# Tailoring Behavior

The AI should:

- prioritize relevant experience
- reorder relevant skills
- emphasize matching technologies
- rewrite summaries
- emphasize business impact
- optimize ATS keywords

Without:

- hallucinating experience
- inventing technologies
- fabricating responsibilities

---

# Example

A backend-heavy Java role should prioritize:

- Spring Boot
- PostgreSQL
- distributed systems
- Kubernetes
- backend architecture

while reducing emphasis on:

- frontend-heavy experience
- unrelated technologies

---

# 4. Document Types

# Important Distinction

These are separate concepts and should be modeled separately.

---

## CV / Resume

Structured professional overview.

---

## Cover Letter

Formal attached document.

Usually:

- longer
- more narrative
- more company-focused

---

## Application Text

Shorter text pasted into application systems.

Often:

- more concise
- more direct
- custom-question oriented

---

## Recruiter Message

Short networking/recruiter outreach.

---

## Follow-Up Message

Post-interview or post-application communication.

---

# Recommended Model

```text
GeneratedDocumentType
- CV
- CoverLetter
- ApplicationText
- RecruiterMessage
- FollowUpMessage
```

---

# 5. Language Handling

# Supported Languages

Initially:

- Danish
- English

---

# Automatic Language Detection

Default behavior:

Infer preferred language from:

- job listing language
- company locale
- posting metadata

---

# User Overrides

Users should always be able to force:

- Danish output
- English output
- bilingual workflows

---

# 6. ATS-Friendly Template System

# Important Clarification

Two-column templates CAN still be ATS-friendly.

Modern ATS systems are significantly better than older systems.

The issue is poor structure — not two columns themselves.

---

# ATS-Safe Requirements

Templates should:

- use semantic HTML structure
- avoid text embedded in images
- avoid excessive decorative graphics
- use proper headings
- avoid complex nested tables
- maintain readable hierarchy

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

More personality-oriented.

---

# 7. Rendering Architecture

# Strong Recommendation

Use HTML/CSS rendering.

Avoid low-level PDF drawing systems initially.

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
- easy theming
- maintainable layouts
- modern styling
- easier ATS-safe rendering
- pixel-consistent PDFs

---

# 8. Editor & Rendering Libraries

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

# Drag & Drop (Future)

Potential:

- Angular CDK Drag/Drop

---

# 9. Personalization & Learning Systems

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

- semantic matching
- inferred skill relationships
- missing skill analysis
- stronger recommendations

---

# AI Explainability

Recommendations should explain WHY jobs match.

Example:

- strong Java overlap
- backend-heavy role
- Kubernetes experience aligned
- remote preference aligned

---

# User Feedback Loops

Users should be able to:

- like/dislike recommendations
- hide recommendation categories
- request more jobs like a posting
- request fewer jobs like a posting

---

# Writing Style Memory

The system should learn:

- phrasing patterns
- tone preferences
- sentence structures
- successful applications

Goal:

Generate applications that genuinely sound like the user.

---

# 10. Future Features

## Interview Tracking

Potential support:

- interview notes
- coding challenge tracking
- recruiter impressions
- AI interview preparation

---

## Recruiter CRM

Potential support:

- recruiter tracking
- networking notes
- referrals
- communication history

---

## Browser Extension

Potential support:

- save jobs instantly
- autofill applications
- import LinkedIn jobs
- inline AI analysis

---

# Final Recommendation

The platform should evolve toward:

> A complete AI-assisted career operating system.

Not merely:

> A job board with AI-generated cover letters.



---

# Documentation Cleanup & Synchronization

# Deprecated / Secondary Documents

The following documents should now be treated as:

- secondary references
- migration references
- historical context

rather than primary working specifications:

```text
Danish AI Job Platform — Product & System Specification
Job Platform Developer Experience Testing And Document Generation
```

Reason:

Their contents were later split into more focused canonical documents.

---

# Current Recommended Canonical Workflow

## Product Direction

Use:

```text
Job Platform Product Strategy
```

---

## Architecture & Infrastructure

Use:

```text
Job Platform System Architecture
```

---

## Features & User Workflows

Use:

```text
Job Platform Features And Ai Workflows
```

---

## CV / Personalization / Documents

Use:

```text
Job Platform Cv Personalization And Document System Spec
```

This is now the canonical source for:

- master/brutto CV architecture
- structured career profiles
- projects
- document rendering
- ATS-safe templates
- language handling
- personalization
- recommendation explainability
- writing-style memory

---

## Testing / QA / Reliability

Use:

```text
Job Platform Testing Strategy
```

The earlier “Developer Experience” document should now be considered superseded by this focused testing document.

---

## Coding-Agent Guidance

Use:

```text
Danish Job Platform Llm Agent Spec
```

This document SHOULD eventually stay condensed and implementation-oriented.

It should not become a giant product-spec clone.

---

## Architectural Refinements / Evolved Decisions

Use:

```text
Job Platform Revised Spec Addendum V2
```

This document exists specifically to:

- preserve compatibility with earlier generated code
- clarify evolved architecture decisions
- avoid rewriting older specifications entirely



---

# Synchronization Update — New Canonical Decisions

# Additional Domain Modules

```text
profiles
cv
projects
personalization
recommendations
```

---

# Canonical Career Profile (Master / Brutto CV)

The platform should revolve around a structured master career profile rather than standalone manually edited CV documents.

The system should store:

- structured experience
- technical skills
- projects
- certifications
- education
- achievements
- portfolio links

Then generate:

- tailored CVs
- cover letters
- application texts
- recruiter messages

from this structured profile.

---

# Projects As First-Class Entities

Projects are considered first-class profile entities.

This is especially important for:

- software engineers
- startup-oriented candidates
- self-taught developers
- freelancers

Projects should function as contextual grounding for:

- CV generation
- cover letters
- application texts
- recruiter outreach
- interview preparation

Projects should support:

- descriptions
- technologies
- architecture details
- GitHub links
- business impact
- measurable outcomes

---

# Document Type Separation

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

# ATS-Friendly Template Strategy

The platform should support:

- ATS-safe templates
- modern two-column templates
- executive templates
- startup/creative templates

Modern two-column templates can still be ATS-friendly if semantic structure is preserved.

---

# Recommended Rendering Architecture

Recommended pipeline:

```text
Structured CV Data
→ Angular Components
→ HTML/CSS Templates
→ Playwright/Puppeteer PDF Rendering
→ Downloadable PDF
```

Avoid relying on Canva or Word automation as core infrastructure.

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
- request more jobs like a posting
- request fewer jobs like a posting
- hide recommendation categories

---

# Skill Taxonomy Layer

The platform should maintain normalized technical skill relationships.

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
- stronger recommendations

---

# Writing Style Memory

The system should learn:

- phrasing patterns
- tone preferences
- successful applications
- sentence structures

Goal:

Generate applications that genuinely sound like the user.

