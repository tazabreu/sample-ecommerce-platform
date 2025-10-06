# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Headless e-commerce platform with microservices architecture, demonstrating event-driven order processing, resilience patterns, and observability. Built with Java 21, Spring Boot 3.2.x, currently undergoing migration from Spring Data JPA to Spring Data JDBC.

**Two microservices:**
- **customer-facing-service** (Port 8080): Catalog browsing, shopping cart (Redis + PostgreSQL), checkout with transactional outbox
- **order-management-service** (Port 8081): Order processing, payment handling via Kafka event consumers

## Technology Stack

- Java 21 (strict - no JDK 22+ features)
- Spring Boot 3.2.x with Spring Data JDBC (migration in progress from JPA)
- PostgreSQL 15, Redis 7, Kafka (Redpanda for local dev)
- Maven 3.8+ multi-module build
- Testcontainers for integration tests

## Build & Test Commands

```bash
# Build all services (skip tests)
mvn clean install -DskipTests

# Build individual service
cd customer-facing-service && mvn clean install -DskipTests

# Run all tests
mvn test

# Run tests with coverage
mvn verify

# Run tests for specific service
mvn test -pl customer-facing-service
cd customer-facing-service && mvn test

# Run single test class
mvn test -Dtest=CartServiceTest -pl customer-facing-service

# Compile without running tests
mvn clean compile -DskipTests

# Check for JPA/Hibernate dependencies (should return nothing after migration)
mvn dependency:tree | grep -i hibernate
```

## Running Services Locally

```bash
# 1. Start infrastructure (PostgreSQL, Redis, Kafka)
cd infrastructure
docker-compose up -d

# Verify infrastructure is running
docker-compose ps

# Create Kafka topics
chmod +x kafka/create-topics.sh
./kafka/create-topics.sh

# 2. Run customer-facing-service (Terminal 1)
cd customer-facing-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Run order-management-service (Terminal 2)
cd order-management-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Verify health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

## Architecture & Code Structure

### Microservices Architecture

Each service follows domain-driven design with explicit aggregate boundaries:

**customer-facing-service aggregates:**
- `Cart` (root) → `CartItem` (child) - uses `@MappedCollection` in JDBC
- `Product` (root) - stores `categoryId` UUID instead of entity reference
- `Category` (root) - no bidirectional relationships
- Simple entities: `OrderCreatedOutbox`, `CheckoutIdempotency`, `OrderNumberSequence`

**order-management-service aggregates:**
- `Order` (root) → `OrderItem` (child) - uses `@MappedCollection` in JDBC
- `PaymentTransaction` (separate root) - not embedded in Order
- `ProcessedEvent` (root) - idempotent event tracking

### Data Persistence Strategy (JDBC Migration)

**Current State**: Mid-migration from JPA to JDBC (see `MIGRATION_HANDOFF.md`)

**JDBC Patterns:**
- Entities use `@org.springframework.data.annotation.Id` (not `jakarta.persistence.Id`)
- Parent-child relationships: `@MappedCollection(idColumn = "parent_id")` replaces `@OneToMany`
- Foreign keys stored as UUID fields (e.g., `categoryId`) instead of entity references
- Manual UUID generation: `if (entity.getId() == null) entity.setId(UUID.randomUUID())`
- Explicit saves required: service methods must call `repository.save()` after mutations
- No lazy loading: fetch associations separately when needed
- Repositories extend `CrudRepository` or `PagingAndSortingRepository` (not `JpaRepository`)
- Custom queries use SQL with `@Query`, not JPQL

**Auditing:**
- Timestamps managed via `AuditingCallback` (not `@CreationTimestamp`/`@UpdateTimestamp`)
- Entities implement `Auditable` interface

### Event-Driven Integration

**Transactional Outbox Pattern:**
- `OrderCreatedOutbox` table stores events atomically with business data
- Background publisher polls outbox and sends to Kafka
- All events include `eventId` and `correlationId` headers

**Kafka Topics:**
- `orders.created` - Customer service publishes checkout events
- `orders.created.dlt` - Dead letter topic for failed processing
- Consumer group: `order-service-group`

**Monitoring Kafka:**
```bash
# List topics
docker exec -it redpanda rpk topic list

# Consume messages
docker exec -it redpanda rpk topic consume orders.created --brokers localhost:9092

# Check consumer lag
docker exec -it redpanda rpk group describe order-service-group
```

### Resilience Patterns

- Circuit breakers via Resilience4j for payment service calls
- Retries with exponential backoff
- Idempotency keys required for `POST /checkout` (stored 24h in `checkout_idempotency` table)
- Optimistic locking with `@Version` on Cart and Product entities

### Observability

**Prometheus Metrics:**
- http://localhost:8080/actuator/prometheus
- http://localhost:8081/actuator/prometheus
- Custom business metrics: `orders_created_total`, `checkout_success_total`

**Structured Logging:**
- All logs in JSON format with correlation IDs
- Correlation ID propagated via `X-Correlation-ID` header across services and events

**Database Access:**
```bash
# Customer database
docker exec -it postgres-customer psql -U customer_user -d customer_db

