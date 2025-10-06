# Documentation Index

## Purpose
- Provide a navigational map for engineers and operators onboarding to the e-commerce platform.
- Highlight the living documents that matter for day-to-day development, releases, and production support.

## Core References
- **Platform Spec**: `../specs/001-e-commerce-platform/spec.md` — functional and non-functional requirements.
- **Implementation Plan**: `../specs/001-e-commerce-platform/plan.md` — delivery sequencing and architectural rationale.
- **Data Model**: `../specs/001-e-commerce-platform/data-model.md` — canonical entities, relationships, and constraints.
- **API Contracts**: `../specs/001-e-commerce-platform/contracts/` — REST and event schemas (source of truth for integration).

## Operational Playbooks
- **Runbook**: `runbook.md` — metrics, alerts, incident workflows, and escalation ladders.
- **Quickstart Automation**: `../scripts/validate-quickstart.sh` — gold-path validation harness used post-deploy and in smoke checks.

## How To Contribute
1. Keep changes small and reversible; update specs alongside any behavior-altering code change.
2. For major decisions, raise an ADR under `docs/adrs/ADR-xxxx-title.md`.
3. Reflect every completed task in `../specs/001-e-commerce-platform/tasks.md` immediately to maintain traceability.
4. Ensure `.env.example` mirrors any configuration touchpoints you introduce.

## Contact
Implementation Agent owns this documentation set. Coordinate with the Architecture and QA agents for cross-cutting decisions.
