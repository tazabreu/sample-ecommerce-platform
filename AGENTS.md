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

8. Java Version: All modules target Java 21. Do not introduce JDK 22+ specific flags, APIs, or build args. Tooling and CI should use Java 21.

9. Test Annotation Processing: Avoid enabling annotation processors for test sources unless a test explicitly requires it. Prefer simple test doubles or hand-rolled mappers in test scope. If annotation processing causes instability, disable it for tests.

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

- Ask intelligent questions for important decision-making; the user is the human-in-the-loop and should always be clarifying questions before agents implement significant changes.
- Never make irreversible data migrations without a backup plan and a rollback task.
- Prefer editing existing files using minimal diffs; do not reformat unrelated code.
- Cite code using file:line snippets when proposing changes in discussions.
- Maintain up-to-date `.env.example` files when environment variables are added/changed in application.yml files.
- Commits must follow the repository's established style. Before committing, review recent commits to understand message structure, scope conventions, and maintain consistency.
- **Always update `specs/001-e-commerce-platform/tasks.md` when completing tasks**: Mark tasks as [X] complete with evidence (file paths, implementation details) IMMEDIATELY after implementation. Never let tasks.md drift from actual codebase state. This is critical for project tracking and handoffs between agents/developers.

### Commit Message Best Practices

When preparing to commit changes, follow this workflow:

1. **Review all changes comprehensively:**
   ```bash
   git status --short          # See all modified/new/deleted files
   git diff --stat            # Get line change statistics
   ```

2. **Create a visual change tree** to understand the scope:
   ```
   📦 Root Changes
   ├── 📝 Modified files       (+lines/-lines)  # Brief description
   ├── ✨ New files            (NEW)            # Purpose
   └── ❌ Deleted files        (-lines)         # Reason

   🛍️  Service A Changes
   ├── 📝 file1.java           (+10/-5)         # What changed
   └── ✨ file2.java           (NEW)            # New feature

   📋 Documentation
   └── 📝 tasks.md             (+50/-10)        # Updated tracking
   ```

3. **Study recent commit history** for style consistency:
   ```bash
   git log --oneline -10                    # See commit titles
   git log --format="%B" -1 <commit-hash>  # See full message
   ```

4. **Write commit message following repository conventions:**
   - Use emoji prefixes: ✨ feat, 🐛 fix, 📝 docs, ♻️ refactor, 🧪 test, 🔧 chore
   - Format: `✨ type(scope): brief summary in imperative mood`
   - Body: Explain WHAT changed, WHY it changed, and HOW it impacts the system
   - Include sections: Key Changes, Infrastructure, Testing, Documentation
   - Reference task IDs when applicable
   - End with validation evidence (tests passing, builds successful)

5. **Example commit message structure:**
   ```
   ✨ chore(post-migration): cleanup, documentation, and testing improvements

   Post-migration cleanup after successful JPA→JDBC migration with
   comprehensive documentation updates, operational tooling, and
   enhanced testing infrastructure.

   Key Changes:
   - Remove migration artifacts (5 files, -2,649 lines)
   - Add T092: unit test coverage task (>80% before integration)
   - Major README.md overhaul with architecture docs
   - Add operational runbook and validation scripts

   Infrastructure:
   - Add custom JDBC converters and callbacks
   - Enhance OutboxPublisher error handling
   - Clean up POM dependencies

   Testing:
   - All 48 tests passing (customer: 39, order: 9)
   - Docker images build successfully

   Documentation:
   - Update AGENTS.md with commit best practices
   - Add comprehensive task tracking in tasks.md
   ```

6. **Validate before committing:**
   ```bash
   mvn clean test              # Ensure tests pass
   docker-compose build        # Verify images build
   ```

### References

- Problem Details for HTTP APIs (RFC 7807)
- Transactional Outbox pattern (a.k.a. Outbox/Inbox)
- Kafka DLT and retry/backoff patterns
- RED/USE monitoring patterns and Micrometer
