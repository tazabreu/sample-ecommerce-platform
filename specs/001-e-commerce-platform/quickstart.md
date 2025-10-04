# Quickstart Guide: E-Commerce Platform

**Feature**: E-Commerce Platform with Catalog, Shopping Cart, and Order Management  
**Date**: 2025-09-30  
**Version**: 1.0.0

## Overview

This quickstart guide walks you through setting up, running, and testing the e-commerce platform locally. It covers the complete end-to-end flow from catalog management to order processing.

---

## Prerequisites

- **Java**: JDK 21 (OpenJDK or Oracle)
- **Maven**: 3.8+
- **Docker**: 20.10+ with Docker Compose
- **curl** or **Postman** for API testing
- **Git**: For cloning the repository

### Verify Prerequisites

```bash
java --version        # Should show Java 21
mvn --version         # Should show Maven 3.8+
docker --version      # Should show Docker 20.10+
docker-compose --version
```

---

## Quick Start (5 Minutes)

### 1. Clone and Build

```bash
# Clone repository
git clone https://github.com/your-org/ecommerce-platform.git
cd ecommerce-platform

# Build all services
mvn clean install -DskipTests
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL, Redis, Kafka (Redpanda)
cd infrastructure
docker-compose up -d

# Verify services are running
docker-compose ps
```

**Expected Output**:
```
NAME                   STATUS              PORTS
postgres               Up                  5432
redis                  Up                  6379
redpanda               Up                  9092, 18081, 18082
```

### 3. Start Application Services

**Terminal 1 - Customer Service**:
```bash
cd customer-facing-service
mvn spring-boot:run
```

**Terminal 2 - Order Service**:
```bash
cd order-management-service
mvn spring-boot:run
```

**Wait for startup** (look for "Started Application in X seconds"):
- Customer Service: http://localhost:8080
- Order Service: http://localhost:8081

### 4. Verify Health

```bash
# Customer Service health check
curl http://localhost:8080/actuator/health

# Order Service health check
curl http://localhost:8081/actuator/health
```

**Expected Response**:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

---

## Complete User Journey

### Scenario: Manager Creates Product, Customer Purchases

#### Step 1: Obtain Manager JWT Token

For local development, use the mock authentication endpoint:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "manager",
    "password": "manager123"
  }'
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Save the token**:
```bash
export MANAGER_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### Step 2: Create Product Category

```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Electronics",
    "description": "Electronic devices and accessories"
  }'
```

**Response**:
```json
{
  "id": "a8f5f167-024d-4a61-8f12-5e1b4e9c6f3a",
  "name": "Electronics",
  "description": "Electronic devices and accessories",
  "createdAt": "2025-09-30T12:00:00Z",
  "updatedAt": "2025-09-30T12:00:00Z"
}
```

**Save the category ID**:
```bash
export CATEGORY_ID="a8f5f167-024d-4a61-8f12-5e1b4e9c6f3a"
```

#### Step 3: Create Products

**Product 1: Wireless Headphones**
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"sku\": \"SKU-HEADPHONE-001\",
    \"name\": \"Premium Wireless Headphones\",
    \"description\": \"Noise-cancelling Bluetooth headphones with 30-hour battery life\",
    \"price\": 149.99,
    \"inventoryQuantity\": 50,
    \"categoryId\": \"$CATEGORY_ID\"
  }"
```

**Product 2: Smartphone Case**
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"sku\": \"SKU-CASE-042\",
    \"name\": \"Protective Phone Case\",
    \"description\": \"Shockproof case with military-grade protection\",
    \"price\": 29.99,
    \"inventoryQuantity\": 200,
    \"categoryId\": \"$CATEGORY_ID\"
  }"
```

**Save product IDs** from responses:
```bash
export PRODUCT1_ID="<headphone-id>"
export PRODUCT2_ID="<case-id>"
```

#### Step 4: Browse Products (Customer)

```bash
# List all products
curl http://localhost:8080/api/v1/products

# Get specific product
curl http://localhost:8080/api/v1/products/$PRODUCT1_ID

# Filter by category
curl "http://localhost:8080/api/v1/products?categoryId=$CATEGORY_ID"
```

#### Step 5: Add Items to Cart

```bash
# Generate session ID (simulate guest checkout)
export SESSION_ID="guest-session-$(date +%s)"

# Add headphones to cart
curl -X POST "http://localhost:8080/api/v1/carts/$SESSION_ID/items" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT1_ID\",
    \"quantity\": 2
  }"

# Add phone case to cart
curl -X POST "http://localhost:8080/api/v1/carts/$SESSION_ID/items" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT2_ID\",
    \"quantity\": 1
  }"