# Order database
docker exec -it postgres-order psql -U order_user -d order_db
```

## Development Guidelines (from AGENTS.md)

### Operating Principles

1. **TDD Always**: Write failing contract tests before feature implementation
2. **Small, Reversible Changes**: Keep PRs under 400 lines when possible
3. **Spec is Source of Truth**: Update `specs/001-e-commerce-platform/spec.md` and related docs with architectural changes
4. **Reliability First**: Apply transactional outbox, idempotency keys, DLQ policies before scale-out
5. **Traceability**: Propagate `X-Correlation-ID` from edge to events; include in logs and metrics

### Technical Conventions

- **API Errors**: RFC 7807 Problem Details (`application/problem+json`) with fields: `type`, `title`, `status`, `detail`, `instance`
- **Idempotency**: Require `Idempotency-Key` header on `POST /checkout`; store key → response for 24h
- **Eventing**: Transactional outbox with background publisher; events include `eventId`, `correlationId` headers
- **Security**: Public catalog endpoints; manager endpoints require ROLE_MANAGER; JWT resource server

### Testing Approach

- **Contract Tests**: REST Assured tests in `contract/` package validate external API contracts
- **Integration Tests**: Use Testcontainers (PostgreSQL, Redis, Kafka) for hermetic testing
- **Unit Tests**: Service layer tests with mocked repositories
- No annotation processing in test scope unless explicitly needed

### Java Version Constraints

- All modules target Java 21
- Do NOT introduce JDK 22+ specific APIs, flags, or build arguments
- Maven surefire plugin configured with `--add-opens` flags for Java 21 reflection

## Migration Context (Active Work)

**Current Branch**: `migration/jpa-to-jdbc`
**Status**: Phase 5 partial - Dependencies updated, entity/service transformation in progress

**Key Migration Files:**
- `MIGRATION_JPA_JDBC.md` - Comprehensive 1900-line migration guide with 38 tasks
- `MIGRATION_HANDOFF.md` - Quick handoff document with remaining work
- `MIGRATION_BASELINE.md` - Pre-migration test baseline (4 known failing tests due to PostgreSQL ENUM casting)

**Completed:**
- M001-M002: Environment validation, dependency audit
- M029-M030: Updated `pom.xml` dependencies (JPA → JDBC)

**In Progress (Phase 2-4):**
- M009-M021: Entity model transformation (11 entities)
- M022-M023: Repository updates (11 repositories)
- M024-M028: Service layer adjustments (6 services)

**Critical JDBC Transformation Rules:**
- Remove JPA annotations (`@Entity`, `@GeneratedValue`, `@Column`, `@ManyToOne`, `@OneToMany`)
- Use Spring Data JDBC annotations (`@Table`, `@MappedCollection`)
- Add explicit setters for all fields (JDBC requires them for hydration)
- Manual UUID generation before saves
- Replace entity references with UUID foreign keys
- No cascading - explicit saves required

**Known Issue:**
- 4 failing tests in `OrderContractTest` due to PostgreSQL ENUM casting (will be fixed at M023)

## Commit Message Style

**IMPORTANT**: Before committing, review `AGENTS.md` for commit workflow best practices and guardrails.

Follow conventional commits with emojis:
```
<emoji> <type>(<scope>): <description>

<optional body with bullet points>
```

**Pre-Commit Checklist (from AGENTS.md):**
1. Review all changes comprehensively (`git status --short`, `git diff --stat`)
2. Create visual change tree to understand scope
3. Analyze impact: production code vs tests vs docs vs infrastructure
4. Draft commit message following project's constitution
5. Verify tasks.md is synchronized if completing tasks
6. Ensure commit message has detailed body for non-trivial changes

Examples:
- `✨ feat(cart): add Redis caching for cart operations`
- `🔧 chore(deps): replace Spring Data JPA with JDBC (M029-M030)`
- `♻️ refactor(entities): transform JPA entities to JDBC aggregates`
- `🐛 fix(order): add explicit ENUM cast in findWithFilters query`

**Co-Authorship**: Include `Co-Authored-By: Claude <noreply@anthropic.com>` for AI pair programming

## API Documentation

Once services are running:
- **Customer Service Swagger**: http://localhost:8080/swagger-ui.html
- **Order Service Swagger**: http://localhost:8081/swagger-ui.html
- OpenAPI specs: `/v3/api-docs`

## Key Documentation Files

- `README.md` - Quick start guide and project overview
- `AGENTS.md` - Agent roles, operating principles, technical conventions
- `specs/001-e-commerce-platform/spec.md` - Feature specification
- `specs/001-e-commerce-platform/plan.md` - Implementation plan
- `specs/001-e-commerce-platform/data-model.md` - Data model design
- `specs/001-e-commerce-platform/contracts/` - API and event contracts
- `docs/jpa-dependencies-audit.md` - JPA dependency audit (created during migration)

## Manual Testing

```bash
cd manual-tests
python test_runner.py
```

## Important Constraints

- Never enable annotation processing for test sources unless explicitly required
- Prefer editing existing files with minimal diffs
- Do not reformat unrelated code
- When proposing changes, cite code using `file:line` format
- Ask clarifying questions before making significant architectural decisions
- **Always keep `specs/001-e-commerce-platform/tasks.md` synchronized with implementation**: Mark tasks complete [X] with evidence (files, implementation details) immediately after changes. Never let tasks.md drift from codebase state
