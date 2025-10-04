# Data Model: E-Commerce Platform

**Feature**: E-Commerce Platform with Catalog, Shopping Cart, and Order Management  
**Date**: 2025-09-30  
**Status**: Phase 1 - Design Complete

## Overview

This document defines the data model for the e-commerce platform, including entities, relationships, validation rules, and state transitions. The model is split across two microservices, each with its own database schema.

---

## Service Boundaries

### Customer-Facing Service Database
Entities: `Category`, `Product`, `Cart`, `CartItem`

### Order Management Service Database
Entities: `Order`, `OrderItem`, `CustomerInfo`, `PaymentTransaction`

---

## Entity Definitions

### 1. Category (Customer-Facing Service)

**Purpose**: Organize products into browsable categories

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| name | String(100) | NOT NULL, UNIQUE | Category name (e.g., "Electronics") |
| description | Text | NULLABLE | Category description |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |

**Relationships**:
- `products`: One-to-Many → Product (one category has many products)

**Validation Rules**:
- `name`: Required, 1-100 characters, unique
- `description`: Optional, max 1000 characters

**Indexes**:
- `idx_category_name`: UNIQUE index on `name`

---

### 2. Product (Customer-Facing Service)

**Purpose**: Represents catalog items available for purchase

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| sku | String(50) | NOT NULL, UNIQUE | Stock Keeping Unit (unique product code) |
| name | String(200) | NOT NULL | Product name |
| description | Text | NULLABLE | Detailed product description |
| price | Decimal(10,2) | NOT NULL, CHECK > 0 | Price in USD (positive only) |
| inventoryQuantity | Integer | NOT NULL, CHECK >= 0 | Available inventory count |
| categoryId | UUID | FK, NOT NULL | Foreign key to Category |
| isActive | Boolean | NOT NULL, DEFAULT TRUE | Product availability flag |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |
| version | Long | NOT NULL, DEFAULT 0 | Optimistic locking version |

**Relationships**:
- `category`: Many-to-One → Category (many products belong to one category)
- `cartItems`: One-to-Many → CartItem (product can appear in multiple carts)

**Validation Rules**:
- `sku`: Required, 1-50 characters, unique, alphanumeric + hyphens
- `name`: Required, 1-200 characters
- `description`: Optional, max 5000 characters
- `price`: Required, positive decimal (2 decimal places)
- `inventoryQuantity`: Required, non-negative integer
- `categoryId`: Required, must reference existing category

**Business Logic**:
- `isInStock()`: Returns `inventoryQuantity > 0`
- `decrementInventory(int quantity)`: Decrements inventory atomically (optimistic locking)
- `canFulfillOrder(int quantity)`: Checks if sufficient inventory available

**Indexes**:
- `idx_product_sku`: UNIQUE index on `sku`
- `idx_product_category`: Index on `categoryId` (for category browsing)
- `idx_product_active`: Index on `isActive` (filter active products)

**State Transitions**:
- ACTIVE (isActive=true) ↔ DISCONTINUED (isActive=false)

---

### 3. Cart (Customer-Facing Service)

**Purpose**: Temporary shopping cart for guest checkout

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| sessionId | String(100) | NOT NULL, UNIQUE | Session identifier (guest checkout) |
| subtotal | Decimal(10,2) | NOT NULL, DEFAULT 0.00 | Calculated cart subtotal |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |
| expiresAt | Timestamp | NOT NULL | Expiration timestamp (session TTL) |

**Relationships**:
- `items`: One-to-Many → CartItem (cart contains multiple items)

**Validation Rules**:
- `sessionId`: Required, unique, 1-100 characters
- `subtotal`: Calculated field (sum of all CartItem subtotals)

**Business Logic**:
- `addItem(Product product, int quantity)`: Adds/updates item in cart
- `removeItem(UUID cartItemId)`: Removes item from cart
- `updateItemQuantity(UUID cartItemId, int quantity)`: Updates item quantity
- `calculateSubtotal()`: Recalculates subtotal from items
- `clear()`: Removes all items (after checkout)

**Storage Strategy**:
- **Redis**: Primary storage (in-memory, fast access, TTL-based expiration)
- **PostgreSQL**: Secondary storage (analytics, abandoned cart recovery)

**Indexes**:
- `idx_cart_session`: UNIQUE index on `sessionId`
- `idx_cart_expires`: Index on `expiresAt` (cleanup expired carts)

---

### 4. CartItem (Customer-Facing Service)

