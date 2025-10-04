## AGENTS.md

Purpose: Define conventions and best practices for human and AI agents collaborating on this repository. Aligns with the project constitution in `specs/001-e-commerce-platform/spec.md` and extends with operational guidance.

### Roles & Responsibilities

- Product Agent: Maintains `spec.md`, ensures requirements remain user-centric and testable.
- Architecture Agent: Owns architecture evolution, reliability patterns (outbox, idempotency, DLT), and cost-efficiency.
- Implementation Agent: Delivers code changes following TDD, keeps `tasks.md` current.
- QA Agent: Owns contract/integration tests; enforces failing-first tests and coverage gates.
- Operations Agent: Owns runbooks, metrics, alert thresholds, SLOs.

Agents may be humans or automated assistants; responsibilities are about decision rights, not job titles.

### Operating Principles

1. TDD always: write failing contract tests (Phase 3.2) before feature implementation.
2. Small, reversible changes: scope each edit to one concern; keep PRs under 400 lines diff when possible.
3. Spec is the source of truth: update `spec.md` and `tasks.md` in the same change that introduces architectural or behavioral shifts.
4. Reliability first: apply transactional outbox, idempotency keys, DLQ policies before scale-out.
5. Uniform errors: use RFC 7807 Problem Details across all APIs.
6. Traceability: propagate `X-Correlation-ID` from edge to events; include in logs and metrics.
7. Environments: prefer ephemeral, hermetic tests (Testcontainers); use profiles to toggle optional infra (e.g., Redis).

### Technical Conventions

- API Errors: `application/problem+json` with fields: `type`, `title`, `status`, `detail`, `instance`.
- Idempotency: Require `Idempotency-Key` on POST /checkout; store key → response for 24h; guard side-effects.
- Eventing: Use transactional outbox table with background publisher; events include `eventId`, `correlationId` headers; DLT topics exist for all consumers.
- Observability: Expose RED metrics; add business KPIs (orders_created_total, checkout_success_total). All logs are structured JSON.
- Security: Public catalog endpoints; manager endpoints require ROLE_MANAGER; JWT resource server in both services.

### Change Workflow

1. Open a task in `tasks.md` (new T0xx) capturing scope and acceptance.
2. Add/adjust tests to fail first (contract/integration).
3. Implement minimal code to pass tests.
4. Update docs: `spec.md`, READMEs, runbooks.
5. Add operational checks (health, metrics, alerts) if behavior or dependencies changed.

### Decision Records

- Significant decisions (patterns, cross-cutting concerns) should be captured as short ADRs under `docs/adrs/ADR-xxxx-title.md`.

### Guardrails for AI Agents

- Never make irreversible data migrations without a backup plan and a rollback task.
- Prefer editing existing files using minimal diffs; do not reformat unrelated code.
- Do not bypass tests; add them first, keep them deterministic.
- Cite code using file:line snippets when proposing changes in discussions.
- Maintain up-to-date `.env.example` files when environment variables are added/changed in application.yml files.

### References

- Problem Details for HTTP APIs (RFC 7807)
- Transactional Outbox pattern (a.k.a. Outbox/Inbox)
- Kafka DLT and retry/backoff patterns
- RED/USE monitoring patterns and Micrometer