```

#### Step 6: View Cart

```bash
curl "http://localhost:8080/api/v1/carts/$SESSION_ID"
```

**Response**:
```json
{
  "id": "c1f2f267-135e-5b72-9g23-6f2c5f0d7g4b",
  "sessionId": "guest-session-1727701200",
  "items": [
    {
      "id": "item-1",
      "productId": "a8f5f167-024d-4a61-8f12-5e1b4e9c6f3a",
      "productSku": "SKU-HEADPHONE-001",
      "productName": "Premium Wireless Headphones",
      "quantity": 2,
      "priceSnapshot": 149.99,
      "subtotal": 299.98
    },
    {
      "id": "item-2",
      "productId": "b9f6f268-135e-5b72-9g23-6f2c5f0d7g4b",
      "productSku": "SKU-CASE-042",
      "productName": "Protective Phone Case",
      "quantity": 1,
      "priceSnapshot": 29.99,
      "subtotal": 29.99
    }
  ],
  "subtotal": 329.97,
  "createdAt": "2025-09-30T12:05:00Z",
  "updatedAt": "2025-09-30T12:06:30Z",
  "expiresAt": "2025-09-30T12:35:00Z"
}
```

#### Step 7: Update Cart (Optional)

```bash
# Update item quantity
CART_ITEM_ID="<item-1-id>"
curl -X PUT "http://localhost:8080/api/v1/carts/$SESSION_ID/items/$CART_ITEM_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 1
  }'

# Remove item from cart
curl -X DELETE "http://localhost:8080/api/v1/carts/$SESSION_ID/items/$CART_ITEM_ID"
```

#### Step 8: Checkout

```bash
curl -X POST http://localhost:8080/api/v1/checkout \
  -H "Content-Type: application/json" \
  -d "{
    \"sessionId\": \"$SESSION_ID\",
    \"customerInfo\": {
      \"name\": \"John Doe\",
      \"email\": \"john.doe@example.com\",
      \"phone\": \"+14155551234\",
      \"shippingAddress\": {
        \"street\": \"123 Main Street, Apt 4B\",
        \"city\": \"San Francisco\",
        \"state\": \"CA\",
        \"postalCode\": \"94105\",
        \"country\": \"USA\"
      }
    }
  }"
```

**Response**:
```json
{
  "orderNumber": "ORD-20250930-001",
  "status": "PENDING",
  "message": "Order submitted successfully. You will receive a confirmation email shortly."
}
```

**Save order number**:
```bash
export ORDER_NUMBER="ORD-20250930-001"
```

#### Step 9: Check Order Status

```bash
# Check order status (no authentication required for guest lookup)
curl "http://localhost:8081/api/v1/orders/$ORDER_NUMBER"
```

**Response** (after processing):
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "orderNumber": "ORD-20250930-001",
  "customerInfo": {
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
      "id": "item-1",
      "productId": "a8f5f167-024d-4a61-8f12-5e1b4e9c6f3a",
      "productSku": "SKU-HEADPHONE-001",
      "productName": "Premium Wireless Headphones",
      "quantity": 2,
      "priceSnapshot": 149.99,
      "subtotal": 299.98
    },
    {
      "id": "item-2",
      "productId": "b9f6f268-135e-5b72-9g23-6f2c5f0d7g4b",
      "productSku": "SKU-CASE-042",
      "productName": "Protective Phone Case",
      "quantity": 1,
      "priceSnapshot": 29.99,
      "subtotal": 29.99
    }
  ],
  "subtotal": 329.97,
  "status": "PAID",
  "paymentStatus": "SUCCESS",
  "createdAt": "2025-09-30T12:10:00Z",
  "updatedAt": "2025-09-30T12:10:05Z",
  "completedAt": null
}
```

**Status Progression**:
1. `PENDING` → Order created, waiting for payment processing
2. `PROCESSING` → Payment in progress
3. `PAID` → Payment successful, ready for fulfillment
4. `FULFILLED` → Order shipped/delivered (manual step)

---

## Testing Event-Driven Architecture

### Monitor Kafka Events

```bash
# Connect to Redpanda container
docker exec -it redpanda rpk topic consume orders.created --brokers localhost:9092

# In another terminal, perform checkout (Step 8 above)
# You should see OrderCreatedEvent printed in real-time
```

**Expected Event**:
```json
{
  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "eventType": "ORDER_CREATED",
  "eventVersion": "1.0",
  "timestamp": "2025-09-30T12:10:00.123Z",
  "orderId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "orderNumber": "ORD-20250930-001",
  "customer": { ... },
  "items": [ ... ],
  "subtotal": 329.97
}
```

### Verify Payment Processing

```bash
# Check order service logs for payment processing
docker logs order-management-service --tail 50 --follow

# Look for log entries:
# [INFO] OrderCreatedListener - Processing OrderCreatedEvent: ORD-20250930-001
# [INFO] PaymentService - Processing payment for order ORD-20250930-001
# [INFO] PaymentService - Payment successful: mock_tx_1727701200
# [INFO] PaymentCompletedListener - Payment completed for order ORD-20250930-001: SUCCESS
```

---

## Manager Operations

### View All Orders

