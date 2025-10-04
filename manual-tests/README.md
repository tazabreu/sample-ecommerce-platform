# E-Commerce Platform Interactive Testing Tool

A beautiful, interactive CLI application for comprehensive testing of the e-commerce platform with automated Docker container management, API testing, database verification, and Kafka message inspection.

## ✨ Features

### 🐳 **Docker Container Management**
- Automatically starts all infrastructure and services
- Option to keep containers running or clean up after tests
- Detached mode for persistent development environment
- Health checks and service readiness detection

### 🎮 **Interactive Testing Mode**
- Beautiful menu-driven interface
- Test individual endpoints with custom data
- Pre-configured test scenarios with meaningful defaults
- Real-time response viewing with syntax highlighting

### ✅ **Happy Path Testing**
- Complete end-to-end customer journey
- Automated flow from auth to order completion
- Database and Kafka verification
- Beautiful progress indicators and tables

### ❌ **Error Scenario Testing**
- **400 Bad Request**: Validation errors, invalid data formats
- **404 Not Found**: Non-existent resources
- **Cart & Checkout Errors**: Empty carts, missing data
- All error scenarios test proper error handling

### 🔍 **Deep Inspection**
- PostgreSQL database state verification
- Redis cache inspection
- Kafka topic consumption and message viewing
- API response validation with JSON formatting

### 🎨 **Beautiful Terminal UI**
- Color-coded output with emoji indicators
- Progress bars and spinners for long operations
- Interactive tables for data display
- Syntax-highlighted JSON responses
- Panel-based layouts

## 📋 Prerequisites

- **Python 3.10+**
- **Docker & Docker Compose** (for container management)
- **8GB RAM** (for running all containers)

## 🚀 Installation

1. **Navigate to the manual-tests directory:**
   ```bash
   cd manual-tests
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

## 📖 Usage

### Quick Start (Recommended)

Start the platform and run interactive tests:

```bash
python test_runner.py start --interactive
```

This will:
1. 🐳 Start all Docker containers (infrastructure + services)
2. ⏳ Wait for services to be healthy
3. 🎮 Launch interactive testing menu
4. 🧹 Clean up containers when you exit

### Interactive Mode (Containers Already Running)

If containers are already running:

```bash
python test_runner.py interactive
```

### Detached Mode (Keep Containers Running)

Start containers and exit (useful for development):

```bash
python test_runner.py start --detach
```

Later, connect and test:

```bash
python test_runner.py interactive
```

Stop containers when done:

```bash
python test_runner.py stop
```

### Keep Containers Alive After Testing

Run tests but keep containers running:

```bash
python test_runner.py start --keep-alive
```

## 🎮 Interactive Menu Options

When you run in interactive mode, you'll see:

```
╔══════════════════════════════════════════════════════════╗
║           Interactive Testing Menu                        ║
╚══════════════════════════════════════════════════════════╝

