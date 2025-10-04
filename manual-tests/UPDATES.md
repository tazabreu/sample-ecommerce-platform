# Manual Tests - Updates Summary

## 🎉 What's New

The manual testing tool has been completely redesigned with Docker container management and an interactive testing interface.

## ✨ Major Features Added

### 1. 🐳 **Docker Container Management**

The test runner now manages the entire platform lifecycle:

```bash
# Start everything and test interactively
python3 test_runner.py start --interactive

# Start in detached mode (persistent)
python3 test_runner.py start --detach

# Stop everything
python3 test_runner.py stop
```

**Key Capabilities:**
- ✅ Automatically starts all containers (PostgreSQL, Redis, Kafka, Services)
- ✅ Builds services from Dockerfile if needed
- ✅ Waits for services to be healthy before testing
- ✅ Handles cleanup on exit (configurable)
- ✅ Graceful Ctrl+C handling
- ✅ Health check integration

**Modes:**
- **Interactive Mode**: Containers cleanup when you exit
- **Keep-Alive Mode** (`--keep-alive`): Containers stay running after tests
- **Detached Mode** (`--detach`): Start containers and exit immediately

### 2. 🎮 **Interactive Testing Menu**

Beautiful, menu-driven interface for testing:

```
Interactive Testing Menu
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1 - Happy Path - Complete Flow
2 - Test Individual Endpoints
3 - Error Scenarios - 400 Bad Request
4 - Error Scenarios - 404 Not Found
5 - Error Scenarios - Cart & Checkout
6 - Database Verification
7 - Kafka Verification
8 - Health Checks
q - Quit
```

**Features:**
- Navigate with number keys
- Continue testing or exit after each scenario
- Context preserved between tests (tokens, IDs, etc.)
- Smart defaults for all inputs

### 3. ❌ **Comprehensive Error Scenario Testing**

Test proper error handling with pre-configured scenarios:

#### **400 Bad Request Scenarios:**
- ❌ Create category with missing required field
- ❌ Create product with negative price
- ❌ Checkout with invalid email format
- ❌ Add to cart with zero quantity
- ❌ Checkout with incomplete address

#### **404 Not Found Scenarios:**
- 🔍 Get non-existent category
- 🔍 Get non-existent product
- 🔍 Get non-existent order
- 🔍 Update non-existent category

#### **Cart & Checkout Error Scenarios:**
- 🛒 Checkout with empty cart
- 🛒 Add non-existent product to cart
- 🛒 Incomplete shipping address

All error tests verify the API returns appropriate error codes and messages.

### 4. 🔧 **Individual Endpoint Testing**

Test specific endpoints with custom or default data:

```
Test Individual Endpoints
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1 - GET /api/v1/categories
2 - POST /api/v1/categories
3 - GET /api/v1/products
4 - POST /api/v1/products
5 - GET /api/v1/carts/{sessionId}
6 - POST /api/v1/carts/{sessionId}/items
7 - POST /api/v1/checkout
8 - GET /api/v1/orders/{orderNumber}
```

**Interactive Prompts:**
- Provide custom values
- Use smart defaults (press Enter)
- Context-aware (uses previously created resources)
- See formatted responses immediately

### 5. 🎨 **Enhanced Visual Design**

**Beautiful terminal output with:**
- ✨ Syntax-highlighted JSON responses
- 📊 Rich tables for cart and order items
- 🎯 Color-coded status indicators
- 📦 Panel-based layouts
- ⏱️ Progress bars with spinners
- 🎭 Emoji indicators for quick scanning

**Response Viewing:**
```json
╭─── Response ────────────────────────╮
│ {                                   │
│   "orderNumber": "ORD-20250104-001",│
│   "status": "PENDING",              │
│   "message": "Order created"        │
│ }                                   │
╰─────────────────────────────────────╯
```

### 6. 🔄 **Test Context Management**

The tool maintains state across operations:

- **Session ID**: Generated once, reused across cart operations
- **Auth Token**: Obtained once, reused for authenticated requests
- **Category IDs**: Saved and used for product creation
- **Product IDs**: List maintained for cart operations
- **Order Numbers**: Saved for order verification

This enables **connected test flows** with meaningful relationships.

### 7. 📊 **Smart Test Data Defaults**

All tests use meaningful, interconnected defaults:

**Category:**
- Name: "Electronics"
- Description: "Electronic devices and accessories"

**Products:**
- Product 1: "Premium Wireless Headphones" - $149.99 (qty: 50)
- Product 2: "Protective Phone Case" - $29.99 (qty: 200)

**Cart:**
- 2x Headphones = $299.98
- 1x Phone Case = $29.99
- **Total: $329.97**

**Customer:**
- Name: John Doe
- Email: john.doe@example.com
- Phone: +14155551234
- Address: 123 Main Street, San Francisco, CA 94105

**All SKUs**: Auto-generated with unique identifiers

## 🔄 Changed Files

### `test_runner.py` (Complete Rewrite)

