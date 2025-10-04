# Quick Start Guide - Interactive Testing

Get started with testing the E-Commerce Platform in 30 seconds!

## 🚀 One Command Start

```bash
cd manual-tests
python3 test_runner.py start --interactive
```

This single command will:
1. 🐳 **Start all containers** (PostgreSQL, Redis, Kafka, Customer Service, Order Service)
2. ⏳ **Wait for services** to be healthy and ready
3. 🎮 **Launch interactive menu** for testing
4. 🧹 **Clean up containers** when you exit

## 🎮 Interactive Menu

Once started, you'll see:

```
╔══════════════════════════════════════════════════════════╗
║           Interactive Testing Menu                        ║
╚══════════════════════════════════════════════════════════╝

Select a test scenario [1]: 
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

### Recommended First Test

**Option 1: Happy Path - Complete Flow**

This runs the complete customer journey:
- ✅ Health checks
- 🔐 Authentication
- 📚 Create category "Electronics"
- 📦 Create 2 products (Headphones $149.99, Phone Case $29.99)
- 🛒 Add items to cart
- 💳 Checkout as "John Doe"
- 📊 Verify order processing
- 🗄️ Check databases
- 📨 Verify Kafka events

**All with meaningful default values - just press Enter!**

## 🎯 Common Workflows

### For Quick Testing

```bash
# Start, test, and auto-cleanup
python3 test_runner.py start --interactive
```

### For Development

```bash
# Start containers and keep them running
python3 test_runner.py start --detach

# Later, run tests multiple times
python3 test_runner.py interactive
python3 test_runner.py interactive
python3 test_runner.py interactive

# When done
python3 test_runner.py stop
```

### For Automated Testing

```bash
# Start and run full automated flow
python3 test_runner.py start --interactive=false
```

## 🎨 What You'll See

Beautiful, colorful terminal output with:

✅ **Green checkmarks** for successful operations  
❌ **Red X marks** for failures (in error testing)  
🔍 **Blue info** for test steps  
⚠️ **Yellow warnings** for non-critical issues  

**Tables** for cart contents and orders  
**Syntax-highlighted JSON** for API responses  
**Progress bars** for waiting operations  

## 🐛 Troubleshooting

### Docker Not Running
```
❌ Docker daemon is not running. Please start Docker.
```
→ Start Docker Desktop

### Port Already in Use
→ Check if services are already running:
```bash
docker ps
```

### Services Not Healthy
→ Give it a bit more time, or check logs:
```bash
docker-compose -p ecommerce-platform logs
```

## 📚 Next Steps

After your first happy path test:

1. **Try Error Scenarios** (Option 3, 4, 5)
   - See how the API handles validation errors
   - Test 404 not found cases
   - Verify cart/checkout error handling

2. **Test Individual Endpoints** (Option 2)
   - Create custom categories and products
   - Test specific flows
   - Experiment with different data

3. **Inspect the Backend** (Option 6, 7)
   - Check database records
   - View Kafka event messages
   - Verify data persistence

## 🎓 Pro Tips

- Press **Ctrl+C** anytime to exit gracefully
- Use **`--keep-alive`** to keep containers between test runs
- Use **`--detach`** for a persistent dev environment
- All tests use **smart defaults** - just press Enter!
- Containers auto-start if not running (when using `interactive`)

## ⚡ Speed Run (< 1 Minute)

```bash
cd manual-tests
python3 test_runner.py start --interactive
# Select option 1
# Press Enter for all prompts
# Watch the magic happen! ✨
```

You'll see:
- ✅ Services started
- ✅ Authentication successful
- ✅ Products created
- ✅ Cart populated
- ✅ Order placed
- ✅ Payment processed
- ✅ Data verified
- 🎉 All tests passed!

## 📖 Full Documentation

See [README.md](README.md) for complete details on all features and options.

---

**Happy Testing! 🚀**

