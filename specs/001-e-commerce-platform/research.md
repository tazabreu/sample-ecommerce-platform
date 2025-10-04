# Technical Research: E-Commerce Platform

**Feature**: E-Commerce Platform with Catalog, Shopping Cart, and Order Management  
**Date**: 2025-09-30  
**Status**: Phase 0 Complete

## Overview

This document captures technical research and decisions for implementing a headless e-commerce platform with microservices architecture, event-driven order processing, and resilience-first design patterns.

## Research Areas

### 1. Microservices Architecture Pattern

**Decision**: Two-service architecture with domain-driven boundaries  
**Rationale**:
- **Customer-facing service**: Handles catalog browsing, cart management, and checkout (synchronous operations)
- **Order management service**: Processes orders asynchronously via event consumers (eventual consistency model)
- Clear separation of concerns aligns with business domains and team structure
- Each service can scale independently based on load patterns

**Alternatives Considered**:
- **Monolithic architecture**: Rejected due to coupling concerns and difficulty scaling order processing independently
- **Single service with async workers**: Rejected as it doesn't demonstrate microservices patterns and cloud-native deployment
- **Three+ services (separate payment, inventory)**: Over-engineered for demonstration scope; increases operational complexity without proportional value

**Best Practices Applied**:
- Database per service pattern (each service owns its data)
- API Gateway pattern (future: single entry point for routing)
- Service discovery (future: Consul/Eureka for cloud deployment)
- Bounded contexts from Domain-Driven Design

**References**:
- Spring Cloud documentation: https://spring.io/projects/spring-cloud
- Microservices patterns: https://microservices.io/patterns/microservices.html
- DDD bounded contexts: https://martinfowler.com/bliki/BoundedContext.html

---

### 2. Event-Driven Architecture with Kafka

**Decision**: Apache Kafka (Redpanda locally) for domain event streaming  
**Rationale**:
- **Event types**: `OrderCreated` (checkout → order service), `PaymentCompleted` (payment result → order service)
- Kafka provides durable, ordered message delivery with replay capability
- Redpanda is Kafka-compatible but lighter for local development (no Zookeeper dependency)
- Decouples services: customer service doesn't wait for order processing
- Supports eventual consistency and audit trails (event sourcing patterns)

**Alternatives Considered**:
- **RabbitMQ**: Simpler setup but less scalable; Kafka better for event streaming and replay
- **AWS SQS/SNS**: Cloud-only, not suitable for local development requirement
- **Synchronous REST calls**: Rejected due to tight coupling and resilience concerns

**Configuration**:
- **Topics**: `orders.created`, `payments.completed`
- **Partitioning**: By order ID for ordering guarantees
- **Retention**: 7 days (demonstration), configurable for production
- **Consumer groups**: One per service to scale consumers independently

**Best Practices Applied**:
- Idempotent consumers (handle duplicate message delivery)
- Dead Letter Queue (DLQ) for poison messages
- Schema evolution strategy (Avro or JSON schema with versioning)
- At-least-once delivery semantics with idempotency keys

**References**:
- Spring Kafka documentation: https://spring.io/projects/spring-kafka
- Redpanda quickstart: https://docs.redpanda.com/
- Kafka patterns: https://www.confluent.io/blog/event-driven-microservices-patterns/

---

### 3. Data Storage Strategy

**Decision**: PostgreSQL for both services + Redis for session-based carts  
**Rationale**:
- **PostgreSQL**: ACID compliance for transactional data (products, orders, payments), JSON support for flexible schemas
- **Redis**: In-memory cache for ephemeral shopping cart data (guest checkout, no persistence needed)
- Each service has separate PostgreSQL database (schema isolation)
- Flyway for database migrations (version-controlled schema evolution)

**Schema Design**:
- **Customer Service DB**: `products`, `categories`, `carts`, `cart_items` (cart data also in Redis for fast access)
- **Order Service DB**: `orders`, `order_items`, `payment_transactions`, `customers` (denormalized customer info per order)

**Alternatives Considered**:
- **NoSQL (MongoDB)**: Rejected due to lack of ACID guarantees for financial transactions (orders, payments)
- **Shared database**: Anti-pattern for microservices; creates coupling and single point of failure
- **Event sourcing for everything**: Over-engineered for demonstration; adds complexity without clear value

**Best Practices Applied**:
- Optimistic locking for inventory updates (prevent overselling)
- Read replicas for catalog queries (future scalability)
- Connection pooling (HikariCP default in Spring Boot)
- Database health checks for circuit breaker integration

**References**:
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Flyway migrations: https://flywaydb.org/
- Redis session management: https://redis.io/topics/distcache

---

### 4. Resilience Patterns with Resilience4j

