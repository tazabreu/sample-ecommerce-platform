# Kafka Event Schemas

**Feature**: E-Commerce Platform Event-Driven Architecture  
**Date**: 2025-09-30  
**Version**: 1.0.0

## Overview

This document defines the event schemas for asynchronous communication between the customer-facing service and order management service via Apache Kafka.

---

## Event Design Principles

1. **Self-contained**: Events include all data needed for consumers (no additional API calls)
2. **Immutable**: Once published, events are never modified
3. **Versioned**: Schema evolution via version field
4. **Idempotent**: Consumers must handle duplicate delivery (at-least-once semantics)
5. **Traceable**: Every event includes correlation ID for distributed tracing

---

## Topics

| Topic Name | Purpose | Producer | Consumer |
|------------|---------|----------|----------|
| `orders.created` | Order submitted for processing | Customer Service | Order Service |
| `payments.completed` | Payment processing result | Order Service | Order Service (internal) |

**Topic Configuration**:
- **Partitions**: 3 (partition key: orderId for ordering guarantees)
- **Replication Factor**: 3 (production), 1 (local development)
- **Retention**: 7 days (replay capability)
- **Compression**: snappy (balance speed/size)

---

## Event 1: OrderCreatedEvent

**Topic**: `orders.created`  
**Producer**: Customer-Facing Service (CheckoutService)  
**Consumer**: Order Management Service (OrderCreatedListener)  
**Trigger**: Customer completes checkout

### Schema (JSON)

```json
{
  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "eventType": "ORDER_CREATED",
  "eventVersion": "1.0",
  "timestamp": "2025-09-30T12:34:56.789Z",
  "correlationId": "req-abc123",
  "orderId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "orderNumber": "ORD-20250930-001",
  "customer": {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+14155551234",
    "shippingAddress": {
      "street": "123 Main Street, Apt 4B",
      "city": "San Francisco",
      "state": "CA",
      "postalCode": "94105",
      "country": "USA"
    }
  },
  "items": [
    {
      "productId": "a8f5f167-024d-4a61-8f12-5e1b4e9c6f3a",
      "productSku": "SKU-WIDGET-001",
      "productName": "Premium Widget",
      "quantity": 2,
      "priceSnapshot": 29.99,
      "subtotal": 59.98
    },
    {
      "productId": "b9f6f268-135e-5b72-9g23-6f2c5f0d7g4b",
      "productSku": "SKU-GADGET-042",
      "productName": "Deluxe Gadget",
      "quantity": 1,
      "priceSnapshot": 49.99,
      "subtotal": 49.99
    }
  ],
  "subtotal": 109.97,
  "cartId": "e1f7f369-246f-6c83-0h34-7g3d6g1e8h5c"
}
```

### Field Definitions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| eventId | UUID | Yes | Unique event identifier (for idempotency checks) |
| eventType | String | Yes | Always "ORDER_CREATED" |
| eventVersion | String | Yes | Schema version (semantic versioning) |
| timestamp | ISO 8601 DateTime | Yes | Event creation timestamp (UTC) |
| correlationId | String | Yes | Request correlation ID (for tracing) |
| orderId | UUID | Yes | Unique order identifier |
| orderNumber | String | Yes | Human-readable order number (ORD-YYYYMMDD-NNN) |
| customer | Object | Yes | Customer information object |
| customer.name | String | Yes | Customer full name (1-200 chars) |
| customer.email | String | Yes | Customer email (valid email format) |
| customer.phone | String | Yes | Customer phone (+E.164 format preferred) |
| customer.shippingAddress | Object | Yes | Shipping address object |
| customer.shippingAddress.street | String | Yes | Street address (1-200 chars) |
| customer.shippingAddress.city | String | Yes | City name (1-100 chars) |
| customer.shippingAddress.state | String | Yes | State/province code (2-100 chars) |
| customer.shippingAddress.postalCode | String | Yes | Postal/ZIP code (1-20 chars) |
| customer.shippingAddress.country | String | Yes | Country code (ISO 3166-1 alpha-3) |
| items | Array | Yes | Order items (min 1 item) |
| items[].productId | UUID | Yes | Product unique identifier |
| items[].productSku | String | Yes | Product SKU (1-50 chars) |
| items[].productName | String | Yes | Product name (1-200 chars) |
| items[].quantity | Integer | Yes | Item quantity (min 1) |
| items[].priceSnapshot | Decimal | Yes | Price at order time (2 decimal places) |
| items[].subtotal | Decimal | Yes | Item subtotal (quantity × priceSnapshot) |
| subtotal | Decimal | Yes | Order subtotal (sum of all item subtotals) |
| cartId | UUID | Yes | Cart ID (for analytics/tracking) |

