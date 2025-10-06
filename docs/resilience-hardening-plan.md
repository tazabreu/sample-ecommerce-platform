# Resilience Hardening Plan

## Context
- Goal: surface resilience gaps and outline minimal, high-leverage improvements without bloating services.
- Scope: customer-facing-service, order-management-service, shared Kafka/PostgreSQL/Redis dependencies.
- Inputs: current codebase, specs/001-e-commerce-platform, existing docs (runbook, testing guides).

## Current Posture Snapshot
- Transactional outbox + manual Kafka ACKs provide at-least-once delivery for `OrderCreated` → `PaymentCompleted` flow.
- Checkout idempotency keys guard duplicate POST submissions; processed_events table de-duplicates consumer replays.
- Resilience4j dependencies are present with circuit breaker, retry, and time limiter configs (but only the payment circuit breaker is wired today).
- DLQ topics exist in infrastructure scripts and runbook guidance; no automated publisher/consumer integration yet.
- Metrics and runbook include resilience-oriented alerts (outbox backlog, circuit breaker state) but rely on manual remediation.

## Gap Analysis & Lightweight Actions
| Gap | Risk | Minimal Fix | Effort |
| --- | --- | --- | --- |
| Resilience4j retry/time limiter beans unused in customer-facing-service outbox publisher | Kafka blips leave events stuck until manual intervention; circuit breaker metrics never fire | Wrap `OutboxPublisher.publishEvent` with `Retry` + `CircuitBreaker` decorators; only touch publisher class | S |
| Payment service only uses `@CircuitBreaker`; retries/timeouts not enforced | Upstream payment hiccups propagate failures immediately, no backoff | Annotate `MockPaymentService.processPayment` (and future implementations) with `@Retry`/`@TimeLimiter` using existing configs | XS |
| Kafka listeners rethrow on failure without DLQ handoff | Poison pill messages cause infinite redeliveries, manual ACK required | Configure `SeekToCurrentErrorHandler` + `DeadLetterPublishingRecoverer` on listener factories targeting `*.dlq` topics | M |
| Outbox failed rows never resurfaced | After 5 retries entries remain FAILED with no alert hook | Add scheduled job to retry `FAILED` rows or emit alert metric (`outbox_failed_total`); reuse existing repository | S |
| Idempotency cache cleanup manual | Table grows until manual cleanup, risk of stale responses | Schedule nightly `IdempotencyService.cleanupExpiredRecords()` run; metric on deletions | XS |
| Chaos/latency testing absent | Resilience changes unvalidated, risk of regressions | Extend Testcontainers suite with targeted fault injections (see below) before prod toggles | M |

## Resilience4j Usage Guidance
- **Keep the library**: already on the classpath, integrates cleanly with Spring Boot 3 actuator metrics. Focus on selective usage to avoid clutter.
- **Targeted decorators**: use `resilience4j-spring-boot3` annotations (`@Retry`, `@TimeLimiter`) or programmatic `Decorators.ofSupplier` only around external boundaries (Kafka send, payment HTTP client, Redis cache warmers).
- **Configuration hygiene**: current YAML instances (`paymentService`, `kafkaPublisher`, `catalogQuery`) are sensible defaults. Review thresholds per environment; expose them via `application-*.yml` overrides rather than hard-coding in beans.
- **Metrics**: ensure `/actuator/metrics/resilience4j_*` is scraped (Prometheus job already planned). Alert on prolonged OPEN states instead of raw failure counts to keep noise low.

## Test & Chaos Validation Strategy
- **Integration Tests (failing-first)**
  - `T058` / checkout flow: extend to assert idempotent retry returns cached response and outbox entry transitions to PUBLISHED.
  - New test: simulate Kafka broker outage using Testcontainers pause → assert outbox retry/backoff kicks in and events eventually publish once broker resumes.
  - New test: payment timeout via configurable mock delay > timeout → expect retry attempts, breaker OPEN after threshold, PaymentCompleted event with failure status.
  - New test (order service): inject malformed message and verify DeadLetterPublishingRecoverer routes to `orders.created.dlq` with headers intact.
- **Chaos Experiments (local/dev)**
  - Use Testcontainers + Toxiproxy (or Redpanda `rpk cluster health`) to add latency/packet loss to Kafka; observe resilience4j metrics and ensure alerts fire per runbook.
  - Scripted `docker-compose kill redis` during cart ops to confirm graceful degradation (cart miss fallback) and metrics/alerts capture outage.
  - Periodic `kubectl rollout restart` style chaos in future environments: verify idempotent consumers prevent duplicate side effects.
- **Observability Hooks**
  - Add synthetic checks exporting gauge for `order_created_outbox_failed_total` and `idempotency_cleanup_deleted_total` to confirm background jobs run.
  - Capture resilience events in structured logs with `event="resilience"` tag for easier filtering during chaos drills.

## Implementation Roadmap (Low → High Effort)
1. Wire payment + outbox paths to existing Resilience4j configs (`@Retry`, `@TimeLimiter`, decorators).
2. Schedule cleanup/retry jobs (`IdempotencyService`, outbox failed requeue) and expose metrics.
3. Enhance Kafka listener factories with DLQ publishing and backoff semantics.
4. Add/extend integration tests leveraging Testcontainers fault injection; document chaos playbook updates in `docs/runbook.md`.
5. Once tests validate, formalize lightweight chaos scripts (make targets or manual-test steps) for recurring drills.

## Dependencies & Notes
- Leverage existing `SchedulingConfig` and `AsyncConfig`; no new infrastructure required.
- Coordinate with QA agent before adding new integration suites to keep TDD gate intact.
- Update `specs/001-e-commerce-platform/tasks.md` when corresponding work items finish (recommend new T094+ entries for DLQ integration and chaos testing coverage).