**Decision**: Resilience4j for circuit breakers, retries, timeouts, and bulkheads  
**Rationale**:
- **Circuit breaker**: Protect payment service calls (mock now, Stripe later)
- **Retry with exponential backoff**: Transient failures in Kafka, database, external APIs
- **Timeouts**: Prevent hanging requests (500ms for catalog, 5s for payment)
- **Bulkhead**: Isolate thread pools (separate pools for critical vs non-critical operations)
- **Rate limiting**: Prevent abuse of manager catalog endpoints (Bucket4j)

**Failure Scenarios Handled**:
- Payment service down → Circuit breaker opens, return graceful error
- Kafka temporarily unavailable → Retry with backoff, eventually fail and alert
- Database connection pool exhausted → Bulkhead isolates impact to specific operations
- Slow external API → Timeout prevents cascading delays

**Alternatives Considered**:
- **Netflix Hystrix**: Deprecated, Resilience4j is modern replacement
- **Custom retry logic**: Reinventing the wheel, error-prone
- **No resilience patterns**: Unacceptable per constitution (Resilience First principle)

**Best Practices Applied**:
- Fail-fast for critical paths (checkout)
- Graceful degradation for non-critical features (related products)
- Actuator health indicators tied to circuit breaker state
- Metrics for all resilience patterns (Micrometer)

**References**:
- Resilience4j documentation: https://resilience4j.readme.io/
- Spring Boot integration: https://resilience4j.readme.io/docs/getting-started-3

---

### 5. Authentication & Authorization

**Decision**: JWT tokens for manager authentication, guest checkout for customers  
**Rationale**:
- **Managers**: OAuth 2.0 / OIDC flow with JWT tokens (Spring Security OAuth2 Resource Server)
- **Customers**: No authentication (guest checkout model per spec clarifications)
- Role-based access control (RBAC): `ROLE_MANAGER` for catalog CRUD, `ROLE_GUEST` for shopping
- Stateless authentication (JWT in Authorization header, no server-side sessions)

**Security Measures**:
- TLS/HTTPS for all API communication (terminated at load balancer in production)
- Input validation with JSR-380 Bean Validation (`@Valid`, `@NotNull`, etc.)
- SQL injection prevention via parameterized queries (JPA/Hibernate)
- XSS prevention via content type validation and output encoding
- Secrets management: Environment variables (local), AWS Secrets Manager / Vault (cloud)

**Alternatives Considered**:
- **API keys**: Less secure, harder to revoke, no standard expiration
- **Session-based auth**: Not stateless, doesn't scale horizontally without sticky sessions
- **Basic auth**: Insecure without TLS, credentials sent with every request

**Best Practices Applied**:
- JWT expiration (15 min access token, 7 day refresh token)
- HTTPS-only cookies for refresh tokens (future consideration)
- CORS configuration for future frontend integration
- Rate limiting on authentication endpoints

**References**:
- Spring Security documentation: https://spring.io/projects/spring-security
- OAuth 2.0 Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/

---

### 6. Observability Stack

**Decision**: SLF4J + Logback for logging, Micrometer + Prometheus for metrics, OpenTelemetry for tracing  
**Rationale**:
- **Structured logging**: JSON format with correlation IDs (MDC) for request tracing across services
- **Metrics**: RED method (Rate, Errors, Duration) + business metrics (orders, cart conversions)
- **Distributed tracing**: Trace order flow from checkout → order creation → payment → fulfillment
- **Health checks**: Spring Boot Actuator `/actuator/health` (liveness, readiness probes)

**Metrics to Track**:
- **Technical**: Request latency (p50, p95, p99), error rates, thread pool usage, DB connection pool
- **Business**: Orders created per minute, payment success rate, cart abandonment, catalog views

**Logging Strategy**:
- **Levels**: ERROR (actionable issues), WARN (degraded state), INFO (business events), DEBUG (troubleshooting)
- **Correlation ID**: Generated at API gateway, propagated via MDC to all log statements
- **Log aggregation**: ELK stack (Elasticsearch, Logstash, Kibana) or Splunk (future)

**Alternatives Considered**:
- **Log4j2**: Similar to Logback, but Logback is Spring Boot default and has broader adoption
- **Zipkin for tracing**: Good, but OpenTelemetry is vendor-neutral and more future-proof
- **Custom metrics**: Reinventing the wheel, Micrometer provides abstractions

**Best Practices Applied**:
- Logs to stdout/stderr (12-Factor App), collected by container runtime
- Sensitive data (PII, payment info) masked in logs
- Prometheus metrics endpoint secured (not publicly exposed)
- Alerting on SLO violations (p95 latency >500ms, error rate >1%)