**Purpose**: Individual product entry in a shopping cart

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| cartId | UUID | FK, NOT NULL | Foreign key to Cart |
| productId | UUID | FK, NOT NULL | Foreign key to Product |
| quantity | Integer | NOT NULL, CHECK > 0 | Item quantity (positive only) |
| priceSnapshot | Decimal(10,2) | NOT NULL | Product price at time of adding |
| subtotal | Decimal(10,2) | NOT NULL | Calculated (quantity × priceSnapshot) |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |

**Relationships**:
- `cart`: Many-to-One → Cart (many items belong to one cart)
- `product`: Many-to-One → Product (reference to product)

**Validation Rules**:
- `cartId`: Required, must reference existing cart
- `productId`: Required, must reference existing product
- `quantity`: Required, positive integer
- `priceSnapshot`: Required, positive decimal (captured at time of adding)
- `subtotal`: Calculated field (quantity × priceSnapshot)

**Business Logic**:
- `calculateSubtotal()`: Returns `quantity * priceSnapshot`
- Price snapshot ensures price changes don't affect items already in cart

**Indexes**:
- `idx_cartitem_cart`: Index on `cartId`
- `idx_cartitem_product`: Index on `productId`
- `uk_cartitem_cart_product`: UNIQUE constraint on (cartId, productId) - one entry per product per cart

---

### 5. Order (Order Management Service)

**Purpose**: Represents a customer's purchase request

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| orderNumber | String(20) | NOT NULL, UNIQUE | Human-readable order number (e.g., ORD-20250930-001) |
| customerName | String(200) | NOT NULL | Customer full name |
| customerEmail | String(100) | NOT NULL | Customer email |
| customerPhone | String(20) | NOT NULL | Customer phone number |
| shippingAddress | JSON | NOT NULL | Shipping address (street, city, state, zip, country) |
| subtotal | Decimal(10,2) | NOT NULL | Order subtotal (sum of item subtotals) |
| status | Enum | NOT NULL | Order status (see state diagram) |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |
| completedAt | Timestamp | NULLABLE | Completion timestamp (when fulfilled) |

**Relationships**:
- `items`: One-to-Many → OrderItem (order contains multiple items)
- `paymentTransaction`: One-to-One → PaymentTransaction (order has one payment)

**Validation Rules**:
- `orderNumber`: Required, unique, format ORD-YYYYMMDD-NNN
- `customerName`: Required, 1-200 characters
- `customerEmail`: Required, valid email format, max 100 characters
- `customerPhone`: Required, valid phone format, max 20 characters
- `shippingAddress`: Required JSON with fields: street, city, state, postalCode, country
- `subtotal`: Required, positive decimal

**Status Enum Values**:
- `PENDING`: Order created, awaiting payment processing
- `PROCESSING`: Payment in progress
- `PAID`: Payment successful, ready for fulfillment
- `FULFILLED`: Order shipped/delivered
- `CANCELLED`: Order cancelled
- `FAILED`: Payment failed

**State Transitions**:
```
PENDING → PROCESSING → PAID → FULFILLED
   ↓          ↓
CANCELLED  FAILED
```

**Business Logic**:
- `canBeCancelled()`: Returns true if status is PENDING or PROCESSING
- `markAsPaid()`: Transitions status from PROCESSING to PAID
- `markAsFailed()`: Transitions status from PROCESSING to FAILED

**Indexes**:
- `idx_order_number`: UNIQUE index on `orderNumber`
- `idx_order_status`: Index on `status` (filter by status)
- `idx_order_created`: Index on `createdAt` (date range queries)
- `idx_order_email`: Index on `customerEmail` (lookup by email)

**Denormalization Note**:
- Customer info (name, email, phone, address) is denormalized into Order table
- No foreign key to separate Customer table (guest checkout model)
- Historical record: customer info captured at time of order

---

### 6. OrderItem (Order Management Service)

**Purpose**: Product entry within an order (immutable historical record)

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| orderId | UUID | FK, NOT NULL | Foreign key to Order |
| productId | UUID | NOT NULL | Product ID (reference, no FK) |
| productSku | String(50) | NOT NULL | Product SKU snapshot |
| productName | String(200) | NOT NULL | Product name snapshot |
| quantity | Integer | NOT NULL, CHECK > 0 | Item quantity |
| priceSnapshot | Decimal(10,2) | NOT NULL | Product price at order time |
| subtotal | Decimal(10,2) | NOT NULL | Calculated (quantity × priceSnapshot) |
| createdAt | Timestamp | NOT NULL | Creation timestamp |

**Relationships**:
- `order`: Many-to-One → Order (many items belong to one order)
- No direct FK to Product (denormalized for historical integrity)