### Consumer Processing Logic

1. **Idempotency Check**: Query `processed_events` table for `eventId`
   - If exists: Log and skip (duplicate delivery)
   - If not exists: Proceed to step 2
2. **Create Order**: Insert into `orders` table with status=PENDING
3. **Create Order Items**: Bulk insert into `order_items` table
4. **Create Payment Transaction**: Insert into `payment_transactions` with status=PENDING
5. **Record Event**: Insert `eventId` into `processed_events` table
6. **Trigger Payment**: Asynchronously call PaymentService.processPayment()
7. **Commit Transaction**: All-or-nothing database transaction

### Error Handling

- **Validation Error**: Log error, send to DLQ (dead letter queue) `orders.created.dlq`
- **Database Error**: Retry up to 3 times with exponential backoff, then DLQ
- **Payment Service Down**: Circuit breaker triggers, order status remains PENDING, manual retry

---

## Event 2: PaymentCompletedEvent

**Topic**: `payments.completed`  
**Producer**: Order Management Service (PaymentService)  
**Consumer**: Order Management Service (PaymentCompletedListener)  
**Trigger**: Payment processing completes (success or failure)

### Schema (JSON)

#### Success Case
```json
{
  "eventId": "d4a96f75-6828-5673-c4gd-3d074g2a01bf8",
  "eventType": "PAYMENT_COMPLETED",
  "eventVersion": "1.0",
  "timestamp": "2025-09-30T12:35:02.456Z",
  "correlationId": "req-abc123",
  "orderId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "orderNumber": "ORD-20250930-001",
  "paymentTransactionId": "f5b07f86-7939-6784-d5he-4e185h3b12cg9",
  "status": "SUCCESS",
  "amount": 109.97,
  "paymentMethod": "MOCK",
  "externalTransactionId": "mock_tx_1727701502",
  "failureReason": null
}
```

#### Failure Case
```json
{
  "eventId": "e5b08f97-8040-7895-e6if-5f296i4c23dh0",
  "eventType": "PAYMENT_COMPLETED",
  "eventVersion": "1.0",
  "timestamp": "2025-09-30T12:35:03.789Z",
  "correlationId": "req-xyz789",
  "orderId": "8d0f7780-8536-51ef-c55c-f18gd2g01bf9",
  "orderNumber": "ORD-20250930-002",
  "paymentTransactionId": "g6c18g08-8a4a-7896-f7jg-6g307j5d34ei1",
  "status": "FAILED",
  "amount": 79.99,
  "paymentMethod": "MOCK",
  "externalTransactionId": null,
  "failureReason": "Payment gateway timeout (simulated failure)"
}
```

### Field Definitions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| eventId | UUID | Yes | Unique event identifier (for idempotency checks) |
| eventType | String | Yes | Always "PAYMENT_COMPLETED" |
| eventVersion | String | Yes | Schema version (semantic versioning) |
| timestamp | ISO 8601 DateTime | Yes | Event creation timestamp (UTC) |
| correlationId | String | Yes | Request correlation ID (matches OrderCreatedEvent) |
| orderId | UUID | Yes | Order unique identifier |
| orderNumber | String | Yes | Human-readable order number |
| paymentTransactionId | UUID | Yes | Payment transaction unique identifier |
| status | Enum | Yes | Payment status: SUCCESS or FAILED |
| amount | Decimal | Yes | Payment amount (2 decimal places) |
| paymentMethod | String | Yes | Payment method: MOCK, STRIPE, PAYPAL, etc. |
| externalTransactionId | String | No | External payment provider transaction ID (null if failed) |
| failureReason | String | No | Failure reason (null if success, 1-500 chars if failed) |

### Consumer Processing Logic

1. **Idempotency Check**: Query `processed_events` table for `eventId`
   - If exists: Log and skip (duplicate delivery)
   - If not exists: Proceed to step 2
2. **Update Payment Transaction**: Set status, externalTransactionId, failureReason, completedAt
3. **Update Order Status**:
   - If SUCCESS: status=PROCESSING → PAID
   - If FAILED: status=PROCESSING → FAILED
4. **Record Event**: Insert `eventId` into `processed_events` table
5. **Commit Transaction**: All-or-nothing database transaction

### Error Handling

- **Validation Error**: Log error, send to DLQ `payments.completed.dlq`
- **Database Error**: Retry up to 3 times with exponential backoff, then DLQ
- **Inconsistent State**: Alert operations team (order/payment mismatch)

---

## Event Versioning Strategy