**References**:
- Micrometer documentation: https://micrometer.io/
- OpenTelemetry Java: https://opentelemetry.io/docs/instrumentation/java/
- Spring Boot Actuator: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

---

### 7. Testing Strategy

**Decision**: Multi-layered testing with contract tests, integration tests (Testcontainers), unit tests  
**Rationale**:
- **Contract tests**: Validate API contracts (OpenAPI spec) before implementation (REST Assured)
- **Integration tests**: Test service with real dependencies (PostgreSQL, Kafka) via Testcontainers
- **Unit tests**: Fast, isolated tests for business logic (JUnit 5, Mockito)
- **Performance tests**: Gatling scripts for load testing (future)

**Test Coverage Targets**:
- **Unit tests**: 80%+ coverage for service and domain logic
- **Integration tests**: All critical paths (checkout flow, order processing)
- **Contract tests**: 100% API endpoint coverage

**TDD Workflow**:
1. Write failing contract test (API spec)
2. Write failing integration test (user scenario)
3. Write failing unit test (business logic)
4. Implement minimal code to pass tests
5. Refactor with tests passing

**Alternatives Considered**:
- **End-to-end tests only**: Slow, brittle, hard to debug failures
- **Unit tests only**: Miss integration issues (serialization, transactions)
- **Manual testing**: Not repeatable, doesn't scale with team size

**Best Practices Applied**:
- Test data builders for readable test setup
- Testcontainers for environment parity (same PostgreSQL/Kafka as production)
- Separate test/prod configurations (application-test.yml)
- CI pipeline runs all tests on every PR

**References**:
- Testcontainers documentation: https://www.testcontainers.org/
- REST Assured: https://rest-assured.io/
- Spring Boot Test: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

---

### 8. Deployment & Infrastructure

**Decision**: Docker Compose for local development, Kubernetes for cloud deployment  
**Rationale**:
- **Local**: Docker Compose orchestrates all services (customer, order, PostgreSQL, Redis, Kafka)
- **Cloud**: Kubernetes manifests for scalable deployment (AWS EKS, GCP GKE, Azure AKS)
- **CI/CD**: GitHub Actions or GitLab CI for automated testing and deployment
- **Configuration**: Environment variables (12-Factor), externalized via ConfigMaps/Secrets (K8s)

**Docker Strategy**:
- Multi-stage builds (Maven build stage, JRE runtime stage)
- Distroless base images for security (minimal attack surface)
- Health check support in Dockerfile (HEALTHCHECK instruction)
- Non-root user for container execution

**Infrastructure as Code**:
- Terraform for cloud resources (future: VPC, RDS, ElastiCache, MSK)
- Helm charts for Kubernetes application deployment (future)
- Docker Compose files version-controlled in `/infrastructure`

**Alternatives Considered**:
- **Bare metal**: Not cloud-native, manual scaling
- **VM-based deployment**: Heavier footprint, slower startup
- **Serverless (Lambda)**: Not suitable for long-running Kafka consumers

**Best Practices Applied**:
- Blue-green deployments for zero-downtime releases (K8s rolling updates)
- Resource limits (CPU, memory) to prevent noisy neighbor issues
- Auto-scaling based on CPU/custom metrics (HPA in Kubernetes)
- Readiness/liveness probes for health-based routing

**References**:
- Spring Boot Docker: https://spring.io/guides/topicals/spring-boot-docker/
- Kubernetes deployment: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/

---

### 9. Payment Service Integration

**Decision**: Mock payment service with Stripe-compatible interface  
**Rationale**:
- **Initial implementation**: Mock service that always succeeds (demonstration)
- **Future integration**: Stripe API with minimal changes (interface abstraction)
- **Circuit breaker**: Wrap payment calls to handle Stripe downtime gracefully
- **Webhook handling**: Stripe sends payment results via webhook (async confirmation)

**Mock Service Contract**:
```java
public interface PaymentService {
    PaymentResult processPayment(PaymentRequest request);
}

// Mock: returns success immediately
// Stripe: calls Stripe API, returns result or throws exception
```

**Stripe Integration Considerations** (future):
- API key management (Secrets Manager)
- Idempotency keys to prevent duplicate charges
- Webhook signature verification (HMAC)
- PCI DSS compliance (Stripe handles card data, we handle order data)

**Alternatives Considered**:
- **PayPal**: Less modern API, fewer features
- **Square**: Good for in-person, less focus on e-commerce
- **No abstraction**: Tightly couple to mock, harder to swap later

**Best Practices Applied**:
- Payment service behind interface (Strategy pattern)
- Retry logic with exponential backoff for transient failures
- Correlation IDs for payment transaction tracing
- Audit log of all payment attempts (success, failure, retries)

**References**:
- Stripe API documentation: https://stripe.com/docs/api
- Stripe webhooks: https://stripe.com/docs/webhooks