**Validation Rules**:
- `orderId`: Required, must reference existing order
- `productId`: Required (stored as UUID, not enforced as FK)
- `productSku`: Required, 1-50 characters
- `productName`: Required, 1-200 characters
- `quantity`: Required, positive integer
- `priceSnapshot`: Required, positive decimal
- `subtotal`: Calculated field (quantity × priceSnapshot)

**Design Rationale**:
- **Immutable**: Once order is created, items never change (historical record)
- **Denormalized**: Product details (SKU, name, price) captured at order time
- **No FK to Product**: Product may be deleted/modified, but order item remains unchanged

**Indexes**:
- `idx_orderitem_order`: Index on `orderId`
- `idx_orderitem_product`: Index on `productId` (analytics)

---

### 7. PaymentTransaction (Order Management Service)

**Purpose**: Tracks payment processing attempts and results

**Fields**:
| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique identifier |
| orderId | UUID | FK, NOT NULL, UNIQUE | Foreign key to Order (one payment per order) |
| amount | Decimal(10,2) | NOT NULL | Payment amount (matches order subtotal) |
| status | Enum | NOT NULL | Payment status (PENDING, SUCCESS, FAILED) |
| paymentMethod | String(50) | NOT NULL | Payment method (MOCK, STRIPE, etc.) |
| externalTransactionId | String(100) | NULLABLE | External payment provider transaction ID |
| failureReason | Text | NULLABLE | Failure reason (if status=FAILED) |
| attemptCount | Integer | NOT NULL, DEFAULT 1 | Number of payment attempts |
| createdAt | Timestamp | NOT NULL | Creation timestamp |
| updatedAt | Timestamp | NOT NULL | Last update timestamp |
| completedAt | Timestamp | NULLABLE | Completion timestamp (success or final failure) |

**Relationships**:
- `order`: One-to-One → Order (one payment per order)

**Validation Rules**:
- `orderId`: Required, unique (one payment per order)
- `amount`: Required, positive decimal, must match order subtotal
- `status`: Required, one of [PENDING, SUCCESS, FAILED]
- `paymentMethod`: Required, one of [MOCK, STRIPE, PAYPAL, etc.]
- `attemptCount`: Required, positive integer

**Status Enum Values**:
- `PENDING`: Payment initiated, awaiting result
- `SUCCESS`: Payment successful
- `FAILED`: Payment failed (final state)

**State Transitions**:
```
PENDING → SUCCESS
   ↓
FAILED
```

**Business Logic**:
- `markAsSuccessful(String externalId)`: Transitions to SUCCESS, records external ID
- `markAsFailed(String reason)`: Transitions to FAILED, records failure reason
- `incrementAttemptCount()`: Increments attempt count (for retries)

**Indexes**:
- `idx_payment_order`: UNIQUE index on `orderId`
- `idx_payment_status`: Index on `status`
- `idx_payment_external`: Index on `externalTransactionId` (Stripe webhook lookups)

---

## Cross-Service Data Flow

### Checkout Flow (Customer → Order Service)

1. **Customer Service**: Cart data assembled into Order payload
2. **Kafka Event**: `OrderCreatedEvent` published to `orders.created` topic
3. **Order Service**: Consumes event, creates Order + OrderItem + PaymentTransaction

**Event Schema: OrderCreatedEvent**
```json
{
  "eventId": "uuid",
  "eventType": "ORDER_CREATED",
  "timestamp": "2025-09-30T12:00:00Z",
  "orderId": "uuid",
  "orderNumber": "ORD-20250930-001",
  "customer": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "shippingAddress": {
      "street": "123 Main St",
      "city": "San Francisco",
      "state": "CA",
      "postalCode": "94105",
      "country": "USA"
    }
  },
  "items": [
    {
      "productId": "uuid",
      "productSku": "SKU-123",
      "productName": "Widget",
      "quantity": 2,
      "priceSnapshot": 29.99,
      "subtotal": 59.98
    }
  ],
  "subtotal": 59.98
}
```

### Payment Processing Flow (Order Service Internal)

1. **Order Service**: Receives OrderCreatedEvent
2. **Create Entities**: Order, OrderItem(s), PaymentTransaction (status=PENDING)
3. **Process Payment**: PaymentService.processPayment() via circuit breaker
4. **Update Status**: PaymentTransaction.status → SUCCESS or FAILED
5. **Publish Event**: `PaymentCompletedEvent` to `payments.completed` topic