**Added:**
- Docker container management functions
- Interactive menu system
- Error scenario testing (3 categories)
- Individual endpoint testing with prompts
- Enhanced response display with syntax highlighting
- Signal handling for graceful shutdown
- Container lifecycle management
- Service health checking

**New Functions:**
- `start_containers()` - Start Docker infrastructure
- `stop_containers()` - Stop and cleanup containers
- `check_containers_status()` - Check if containers are running
- `wait_for_services()` - Wait for services to be healthy
- `interactive_mode()` - Main interactive menu
- `happy_path_flow()` - Complete happy path
- `test_individual_endpoints()` - Individual endpoint menu
- `error_scenarios_400()` - 400 error testing
- `error_scenarios_404()` - 404 error testing
- `error_scenarios_cart_checkout()` - Cart/checkout errors
- `show_response()` - Beautiful response formatting
- `signal_handler()` - Ctrl+C handling
- Multiple `test_*_interactive()` functions

**New Commands:**
```bash
python3 test_runner.py start [--interactive] [--keep-alive] [--detach]
python3 test_runner.py stop
python3 test_runner.py interactive
```

### `README.md` (Complete Rewrite)

**Added:**
- Docker management documentation
- Interactive mode guide
- Error scenario descriptions
- Command reference with examples
- Troubleshooting section
- Configuration details
- Output examples with screenshots
- Best practices
- Test data management explanation

**New Sections:**
- 🐳 Docker Container Management
- 🎮 Interactive Testing Mode
- 🧪 Test Scenarios (detailed breakdown)
- 🎨 Output Examples
- 🎯 Command Reference
- 🔧 Configuration
- 🐛 Troubleshooting
- 📊 Test Data Management
- 🎓 Best Practices

### `QUICKSTART.md` (New File)

Quick 30-second start guide:
- One command to run everything
- Interactive menu overview
- Common workflows
- Troubleshooting tips
- Speed run guide (< 1 minute)

### `requirements.txt` (Unchanged)

All existing dependencies are compatible:
- requests
- psycopg2-binary
- redis
- kafka-python
- rich
- typer
- pydantic
- python-dotenv

## 🎯 Usage Changes

### Before (Old Way)

```bash
# Had to start infrastructure manually
cd infrastructure
docker-compose up -d
./kafka/create-topics.sh

# Then run tests separately
cd ../manual-tests
python test_runner.py full-flow
```

### After (New Way)

```bash
# Everything in one command
cd manual-tests
python3 test_runner.py start --interactive

# Or for persistent environment
python3 test_runner.py start --detach
python3 test_runner.py interactive
# ... test multiple times ...
python3 test_runner.py stop
```

## 🚀 New Workflows

### 1. Quick Test Session
```bash
python3 test_runner.py start --interactive
# Select option 1 (Happy Path)
# Containers auto-cleanup on exit
```

### 2. Development Environment
```bash
python3 test_runner.py start --detach
# Containers stay running
# Test repeatedly:
python3 test_runner.py interactive
# When done:
python3 test_runner.py stop
```

### 3. Error Testing
```bash
python3 test_runner.py start --interactive
# Select option 3, 4, or 5
# See validation errors
# Verify error handling
```

### 4. Custom Endpoint Testing
```bash
python3 test_runner.py start --interactive
# Select option 2
# Choose specific endpoints
# Provide custom data
```

## ✅ Backward Compatibility

All original commands still work:

```bash
# Original commands (require manual infrastructure start)
python test_runner.py test-health
python test_runner.py test-auth
python test_runner.py full-flow
python test_runner.py verify-database
python test_runner.py verify-kafka
```

## 📈 Benefits

1. **🎯 Simplified Workflow**: One command to start everything
2. **🎮 Interactive Experience**: Menu-driven, user-friendly
3. **❌ Better Testing**: Error scenarios included
4. **🎨 Beautiful Output**: Syntax highlighting, tables, colors
5. **🔄 Stateful**: Context preserved across tests
6. **🐳 Container Management**: No manual Docker commands needed
7. **🧹 Clean Shutdown**: Graceful cleanup with Ctrl+C
8. **📚 Smart Defaults**: Meaningful test data out of the box
9. **🔧 Flexible**: Interactive or automated modes
10. **📖 Well Documented**: Comprehensive README and quickstart

## 🎓 Recommended First Use

```bash
cd manual-tests
python3 test_runner.py start --interactive
# Select: 1 (Happy Path)
# Press Enter for all prompts
# Enjoy the show! ✨
```

## 📝 Notes

- Docker must be running before using container management features
- Ctrl+C is handled gracefully and will cleanup containers
- Use `--keep-alive` or `--detach` to persist containers between test sessions
- All test data uses realistic, meaningful defaults
- Interactive mode allows custom values for all inputs
- Error scenarios test API validation and error handling

## 🔗 See Also

- [README.md](README.md) - Complete documentation
- [QUICKSTART.md](QUICKSTART.md) - 30-second start guide
- [requirements.txt](requirements.txt) - Python dependencies

---

**Updated**: October 4, 2025

