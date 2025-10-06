# Operations Runbook

## 1. Purpose & Audience
- Audience: SREs, on-call engineers, and operations analysts supporting the e-commerce platform.
- Objective: Provide fast, repeatable procedures to keep the customer journey healthy and recover gracefully from incidents.
- Scope: `customer-facing-service` (8080) and `order-management-service` (8081) plus PostgreSQL, Redis, Redpanda, and supporting tooling.

## 2. System Snapshot
- **Topology**
  - Customer-facing service issues REST APIs, writes to PostgreSQL (`customer_db`), caches hot catalogue data in Redis, and emits `orders.created` events.
  - Order management service consumes `orders.created`, persists orders to PostgreSQL (`order_db`), interacts with the payment stub, and emits `payments.completed`.
  - Both services publish Micrometer metrics and structured JSON logs enriched with `X-Correlation-ID`.
- **Critical Dependencies**
  - PostgreSQL pods/containers: `postgres-customer`, `postgres-order`.
  - Redis cache: `redis`.
  - Redpanda broker: `redpanda` with DLQ topics `orders.created.dlq` and `payments.completed.dlq`.
  - External payment stub: `payment-gateway` (HTTP).
- **Golden Signals**
  - `checkout_success_total`, `orders_created_total`, HTTP 5xx rate, Kafka consumer lag, circuit breaker state, JVM heap utilisation.

## 3. Monitoring Surfaces
- **Metrics**
  - Endpoint: `http://<host>:<port>/actuator/prometheus`.
  - Dashboards: import JSON from `infrastructure/grafana/dashboards/` when available.
  - Key calculations:
    - Checkout success rate = `checkout_success_total / checkout_attempt_total`.
    - Payment success rate = `payments_completed_total / payments_attempted_total`.
    - Kafka lag = `kafka_consumer_records_lag_max` (Micrometer) or `rpk group describe`.
- **Logs**
  - Format: JSON with `timestamp`, `level`, `correlationId`, `spanId`, `traceId`.
  - Access (Docker): `docker logs <service> | jq '.'` with correlation filtering.
  - Access (Kubernetes): `kubectl logs deploy/customer-facing-service -n <ns> --since=10m`.
- **Health Checks**
  - Liveness: `/actuator/health/liveness`.
  - Readiness: `/actuator/health/readiness` (fails closed on dependency outages).
  - Full health: `/actuator/health` includes dependency detail for dashboards.

## 4. Alert Matrix
| Alert | Detection Hint | First Response | Escalation Trigger |
| ----- | --------------- | -------------- | ------------------ |
| Checkout success < 95% for 5m | Prometheus rule on `checkout_success_total` vs attempts | Run quickstart smoke, inspect payment stub, review error logs | Cannot restore >95% within 30m |
| Kafka consumer lag > 1000 | `kafka_consumer_records_lag_max` or `rpk group describe order-service-group` | Check order service pods, restart consumer, inspect DLQ | Backlog still >1000 after 15m |
| Circuit breaker open (payment) | `resilience4j_circuitbreaker_state{state="OPEN"} > 0` | Verify payment stub health, clear connectivity issues, ensure retries resume | Breaker oscillating for >10m |
| Database connection errors | Surge in 5xx with `org.postgresql.util.PSQLException` | Validate DB pod health, check connection pool saturation, failover if needed | DB unreachable >10m |
| Outbox backlog > 20 pending rows | Query `order_created_outbox` status | Restart publisher, replay DLQ events | Pending > 20 for >15m |

## 5. Incident Playbooks
### 5.1 Kafka Consumer Lag
1. `docker exec -it redpanda rpk group describe order-service-group` (or `kubectl exec` if on K8s).
2. If lag is confined to a partition, check for stuck consumer logs (`OrderCreatedEventConsumer`).
3. Restart order service deployment/pod. Ensure `ORDER_SERVICE_KAFKA_BOOTSTRAP_SERVERS` points to healthy brokers.
4. Inspect DLQ topic:
   ```bash
   docker exec -it redpanda rpk topic consume orders.created.dlq --num 5
   ```
5. After recovery, replay DLQ messages once root cause fixed:
   ```bash
   ./manual-tests/replay-dlq.sh orders.created.dlq
   ```

### 5.2 Payment Circuit Breaker Stuck Open
1. Check payment stub health: `curl http://payment-gateway:9090/actuator/health`.
2. Confirm downstream latency around checkout in logs (`PaymentService`).
3. If stub is healthy, recycle order service pods to clear stale connections.
4. Monitor `resilience4j_circuitbreaker_state{state="CLOSED"}` returning to 1.
5. Communicate to product if customer-impacting downtime exceeded 5 minutes.

### 5.3 Checkout Idempotency Failures
1. Inspect recent `CheckoutIdempotency` entries:
   ```sql
   SELECT idempotency_key, status, created_at
   FROM checkout_idempotency
   ORDER BY created_at DESC LIMIT 10;
   ```
2. Ensure client includes consistent `Idempotency-Key` header; replay cached response via `IdempotencyService` if present.
3. If stale entries accumulate, schedule cleanup job run or prune manually after audit.

### 5.4 Database Degradation
1. Check connection pool metrics (`hikaricp_connections_active`).
2. Verify DB CPU/IO via monitoring platform.
3. If runaway queries observed, capture `pg_stat_activity` and engage engineering for query plan review.
4. Consider temporarily scaling application replicas down to reduce pressure while remediation occurs.

## 6. Routine Operations
- **Deployments**: follow the deployment playbook in `README.md`. Post-deploy, run `scripts/validate-quickstart.sh --target <env>`.
- **Backups**: schedule PostgreSQL dumps via managed service or `pg_dump`. Ensure retention >14 days.
- **Schema Changes**: create new Flyway migrations (never mutate existing) and review with architecture agent before deploy.
- **Secrets Rotation**: rotate JWT secrets and DB passwords quarterly; validate `.env.example` stays accurate.
- **Capacity Reviews**: monthly check on Kafka retention, Redis memory, and JVM heap headroom.

## 7. Communication & Escalation
- Primary channel: `#ecommerce-operations` (Slack or equivalent).
- Escalation ladder: On-call engineer → Implementation Agent → Architecture Agent → Product Agent.
- For user-visible incidents >15 minutes, publish post-incident summary within 24 hours using ADR template.

## 8. Reference Library
- Service APIs: `specs/001-e-commerce-platform/contracts/`.
- Architecture spec: `specs/001-e-commerce-platform/spec.md`.
- Quickstart smoke: `scripts/validate-quickstart.sh`.
- Task register: `specs/001-e-commerce-platform/tasks.md`.
- ADR template: `docs/adrs/` (create if absent when logging significant changes).