**Event Schema: PaymentCompletedEvent**
```json
{
  "eventId": "uuid",
  "eventType": "PAYMENT_COMPLETED",
  "timestamp": "2025-09-30T12:00:05Z",
  "orderId": "uuid",
  "orderNumber": "ORD-20250930-001",
  "paymentTransactionId": "uuid",
  "status": "SUCCESS",
  "amount": 59.98,
  "externalTransactionId": "stripe_tx_123",
  "failureReason": null
}
```

---

## Database Schemas

### Customer-Facing Service Schema (PostgreSQL)

```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL CHECK (price > 0),
    inventory_quantity INTEGER NOT NULL CHECK (inventory_quantity >= 0),
    category_id UUID NOT NULL REFERENCES categories(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) NOT NULL UNIQUE,
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_snapshot DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id)
);

-- Indexes
CREATE INDEX idx_product_category ON products(category_id);
CREATE INDEX idx_product_active ON products(is_active);
CREATE INDEX idx_cart_expires ON carts(expires_at);
CREATE INDEX idx_cartitem_cart ON cart_items(cart_id);
CREATE INDEX idx_cartitem_product ON cart_items(product_id);
```

### Order Management Service Schema (PostgreSQL)

```sql
CREATE TYPE order_status AS ENUM ('PENDING', 'PROCESSING', 'PAID', 'FULFILLED', 'CANCELLED', 'FAILED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(20) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    customer_email VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    shipping_address JSONB NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    status order_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_sku VARCHAR(50) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_snapshot DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    amount DECIMAL(10,2) NOT NULL,
    status payment_status NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NOT NULL,
    external_transaction_id VARCHAR(100),
    failure_reason TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_order_status ON orders(status);
CREATE INDEX idx_order_created ON orders(created_at);
CREATE INDEX idx_order_email ON orders(customer_email);
CREATE INDEX idx_orderitem_order ON order_items(order_id);
CREATE INDEX idx_orderitem_product ON order_items(product_id);
CREATE INDEX idx_payment_status ON payment_transactions(status);
CREATE INDEX idx_payment_external ON payment_transactions(external_transaction_id);
```

---

## Data Integrity & Constraints

### Referential Integrity
- **Customer Service**: Products → Categories (ON DELETE RESTRICT to prevent orphaned products)
- **Customer Service**: CartItems → Products (ON DELETE RESTRICT to prevent invalid cart items)
- **Order Service**: OrderItems → Orders (ON DELETE CASCADE, order deletion removes items)
- **Order Service**: PaymentTransactions → Orders (ON DELETE CASCADE)

### Concurrency Control
- **Optimistic Locking**: Product entity uses `@Version` for inventory updates (prevent lost updates)
- **Transaction Isolation**: SERIALIZABLE for checkout (prevent double-spending of inventory)

### Data Validation
- **Bean Validation (JSR-380)**: All DTOs annotated with `@Valid`, `@NotNull`, `@Min`, `@Max`, `@Pattern`
- **Database Constraints**: CHECK constraints for positive prices, quantities, amounts

---

## Archival & Retention

### Cart Data
- **Redis TTL**: 30 minutes (automatic expiration)
- **PostgreSQL Cleanup**: Daily job deletes carts older than 7 days (analytics retention)

### Order Data
- **Retention**: Indefinite (regulatory compliance for financial records)
- **Archival**: Orders older than 2 years moved to cold storage (future enhancement)

### Event Data
- **Kafka Retention**: 7 days (replay capability for debugging)
- **Event Log Table**: Optional persistent event log for audit trail (future enhancement)

---

## Migration Strategy

### Initial Schema Creation
- Flyway migrations in `src/main/resources/db/migration/`
- Versioned SQL files: `V1__create_tables.sql`, `V2__add_indexes.sql`, etc.

### Schema Evolution
- Backward-compatible changes: Add columns with DEFAULT values, add indexes
- Breaking changes: Require new API version, dual-write pattern during migration

---

## Performance Considerations

### Read Optimization
- **Indexes**: All foreign keys, status fields, date range queries
- **Redis Cache**: Product catalog (TTL 5 min), cart data (TTL 30 min)
- **Read Replicas**: PostgreSQL read replicas for catalog queries (future)

### Write Optimization
- **Batch Inserts**: OrderItem bulk insert during order creation
- **Async Processing**: Order processing via Kafka (no blocking on checkout)
- **Connection Pooling**: HikariCP (default 10 connections per service)

---

## Data Model Complete

**Entities Defined**: 7 entities across 2 services  
**Relationships Mapped**: 9 relationships (foreign keys and logical references)  
**Validation Rules**: Comprehensive constraints and business logic  
**Ready for**: API contract generation (Phase 1 continued)

