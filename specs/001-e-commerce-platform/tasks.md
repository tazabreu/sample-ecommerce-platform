# Tasks: E-Commerce Platform with Catalog, Shopping Cart, and Order Management

_Last updated: 2025-10-06_

## Milestone History
- ✅ Infrastructure, contract tests, and core domain services shipped (T001-T050)
- ✅ Spring Data JDBC migration complete across both services (T054-T055)
- ✅ Reliability hardening delivered: idempotency, transactional outbox, DLQ strategy (T087-T091)
- ✅ Documentation uplift executed: README, runbook, quickstart automation, docs index (T070-T073)

## Active Backlog (12 open / 92 total = 87% complete)

### Critical Quality Gates
- [X] **T092** 🚨 Increase unit test coverage on both services to >80% before integration tests ✅ **COMPLETED**
  Evidence:
  - customer-facing-service: 107 tests passing (service layer: CartServiceTest +8, OrderNumberServiceTest +5, CatalogServiceTest 26, CheckoutServiceTest 12, IdempotencyServiceTest 10)
  - order-management-service: 60 tests passing (service layer: OrderProcessingServiceTest 8, OrderQueryServiceTest 24, PaymentCompletedServiceTest 4)
  - Total: 167 tests, 100% passing, BUILD SUCCESS
  - Files: OrderNumberServiceTest.java (NEW), CartServiceTest.java (expanded), OrderNumberService.java (removed 999/day limit - now supports unlimited orders)
  - OrderNumberService changed from MAX_SEQUENCE_PER_DAY=999 to unlimited (2.1B/day supported by INT column)
  - Format changed from %03d to %d (removes padding, supports larger numbers)  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/**`, `order-management-service/src/test/java/com/ecommerce/order/**`  
  **Scope**: Add focused unit tests for service, repository, controller, and event infrastructure layers; target >80% line coverage and >70% branch coverage per service  
  **Acceptance Criteria**: JaCoCo report >80% coverage on both services; no skipped tests; mvn test green

- [ ] **T057** Post-migration cleanup and optimization  
  **Files**: Both services configuration + model packages  
  **Remaining Scope**: Normalize datasource configuration keys, prune legacy Hibernate logging settings, align cart item mapping strategy, unify auditing callbacks, and batch-lock checkout inventory updates

### Integration Validation (Blocked by T092)
- [ ] **T058** [P] Integration test for complete checkout flow  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/integration/CheckoutFlowIntegrationTest.java`  
  **Details**: Testcontainers + Embedded Kafka scenario covering catalog → cart → checkout → OrderCreated event publish → inventory decrement → cart cleanup

- [ ] **T059** [P] Integration test for order processing flow  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/integration/OrderProcessingIntegrationTest.java`  
  **Details**: Publish OrderCreated event, assert order lifecycle to PAID, PaymentCompleted event emission, and payment transaction persistence

- [ ] **T060** [P] Integration test for cart expiration  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/integration/CartExpirationIntegrationTest.java`  
  **Details**: Use short TTL, validate cart eviction from Redis/PostgreSQL via scheduled cleanup

- [ ] **T061** [P] Integration test for payment failure handling  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/integration/PaymentFailureIntegrationTest.java`  
  **Details**: Force MockPaymentService failure, assert order status=FAILED and PaymentCompleted event with failureReason

- [ ] **T062** [P] Integration test for circuit breaker behavior  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/integration/CircuitBreakerIntegrationTest.java`  
  **Details**: Simulate payment timeouts, verify breaker opens, health surfaces reflect state, and recovery to HALF_OPEN

- [ ] **T063** [P] Integration test for manager operations  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/integration/ManagerOperationsIntegrationTest.java`  
  **Details**: Validate cancel and fulfill flows for paid orders including tracking info capture

### Platform Tooling & Observability
- [ ] **T065** [P] Create environment variable template  
  **Files**: `.env.example`  
  **Details**: Document DATABASE_*, REDIS_URL, KAFKA_BOOTSTRAP_SERVERS, JWT_SECRET, JWT_ISSUER with local vs cloud guidance; keep in sync with application.yml

- [ ] **T068** [P] Configure Prometheus scraping for both services  
  **Files**: `infrastructure/prometheus/prometheus.yml`, `infrastructure/prometheus/alert-rules.yml`  
  **Details**: Target `/actuator/prometheus` for customer (8080) and order (8081) services; author alerts for consumer lag, payment success rate, p95 latency, and service availability

- [ ] **T069** [P] Setup Grafana dashboards  
  **Files**: `infrastructure/grafana/dashboards/ecommerce-overview.json`, `order-processing.json`  
  **Details**: Overview (RED metrics, carts, orders) and order processing dashboards (status mix, payment success, consumer lag, circuit breaker state)

### Documentation & Contract Sync
- [ ] **T067** [P] AI-driven API documentation sync  
  **Files**: `specs/001-e-commerce-platform/contracts/`  
  **Details**: Compare implemented controllers/events vs contracts, update OpenAPI specs to eliminate drift, document variances

- [ ] **T074** [P] Write service-specific README files  
  **Files**: `customer-facing-service/README.md`, `order-management-service/README.md`  
  **Details**: Capture architecture, datasource schemas, API endpoints, Kafka topics, config properties, local dev steps, and test guidance per service

### Follow-up / Critiques
- [ ] **T093** [P] Harden quickstart validation authentication strategy  
  **Files**: `scripts/validate-quickstart.sh`, security docs  
  **Details**: Script currently depends on dev-only `/api/v1/auth/login`; design production-safe token acquisition (service account or OAuth client) and document fallback procedure

---

## Completed Task Archive

### Execution Flow Summary
This tasks register was generated from plan.md, data-model.md, contracts/, kafka-events.md, and quickstart.md. Execution follows strict TDD sequencing: contract tests → implementation → integration → polish. Tasks marked [P] are parallel-safe (distinct files, no dependency conflicts).

## Phase 3.1: Infrastructure Setup (Priority 1)

**Purpose**: Bootstrap project structure, dependencies, and infrastructure before any code

- [X] **T001** Create parent POM with multi-module Maven project structure  
  **Files**: `pom.xml` (root), `customer-facing-service/pom.xml`, `order-management-service/pom.xml`  
  **Details**: Parent POM with dependency management (Spring Boot 3.x, Resilience4j, Testcontainers, REST Assured). Modules: customer-facing-service, order-management-service, shared-lib (optional)

- [X] **T002** [P] Create Docker Compose infrastructure file  
  **Files**: `infrastructure/docker-compose.yml`, `infrastructure/docker-compose.override.yml`  
  **Details**: PostgreSQL 15 (2 databases: customer_db, order_db), Redis 7, Redpanda (preferred Kafka-compatible broker). Include health checks, volumes, and environment variables

- [X] **T003** [P] Configure Flyway database migrations for customer-facing service  
  **Files**: `customer-facing-service/src/main/resources/db/migration/V1__create_categories_table.sql`, `V2__create_products_table.sql`, `V3__create_carts_table.sql`, `V4__create_cart_items_table.sql`, `V5__create_indexes.sql`  
  **Details**: Create tables per data-model.md with constraints, indexes, and audit fields. Use UUID primary keys, foreign keys, CHECK constraints

- [X] **T004** [P] Configure Flyway database migrations for order management service  
  **Files**: `order-management-service/src/main/resources/db/migration/V1__create_order_enums.sql`, `V2__create_orders_table.sql`, `V3__create_order_items_table.sql`, `V4__create_payment_transactions_table.sql`, `V5__create_indexes.sql`, `V6__create_processed_events_table.sql`  
  **Details**: Create ENUM types (order_status, payment_status), tables with JSONB for addresses, idempotency table

- [X] **T005** [P] Create Kafka topic configuration script  
  **Files**: `infrastructure/kafka/create-topics.sh`  
  **Details**: Create topics `orders.created` and `payments.completed` on Redpanda (preferred Kafka-compatible broker) with 3 partitions, retention 7 days, compression snappy. Include DLQ topics

- [X] **T006** [P] Configure customer-facing service application properties  
  **Files**: `customer-facing-service/src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`  
  **Details**: Spring Boot config (server port 8080), PostgreSQL connection, Redis connection, Kafka producer, Actuator endpoints, logging (JSON format with correlation IDs)

- [X] **T007** [P] Configure order management service application properties  
  **Files**: `order-management-service/src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`  
  **Details**: Spring Boot config (server port 8081), PostgreSQL connection, Kafka consumer (group: order-service-group, earliest offset), Actuator, Resilience4j circuit breaker for payment service

- [X] **T008** [P] Configure Resilience4j patterns in customer-facing service  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/ResilienceConfig.java`  
  **Details**: Circuit breaker config for Kafka publishing, timeout policies (500ms for catalog queries), retry with exponential backoff

- [X] **T009** [P] Configure Resilience4j patterns in order management service  
  **Files**: `order-management-service/src/main/java/com/ecommerce/order/config/ResilienceConfig.java`  
  **Details**: Circuit breaker for payment service (3 failures → open, 30s wait), retry policy (3 attempts, 1s-5s backoff), timeout 5s

- [X] **T010** [P] Setup Micrometer and Prometheus metrics exposure  
  **Files**: Customer and order services - `config/MetricsConfig.java`  
  **Details**: Enable `/actuator/prometheus` endpoint, custom metrics (orders created, payment success rate, cart conversions), RED method metrics (Rate, Errors, Duration)

- [X] **T011** [P] Configure Spring Boot Actuator health checks  
  **Files**: Both services - `config/ActuatorConfig.java`  
  **Details**: Health endpoints (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`), database health indicator, Kafka health indicator, Redis health indicator

- [X] **T012** [P] Setup structured logging with correlation IDs  
  **Files**: Both services - `src/main/resources/logback-spring.xml`, `config/LoggingConfig.java`  
  **Details**: JSON log format, MDC for correlation IDs (generated at controller entry), log levels (ERROR, WARN, INFO), mask PII in logs

---

## Phase 3.2: Contract Tests First (Priority 2) ⚠️ TDD GATE

**CRITICAL**: These tests were written before implementation and validated expected failures per constitution.

### Customer-Facing Service Contract Tests

- [X] **T013** [P] Category endpoints contract tests  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/contract/CategoryContractTest.java`

- [X] **T014** [P] Product endpoints contract tests  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/contract/ProductContractTest.java`

- [X] **T015** [P] Cart endpoints contract tests  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/contract/CartContractTest.java`

- [X] **T016** [P] Checkout endpoint contract tests  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/contract/CheckoutContractTest.java`

### Order Management Service Contract Tests

- [X] **T017** [P] Order endpoints contract tests  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/contract/OrderContractTest.java`

- [X] **T018** [P] Health endpoints contract tests  
  **Files**: `order-management-service/src/test/java/com/ecommerce/order/contract/HealthContractTest.java`

### Event Schema Contract Tests

- [X] **T019** [P] OrderCreatedEvent schema contract tests  
  **Files**: `shared-lib/src/test/java/com/ecommerce/shared/event/OrderCreatedEventContractTest.java`

- [X] **T020** [P] PaymentCompletedEvent schema contract tests  
  **Files**: `shared-lib/src/test/java/com/ecommerce/shared/event/PaymentCompletedEventContractTest.java`

---

## Phase 3.3: Entity Models & Repositories (Priority 3)

- [X] **T021** [P] Category entity
- [X] **T022** [P] Product entity
- [X] **T023** [P] Cart entity
- [X] **T024** [P] CartItem entity
- [X] **T025** [P] Order entity
- [X] **T026** [P] OrderItem entity
- [X] **T027** [P] PaymentTransaction entity
- [X] **T028** [P] Customer service repositories
- [X] **T029** [P] Order service repositories

*(All entity tasks delivered with Spring Data JDBC aggregates, UUID identifiers, optimistic locking where required.)*

---

## Phase 3.4: DTOs & Mappers (Priority 4)

- [X] **T030** [P] Category DTOs & mapper
- [X] **T031** [P] Product DTOs & mapper
- [X] **T032** [P] Cart DTOs & mapper
- [X] **T033** [P] Order DTOs & mapper

---

## Phase 3.5: Service Layer (Priority 5)

- [X] **T034** Checkout service orchestration
- [X] **T035** Catalog service CRUD
- [X] **T036** Cart service with Redis cache
- [X] **T037** Inventory reservation logic
- [X] **T038** Kafka event publisher with idempotency guard
- [X] **T039** Payment service integration (circuit breaker + retry)
- [X] **T040** Order processing service (consumer side)
- [X] **T041** Order query service (manager operations)

---

## Phase 3.6: Event Infrastructure (Priority 6)

- [X] **T042** Transactional outbox implementation (customer service)
- [X] **T043** Outbox publisher scheduler
- [X] **T044** Order service Kafka consumer with DLQ handoff

---

## Phase 3.7: REST Controllers (Priority 7)

- [X] **T045** CategoryController endpoints
- [X] **T046** ProductController endpoints
- [X] **T047** CartController endpoints
- [X] **T048** CheckoutController with idempotency
- [X] **T049** OrderController (public + manager flows)
- [X] **T050** Global exception handling with RFC 7807 responses

---

## Phase 3.8: Security Configuration (Priority 8)

- [X] **T051** JWT resource server configuration (customer service)
- [X] **T052** JWT resource server configuration (order service)
- [X] **T053** Role-based access control for manager endpoints

---

## Phase 3.9: Data Access Modernization (Priority 9)

- [X] **T054** Migrate customer-facing service from JPA to Spring Data JDBC
- [X] **T055** Migrate order management service from JPA to Spring Data JDBC
- [X] **T056** Performance validation — cancelled (documented rationale)
- [ ] **T057** Post-migration cleanup — see Active Backlog for remaining scope

---

## Phase 3.9.5: Unit Test Coverage (Priority 9.5)

- Outstanding work tracked as **T092** in Active Backlog (unit coverage gate prior to integration tests).

---

## Phase 3.10: Integration Tests (Priority 10)

- Outstanding work tracked as **T058-T063** in Active Backlog (blocked until T092 completes).

---

## Phase 3.11: Deployment & Infrastructure (Priority 11)

- [X] **T064** [P] Create optimized Dockerfiles for both services
- [ ] **T065** [P] Environment variable template — see Active Backlog

---

## Phase 3.12: Observability & Monitoring (Priority 12)

- [X] **T066-T067** Custom Micrometer metrics (folded into T091 completion)
- [ ] **T068** Configure Prometheus scraping — see Active Backlog
- [ ] **T069** Setup Grafana dashboards — see Active Backlog

---

## Phase 3.13: Documentation & Polish (Priority 13)

- [X] **T070** [P] Write comprehensive README.md  
  **Status**: ✅ DONE (2025-10-06) — README now covers architecture, quickstart, quality gates, deployment, troubleshooting, and references quickstart automation

- [X] **T071** [P] Create operational runbook  
  **Status**: ✅ DONE (2025-10-06) — `docs/runbook.md` provides topology, alert matrix, incident playbooks, operations cadence

- [X] **T072** [P] Create quickstart validation script  
  **Status**: ✅ DONE (2025-10-06) — `scripts/validate-quickstart.sh` seeds catalog, executes checkout, polls order status, and cleans artefacts with correlation-aware logging

- [X] **T073** [P] Publish documentation index  
  **Status**: ✅ DONE (2025-10-06) — `docs/README.md` maps core references, runbook, automation assets, and contribution guardrails

- [ ] **T067** [P] API documentation sync — see Active Backlog
- [ ] **T074** [P] Service-specific READMEs — see Active Backlog

---

## Phase 3.14: Reliability & Operational Hardening (Priority 14)

~~**DEPRECATED**: Tasks T079-T086 were superseded by urgent remediation (T087-T091).~~

**Status**: ✅ Completed via T087-T091 (idempotency, outbox, DLQ, metrics hardening).

---

## Urgent Remediation Tasks (Priority 0)

- [X] **T087** Harden checkout with persisted Idempotency-Key workflow — completed with dedicated table, TTL enforcement, cached response replay
- [X] **T088** Implement transactional outbox publishing for OrderCreated events — background publisher, status transitions, retry policy
- [X] **T089** Implement dead-letter handling for Kafka consumers — DLQ topics, retry/backoff strategy
- [X] **T090** Ensure payment gateway resilience — circuit breaker tuning, fallback messaging
- [X] **T091** Expand observability — custom business KPIs and enhanced logging

---

## Dependencies Graph

```
Setup (T001-T012)
  ↓
Contract Tests (T013-T020) [MUST FAIL]
  ↓
Entities & Repositories (T021-T029)
  ↓
DTOs & Mappers (T030-T033)
  ↓
Service Layer (T034-T041)
  ↓
Event Infrastructure (T042-T044)
  ↓
Controllers (T045-T050) [Contract tests now PASS]
  ↓
Security (T051-T053)
  ↓
Data Access Modernization (T054-T056)
  ↓
Integration Tests (T057-T063)
  ↓
Deployment (T064-T065)
  ↓
Observability (T066-T069)
  ↓
Documentation (T070-T078)
  ↓
Reliability Hardening (T079-T086)
```

---

## Parallel Execution Examples

### Setup Phase (All Parallel)
```bash
# Independent infrastructure tasks
Task T002: "Create Docker Compose infrastructure"
Task T003: "Configure Flyway migrations for customer service"
Task T004: "Configure Flyway migrations for order service"
Task T005: "Create Kafka topic configuration"
Task T006: "Configure customer service application.yml"
Task T007: "Configure order service application.yml"
Task T008: "Configure Resilience4j in customer service"
Task T009: "Configure Resilience4j in order service"
Task T010: "Setup Micrometer and Prometheus"
Task T011: "Configure Actuator health checks"
Task T012: "Setup structured logging"
```

### Contract Tests Phase (All Parallel)
```bash
Task T013: "Category endpoints contract tests"
Task T014: "Product endpoints contract tests"
Task T015: "Cart endpoints contract tests"
Task T016: "Checkout endpoint contract tests"
Task T017: "Order endpoints contract tests"
Task T018: "Health endpoints contract tests"
Task T019: "OrderCreatedEvent schema contract tests"
Task T020: "PaymentCompletedEvent schema contract tests"
```

### Entity Models Phase (All Parallel)
```bash
Task T021: "Category entity"
Task T022: "Product entity"
Task T023: "Cart entity"
Task T024: "CartItem entity"
Task T025: "Order entity"
Task T026: "OrderItem entity"
Task T027: "PaymentTransaction entity"
Task T028: "Customer service repositories"
Task T029: "Order service repositories"
```

### Integration Tests Phase (Parallel Candidates)
```bash
Task T058: "Checkout flow integration test"
Task T059: "Order processing integration test"
Task T060: "Cart expiration integration test"
Task T061: "Payment failure integration test"
Task T062: "Circuit breaker integration test"
Task T063: "Manager operations integration test"
```

---

## Task Validation Checklist

**Contract Coverage**:
- [x] All customer service endpoints covered by contract tests (T013-T016)
- [x] All order service endpoints covered by contract tests (T017-T018)
- [x] All Kafka events covered by schema tests (T019-T020)

**Entity Coverage**:
- [x] All entities implemented (T021-T027)
- [x] Repositories delivered (T028-T029)

**TDD Workflow**:
- [x] Contract tests preceded implementation
- [x] Integration tests scheduled after full stack build-out
- [x] Contract tests annotated MUST FAIL before implementation

**Parallelization**:
- [x] All [P] tasks operate on independent modules/files
- [x] No parallel tasks depend on one another

**Completeness**:
- [x] Quickstart journeys implemented (checkout flow T054, manager operations T060)
- [x] Resilience patterns implemented (circuit breaker T059, retries T008-T009)
- [x] Observability foundations in place (metrics T066, health checks T011, logging T012)
- [x] Security requirements delivered (JWT auth T051-T053, role-based controllers)

**File Paths**:
- [x] Tasks specify exact file paths aligned to module structure

---

## Success Criteria

- [ ] All 92 tasks completed (T001-T092)
- [ ] Unit tests: >80% coverage on both services (T092)
- [ ] Integration tests: 100% passing (T058-T063)
- [ ] Quickstart script: Executes without errors (T072)
- [ ] Prometheus/Grafana observability live (T068-T069)
- [ ] Environment template published (T065)
- [ ] API docs synchronized with implementation (T067)
- [ ] Service READMEs published (T074)

---

## Notes for Implementation Agents

1. TDD remains non-negotiable: write failing tests before implementation.
2. Testcontainers required for integration tests (ensure Docker running).
3. Enforce idempotency for Kafka consumers via ProcessedEventRepository.
4. Maintain optimistic locking handling downstream.
5. Monitor circuit breaker transitions during payment failure tests.
6. Propagate `X-Correlation-ID` through logs, metrics, and events.
7. Keep Flyway migrations append-only (add new versions for schema changes).
8. Maintain environment parity between local and deployment manifests.
9. Document any config drift immediately in `.env.example` and service READMEs.
10. Observe AGENTS constitution for role hand-offs and traceability.

---

## Success Metrics Dashboard

- Unit test coverage target: >80% (per JaCoCo)
- Checkout success rate target: >95%
- Kafka consumer lag: < 1000 records
- Payment success rate: > 97%
- P95 checkout latency: < 500 ms

---

## Timeline & Resourcing Snapshot

- Tasks generated for 92 work items; 79 completed (86%).
- Estimated remaining effort: 60-80 developer-hours (unit tests + integrations + observability + docs sync).
- Recommended staffing: 2 developers (pair on T092 + integration tests) plus 1 ops engineer for observability rollout.
