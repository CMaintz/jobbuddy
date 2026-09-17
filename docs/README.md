# AutoApplicant documentation

Project docs, organised by area. (Agent guidance lives in [`../CLAUDE.md`](../CLAUDE.md); the project intro in [`../README.md`](../README.md).)

## Specs — `specs/`
- [revised_spec_addendum_v_2.md](specs/revised_spec_addendum_v_2.md) — **canonical** spec addendum: master/brutto CV, document types, language handling, ATS rendering.
- [cv_personalization_and_document_system_spec.md](specs/cv_personalization_and_document_system_spec.md) — CV personalisation & document-system spec.

## Architecture — `architecture/`
- [ARCHITECTURE_NOTES.md](architecture/ARCHITECTURE_NOTES.md) — living inventory: implemented features, resolved violations, tech debt.
- [system_architecture.md](architecture/system_architecture.md) — hexagonal layers & design principles (background).

## Guides — `guides/`
- [SETUP.md](guides/SETUP.md) — install, env config, deployment.
- [COMMANDS.md](guides/COMMANDS.md) — CLI / crawl / enrichment commands.
- [testing_strategy.md](guides/testing_strategy.md) — testing pyramid, coverage targets, quality gates.
- [db.md](guides/db.md) — PostgreSQL cheatsheet.

## Product & domain — `product/`
- [product_strategy.md](product/product_strategy.md) — vision, market, differentiators, roadmap.
- [features_and_ai_workflows.md](product/features_and_ai_workflows.md) — feature inventory + AI workflows.
- [danish_market_playbook.md](product/danish_market_playbook.md) — Danish recruitment conventions (drives generation prompts).
- [skill_elicitation_design.md](product/skill_elicitation_design.md) — skill-evidence capture design.

## Archive — `archive/`
Superseded or point-in-time docs, kept for history — **not** current guidance:
- `original_spec.md`, `llm_agent_spec.md` — earlier specs, replaced by `specs/revised_spec_addendum_v_2.md`.
- `PLAN.md`, `cv_letter_quality_plan.md` — delivered work plans (marked done).
- `AUDIT_REPORT.md` — 2026-05-22 codebase/security audit snapshot.