┌─────┬──────────────────────────────────┬──────────────────────────────┐
│ Opt │ Test Scenario                    │ Description                  │
├─────┼──────────────────────────────────┼──────────────────────────────┤
│ 1   │ Happy Path - Complete Flow       │ Run successful end-to-end    │
│ 2   │ Test Individual Endpoints        │ Test specific endpoints      │
│ 3   │ Error Scenarios - 400 Bad Request│ Test validation errors       │
│ 4   │ Error Scenarios - 404 Not Found  │ Test resource not found      │
│ 5   │ Error Scenarios - Cart & Checkout│ Test cart/checkout errors    │
│ 6   │ Database Verification            │ Inspect database state       │
│ 7   │ Kafka Verification               │ Check event messages         │
│ 8   │ Health Checks                    │ Verify service health        │
│ q   │ Quit                             │ Exit interactive mode        │
└─────┴──────────────────────────────────┴──────────────────────────────┘
```

## 🧪 Test Scenarios

### 1. Happy Path - Complete Flow

Tests the entire customer journey:

1. ✅ Health checks for all services
2. 🔐 Manager authentication
3. 📚 Category & Product creation (2 products)
4. 🛒 Add items to cart
5. 💳 Checkout with valid customer info
6. 📦 Order processing verification
7. 🗄️ Database state verification
8. 📨 Kafka event verification

**Default Test Data:**
- **Category**: "Electronics" with description
- **Product 1**: "Premium Wireless Headphones" - $149.99 (qty: 2)
- **Product 2**: "Protective Phone Case" - $29.99 (qty: 1)
- **Customer**: John Doe, john.doe@example.com
- **Address**: 123 Main Street, San Francisco, CA 94105

### 2. Test Individual Endpoints

Interactive testing of specific endpoints:

- `GET /api/v1/categories` - List categories
- `POST /api/v1/categories` - Create category (customizable)
- `GET /api/v1/products` - List products
- `POST /api/v1/products` - Create product (customizable)
- `GET /api/v1/carts/{sessionId}` - Get cart
- `POST /api/v1/carts/{sessionId}/items` - Add to cart
- `POST /api/v1/checkout` - Checkout
- `GET /api/v1/orders/{orderNumber}` - Get order

You can provide custom values or use intelligent defaults.

### 3. Error Scenarios - 400 Bad Request

Tests validation and error handling:

- ❌ Create category with missing required field (name)
- ❌ Create product with negative price
- ❌ Checkout with invalid email format
- ❌ Add to cart with zero/negative quantity
- ❌ Checkout with incomplete shipping address

**Expected**: All should return `400 Bad Request` with error details

### 4. Error Scenarios - 404 Not Found

Tests non-existent resource handling:

- 🔍 Get non-existent category by ID
- 🔍 Get non-existent product by ID
- 🔍 Get non-existent order by number
- 🔍 Update non-existent category

**Expected**: All should return `404 Not Found`

### 5. Error Scenarios - Cart & Checkout

Tests cart and checkout validation:

- 🛒 Checkout with empty cart
- 🛒 Add non-existent product to cart
- 🛒 Checkout with incomplete address

**Expected**: Appropriate error responses (400/404)

### 6. Database Verification

Inspects database state:

- **Customer DB**: Count categories, products, carts, cart_items
- **Order DB**: Count orders, order_items, payment_transactions, processed_events
- **Redis**: List cache keys and types
- Shows specific order details if order number is available

### 7. Kafka Verification

Checks event messages:

- Reads from `orders.created` topic
- Reads from `payments.completed` topic
- Shows last 3 messages with event type, order number, timestamp

### 8. Health Checks

Verifies service health:

- Customer Service health endpoint
- Order Service health endpoint
- Component status (db, redis, kafka)

## 🎨 Output Examples

### Starting Containers

```
╔══════════════════════════════════════════════════════════╗
║          🐳 Starting Docker Containers                    ║
║          Launching infrastructure and services            ║
╚══════════════════════════════════════════════════════════╝

🔍 Checking Docker daemon...
✅ Docker daemon is running
⏳ Starting containers with docker-compose...
✅ Containers started successfully
⏳ Waiting for services to be healthy...
✅ Customer Service is healthy
✅ Order Service is healthy
```

### Happy Path Flow

```
╔══════════════════════════════════════════════════════════╗
║          ✨ Happy Path - Complete Flow                    ║
╚══════════════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════════════╗
║          🔐 Authentication                                ║
╚══════════════════════════════════════════════════════════╝

🔍 Logging in as manager...
  └─ Token obtained: ✓
  └─ Token type: Bearer
  └─ Expires in: 900s

╔══════════════════════════════════════════════════════════╗
║          📚 Catalog Management                            ║
╚══════════════════════════════════════════════════════════╝

🔍 Creating category 'Electronics'...
  └─ Category ID: 550e8400-e29b-41d4-a716-446655440000
  └─ Category Name: Electronics

🔍 Creating product 'Premium Wireless Headphones'...
  └─ Product ID: 6ba7b810-9dad-11d1-80b4-00c04fd430c8
  └─ SKU: SKU-HEADPHONE-A3F5D2
  └─ Price: $149.99
```

### Error Scenario Testing

```
╔══════════════════════════════════════════════════════════╗
║          ❌ Error Scenarios - 400 Bad Request             ║
║          Testing validation and error handling           ║
╚══════════════════════════════════════════════════════════╝

Testing invalid requests...

🔍 Test: Create category with missing name
  Status Code: 400