```bash
curl "http://localhost:8081/api/v1/orders" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

### Filter Orders by Status

```bash
curl "http://localhost:8081/api/v1/orders?status=PAID" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

### Filter Orders by Date Range

```bash
curl "http://localhost:8081/api/v1/orders?startDate=2025-09-30T00:00:00Z&endDate=2025-09-30T23:59:59Z" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

### Mark Order as Fulfilled

```bash
curl -X POST "http://localhost:8081/api/v1/orders/$ORDER_NUMBER/fulfill" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trackingNumber": "TRACK-123456789",
    "carrier": "FedEx",
    "notes": "Package shipped via FedEx 2-day"
  }'
```

### Cancel Order

```bash
curl -X POST "http://localhost:8081/api/v1/orders/$ORDER_NUMBER/cancel" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Customer requested cancellation"
  }'
```

---

## Testing Resilience Patterns

### Test Circuit Breaker (Payment Service Down)

1. **Simulate payment service failure**:
   ```bash
   # In Order Service application.yml, set:
   payment.service.mock.failure-rate=1.0  # 100% failure rate
   ```

2. **Restart order service**

3. **Perform checkout** (Step 8 above)

4. **Check order status**:
   ```bash
   curl "http://localhost:8081/api/v1/orders/$ORDER_NUMBER"
   ```
   **Expected**: `status: FAILED`, `paymentStatus: FAILED`

5. **Verify circuit breaker**:
   ```bash
   curl "http://localhost:8081/actuator/health"
   ```
   **Expected**: Circuit breaker state shown in health response

### Test Kafka Consumer Retry

1. **Stop order service**:
   ```bash
   # Ctrl+C in Terminal 2
   ```

2. **Perform checkout** (OrderCreatedEvent published to Kafka)

3. **Restart order service** (consumer catches up):
   ```bash
   mvn spring-boot:run
   ```

4. **Verify order processed**:
   ```bash
   curl "http://localhost:8081/api/v1/orders/$ORDER_NUMBER"
   ```

---

## Observability

### Prometheus Metrics

```bash
# Customer Service metrics
curl http://localhost:8080/actuator/prometheus

# Order Service metrics
curl http://localhost:8081/actuator/prometheus
```

**Key Metrics**:
- `http_server_requests_seconds_*` - Request latency and count
- `jvm_memory_*` - JVM memory usage
- `kafka_consumer_*` - Kafka consumer lag and throughput
- `resilience4j_circuitbreaker_*` - Circuit breaker state

### View Metrics in Prometheus (Optional)

```bash
# Add Prometheus to docker-compose.yml
# Visit http://localhost:9090 to query metrics
```

### Structured Logging

```bash
# View JSON logs with correlation IDs
docker logs customer-facing-service --tail 100 | jq '.'

# Filter by correlation ID
docker logs customer-facing-service --tail 1000 | jq 'select(.correlationId=="req-abc123")'
```

---

## Cleanup

### Stop Services

```bash
# Stop application services (Ctrl+C in both terminals)

# Stop infrastructure
cd infrastructure
docker-compose down

# Remove volumes (data will be lost)
docker-compose down -v
```

---

## Troubleshooting

### Issue: Services fail to start

**Solution**: Check port availability
```bash
lsof -i :8080  # Customer Service
lsof -i :8081  # Order Service
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka
```

### Issue: Database connection errors

**Solution**: Verify PostgreSQL is running
```bash
docker-compose ps postgres
docker-compose logs postgres
```

### Issue: Kafka consumer lag

**Solution**: Check consumer group status
```bash
docker exec -it redpanda rpk group describe order-service-group
```

### Issue: Cart not found after creation

**Solution**: Check Redis connection
```bash
docker exec -it redis redis-cli PING
docker exec -it redis redis-cli KEYS "cart:*"
```

---

## Next Steps

1. **Run Integration Tests**:
   ```bash
   mvn verify
   ```

2. **Run Performance Tests** (future):
   ```bash
   cd performance-tests
   ./run-gatling-tests.sh
   ```

3. **Deploy to Cloud** (future):
   ```bash
   kubectl apply -f infrastructure/k8s/
   ```

4. **Integrate Stripe** (future):
   - Replace mock payment service with Stripe implementation
   - Configure Stripe API keys in environment variables

---

## API Documentation

### Swagger UI

- **Customer Service**: http://localhost:8080/swagger-ui.html
- **Order Service**: http://localhost:8081/swagger-ui.html

### OpenAPI Specs

- **Customer Service**: http://localhost:8080/v3/api-docs
- **Order Service**: http://localhost:8081/v3/api-docs

---

## Support

For issues or questions:
- **GitHub Issues**: https://github.com/your-org/ecommerce-platform/issues
- **Documentation**: See `/specs/001-e-commerce-platform/` for detailed specs
- **Team Chat**: #ecommerce-platform Slack channel

---

**Quickstart Complete**: System ready for development and testing!