---

### 10. Data Model Design Decisions

**Decision**: JPA entities with explicit relationships, DTOs for API boundary  
**Rationale**:
- **Entities**: Rich domain models with business logic (validation, state transitions)
- **DTOs**: Separate request/response objects to decouple API from database schema
- **Mapping**: MapStruct for entity ↔ DTO conversion (compile-time safety)
- **Optimistic locking**: `@Version` annotation for concurrency control (inventory updates)

**Entity Design Highlights**:
- **Product**: SKU uniqueness constraint, category foreign key, soft delete flag
- **Cart**: Session-based (stored in Redis with TTL), also persisted to DB for analytics
- **Order**: Denormalized customer info (no foreign key to customer table, guest checkout)
- **OrderItem**: Price snapshot at order time (immutable, historical record)

**Database Normalization**:
- 3NF for transactional data (products, orders)
- Denormalization for read-heavy queries (order summary with customer info)
- Separate read models (future: CQRS pattern for reporting)

**Alternatives Considered**:
- **Anemic domain model**: Entities as data holders, logic in services (rejected: less encapsulation)
- **No DTOs**: Expose entities directly (rejected: tight coupling, serialization issues)
- **GraphQL**: Over-engineered for API-first REST design, adds complexity

**Best Practices Applied**:
- Hibernate second-level cache for frequently accessed data (product catalog)
- Lazy loading for collections (avoid N+1 queries)
- Query optimization (indexes on foreign keys, composite indexes for common queries)
- Audit fields (created_at, updated_at) on all entities

**References**:
- Spring Data JPA best practices: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- MapStruct documentation: https://mapstruct.org/

---

## Technology Stack Summary

| Component | Technology | Justification |
|-----------|-----------|---------------|
| Language | Java 21 | Constitution requirement, LTS support, performance |
| Framework | Spring Boot 3.x | Constitution requirement, production-grade features |
| Build Tool | Maven | Widely adopted, excellent IDE support, corporate standard |
| Database | PostgreSQL 15 | ACID compliance, JSON support, horizontal scaling (Citus) |
| Caching | Redis 7 | In-memory speed, session storage, pub/sub capabilities |
| Messaging | Kafka (Redpanda) | Event streaming, durability, replay capability |
| Resilience | Resilience4j | Modern, lightweight, Spring Boot integration |
| Observability | Micrometer + Prometheus | Metrics abstraction, Prometheus standard for K8s |
| Tracing | OpenTelemetry | Vendor-neutral, CNCF standard, future-proof |
| Testing | JUnit 5, Testcontainers | Modern testing, environment parity |
| Containerization | Docker + Docker Compose | Local development, cloud deployment parity |
| Orchestration | Kubernetes (future) | Scalability, self-healing, cloud-native |

---

## Open Questions Resolved

1. **Q**: How to handle inventory reservation during checkout?  
   **A**: Immediate decrement on order submission (permanent reservation, no release for demo). Future: temporary hold with timeout.

2. **Q**: Should carts be persisted across sessions?  
   **A**: No (guest checkout model). Cart stored in Redis with session TTL (30 min), also persisted to DB for analytics.

3. **Q**: How to ensure idempotency in event consumers?  
   **A**: Event ID stored in `processed_events` table. Consumer checks before processing, skips if already processed.

4. **Q**: What happens if payment service is down during checkout?  
   **A**: Circuit breaker returns error immediately, customer sees "Payment temporarily unavailable" message. Order saved with PENDING status, manual retry (future: automatic retry with backoff).

5. **Q**: How to version APIs for backward compatibility?  
   **A**: URL versioning (`/api/v1/`, `/api/v2/`). Breaking changes require new version. Old versions supported for 6 months.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Kafka message loss | Low | High | At-least-once delivery, idempotent consumers, DLQ |
| Inventory overselling | Medium | High | Optimistic locking, transaction isolation (SERIALIZABLE) |
| Payment service downtime | Medium | High | Circuit breaker, graceful error messages, manual retry |
| Database connection exhaustion | Low | High | Connection pooling, bulkhead pattern, monitoring |
| Slow catalog queries | Medium | Low | Redis cache, read replicas, database indexing |

---

## Next Steps (Phase 1)

1. Generate detailed data model (`data-model.md`) from entities identified
2. Create OpenAPI contracts for all endpoints (`contracts/`)
3. Write contract tests (REST Assured)
4. Document quickstart scenarios (`quickstart.md`)
5. Update agent context file with project structure

---

**Research Complete**: All technical decisions documented with rationale.  
**Unknowns Resolved**: No NEEDS CLARIFICATION remaining.  
**Ready for**: Phase 1 (Design & Contracts)