╭─── Response ────────────────────────────────────────╮
│ {                                                   │
│   "timestamp": "2025-10-04T16:30:00.000Z",         │
│   "status": 400,                                    │
│   "error": "Bad Request",                           │
│   "message": "Validation failed",                   │
│   "errors": [                                       │
│     {                                               │
│       "field": "name",                              │
│       "message": "must not be blank"                │
│     }                                               │
│   ]                                                 │
│ }                                                   │
╰─────────────────────────────────────────────────────╯
```

### Database Verification

```
╔══════════════════════════════════════════════════════════╗
║          🗄️ Database Verification                        ║
╚══════════════════════════════════════════════════════════╝

🔍 Checking customer database...
  └─ Categories: 3
  └─ Products: 8
  └─ Carts: 5
  └─ Cart Items: 12

🔍 Checking order database...
  └─ Orders: 2
  └─ Order Items: 4
  └─ Payment Transactions: 2
  └─ Processed Events: 4

  └─ Order in DB: ✓
    └─ Status: PROCESSING
    └─ Subtotal: $329.97
    └─ Customer: john.doe@example.com
```

## 🎯 Command Reference

### Start with Interactive Testing
```bash
python test_runner.py start --interactive
```

### Start and Keep Containers Running
```bash
python test_runner.py start --keep-alive
```

### Start in Detached Mode
```bash
python test_runner.py start --detach
```

### Interactive Mode (Containers Running)
```bash
python test_runner.py interactive
```

### Run Automated Full Flow
```bash
python test_runner.py full-flow
```

### Individual Test Commands
```bash
python test_runner.py test-health
python test_runner.py test-auth
python test_runner.py test-catalog-management
python test_runner.py test-cart-operations
python test_runner.py test-checkout
python test_runner.py test-order-processing
python test_runner.py verify-database
python test_runner.py verify-kafka
```

### Stop Containers
```bash
python test_runner.py stop
```

## 🔧 Configuration

The tool connects to services with these defaults (configured for Docker):

- **Customer Service**: http://localhost:8080
- **Order Service**: http://localhost:8081
- **PostgreSQL (Customer)**: localhost:5432
- **PostgreSQL (Order)**: localhost:5433
- **Redis**: localhost:6379
- **Kafka**: localhost:9092

## 🐛 Troubleshooting

### Docker Daemon Not Running
```
❌ Docker daemon is not running. Please start Docker.
```
**Solution**: Start Docker Desktop or Docker daemon

### Services Not Healthy
If services don't become healthy within 120 seconds:
- Check Docker logs: `docker-compose -p ecommerce-platform logs`
- Verify resource allocation (need 8GB RAM)
- Check port conflicts (8080, 8081, 5432, 5433, 6379, 9092)

### Connection Refused
If tests fail with connection errors:
- Ensure containers are running: `python test_runner.py start --detach`
- Wait a bit longer for services to fully start
- Check health endpoints manually: `curl http://localhost:8080/actuator/health`

### Authentication Failed
If auth tests fail:
- Check customer service logs
- Verify dev profile is active (mock auth enabled)

### Keyboard Interrupt (Ctrl+C)
The tool handles Ctrl+C gracefully:
- Displays cleanup message
- Stops containers if `--keep-alive` not used
- Exits cleanly

## 📊 Test Data Management

The tool maintains test context across operations:

- **Session ID**: Generated per test run (e.g., `test-session-{uuid}`)
- **Manager Token**: Obtained once, reused for authenticated requests
- **Category ID**: Saved after creation, used for product creation
- **Product IDs**: List of created products for cart operations
- **Order Number**: Saved after checkout for order verification

## 🎓 Best Practices

1. **Start Fresh**: Use `--interactive` for guided testing
2. **Keep Alive**: Use `--keep-alive` when debugging
3. **Detached Mode**: Use `--detach` for development environment
4. **Error Testing**: Run error scenarios to verify proper handling
5. **Verify State**: Always check database and Kafka after operations

## 📝 Notes

- The tool automatically manages Docker containers lifecycle
- All test data uses meaningful defaults
- Interactive mode allows custom values
- Ctrl+C is handled gracefully with cleanup
- Containers can be kept running between test sessions

## 🔗 Related Documentation

- Main README: `../README.md`
- API Contracts: `../specs/001-e-commerce-platform/contracts/`
- Quick Start Guide: `../specs/001-e-commerce-platform/quickstart.md`

## 🎉 Have Fun Testing!

The interactive test runner makes it easy and enjoyable to test your e-commerce platform. Happy testing! 🚀