### Backward-Compatible Changes (Patch/Minor)
- Adding optional fields
- Adding new enum values (append only)
- Clarifying field descriptions

**Example**: Adding optional `discount` field to OrderCreatedEvent
```json
{
  "eventVersion": "1.1",
  "subtotal": 109.97,
  "discount": 10.00,  // NEW optional field
  "total": 99.97      // NEW optional field
}
```

### Breaking Changes (Major)
- Removing required fields
- Renaming fields
- Changing field types
- Changing event structure

**Migration Path**:
1. Deploy consumers that support both versions
2. Deploy producers with new version
3. Wait for old events to expire (retention period)
4. Remove old version support from consumers

**Example**: Moving from v1 to v2 with renamed field
```json
{
  "eventVersion": "2.0",
  "customerDetails": {   // RENAMED from "customer"
    "fullName": "John Doe",
    "emailAddress": "john.doe@example.com"  // RENAMED from "email"
  }
}
```

---

## Kafka Configuration

### Producer Configuration (Customer Service)

```properties
spring.kafka.producer.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.max.in.flight.requests.per.connection=5
spring.kafka.producer.compression-type=snappy
```

### Consumer Configuration (Order Service)

```properties
spring.kafka.consumer.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=order-service-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.consumer.properties.isolation.level=read_committed
spring.kafka.listener.ack-mode=manual
```

---

## Monitoring & Alerting

### Metrics to Track

1. **Producer Metrics**:
   - `orders.created.messages.sent.total`: Total messages published
   - `orders.created.messages.failed.total`: Failed publish attempts
   - `orders.created.publish.latency`: Time to publish (p50, p95, p99)

2. **Consumer Metrics**:
   - `orders.created.messages.consumed.total`: Total messages consumed
   - `orders.created.messages.processed.success.total`: Successfully processed
   - `orders.created.messages.processed.failed.total`: Processing failures (→ DLQ)
   - `orders.created.processing.latency`: Time to process (p50, p95, p99)
   - `orders.created.consumer.lag`: Consumer group lag (messages behind)

3. **Business Metrics**:
   - `orders.created.events.per.minute`: Order creation rate
   - `payments.completed.success.rate`: Payment success percentage
   - `payments.completed.failure.rate`: Payment failure percentage

### Alerts

- **Critical**: Consumer lag > 1000 messages (data processing delay)
- **Critical**: Payment success rate < 95% (payment service issues)
- **Warning**: Message publish failures > 10/minute (Kafka connectivity)
- **Warning**: DLQ message count > 0 (malformed events or processing errors)

---

## Testing Strategy

### Contract Tests (Producer)

```java
@Test
public void orderCreatedEvent_shouldMatchSchema() {
    OrderCreatedEvent event = createTestEvent();
    
    String json = objectMapper.writeValueAsString(event);
    
    // Validate against JSON Schema
    assertThat(json).matchesJsonSchema("kafka-events/order-created-v1.json");
    
    // Validate required fields
    assertThat(event.getEventId()).isNotNull();
    assertThat(event.getOrderNumber()).matches("^ORD-\\d{8}-\\d{3}$");
    assertThat(event.getItems()).isNotEmpty();
}
```

### Contract Tests (Consumer)

```java
@Test
public void orderCreatedListener_shouldProcessValidEvent() {
    String eventJson = loadTestResource("order-created-valid.json");
    
    ConsumerRecord<String, String> record = new ConsumerRecord<>(
        "orders.created", 0, 0, "order-123", eventJson
    );
    
    listener.onMessage(record);
    
    // Verify order created in database
    Order order = orderRepository.findByOrderNumber("ORD-20250930-001");
    assertThat(order).isNotNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
}
```

### Integration Tests (End-to-End)

```java
@SpringBootTest
@EmbeddedKafka(topics = {"orders.created", "payments.completed"})
public class CheckoutFlowIntegrationTest {
    
    @Test
    public void checkout_shouldPublishOrderCreatedEvent() {
        // Arrange: Create cart with items
        Cart cart = createTestCart();
        
        // Act: Submit checkout
        CheckoutResponse response = checkoutService.checkout(cart, customerInfo);
        
        // Assert: Verify event published
        ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        
        OrderCreatedEvent event = parseEvent(records.iterator().next().value());
        assertThat(event.getOrderNumber()).isEqualTo(response.getOrderNumber());
    }
}
```

---

## Event Schemas Complete

**Events Defined**: 2 domain events  
**Topics Defined**: 2 Kafka topics  
**Configuration**: Producer and consumer settings documented  
**Ready for**: Quickstart guide and implementation tasks

