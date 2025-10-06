# Unit Test Coverage Plan (T092)

**Goal**: Achieve >80% line coverage and >70% branch coverage on both services before integration tests  
**Approach**: Business-driven, mutation testing-aware, risk-based prioritization  
**Timeline**: Complete before T058-T063 (Integration Tests)

---

## 📊 Current State Analysis

### Customer-Facing Service
**Existing Tests**: 2 test classes (CartServiceTest, CheckoutServiceTest)

**Coverage Gaps**:
- ❌ CatalogService (0% coverage) - **HIGH BUSINESS RISK**
- ❌ IdempotencyService (0% coverage) - **HIGH BUSINESS RISK**
- ❌ OrderNumberService (0% coverage) - **MEDIUM RISK**
- ❌ MockAuthService (0% coverage) - **LOW RISK** (dev-only)
- ⚠️ CartService (partial coverage) - **NEEDS EXPANSION**
- ⚠️ CheckoutService (partial coverage) - **NEEDS EXPANSION**
- ❌ All Controllers (0% coverage) - **MEDIUM RISK**
- ❌ All Repositories (0% coverage) - **LOW RISK** (simple CRUD)

### Order-Management Service
**Existing Tests**: 2 test classes (OrderProcessingServiceTest, PaymentCompletedServiceTest)

**Coverage Gaps**:
- ❌ OrderQueryService (0% coverage) - **HIGH BUSINESS RISK**
- ⚠️ OrderProcessingService (partial coverage) - **NEEDS EXPANSION**
- ⚠️ PaymentCompletedService (partial coverage) - **NEEDS EXPANSION**
- ❌ OrderController (0% coverage) - **MEDIUM RISK**
- ❌ All Repositories (0% coverage) - **LOW RISK** (simple CRUD)

---

## 🎯 Testing Strategy

### Prioritization Framework

**Priority 1 - CRITICAL BUSINESS LOGIC** (Must have >90% coverage):
- Money calculations (cart subtotals, order totals, price snapshots)
- Inventory management (decrement, validation, insufficient stock)
- State transitions (order status, payment status, cancellation rules)
- Idempotency (checkout, event processing)
- Data integrity (order number generation, uniqueness constraints)

**Priority 2 - BUSINESS RULES** (Must have >85% coverage):
- Validation logic (SKU uniqueness, category existence, email format)
- Authorization rules (cancellation, fulfillment permissions)
- Edge cases (empty carts, deleted products, expired sessions)
- Error handling (not found, invalid state, duplicate resources)

**Priority 3 - INTEGRATION POINTS** (Must have >75% coverage):
- Controllers (request validation, response mapping, error handling)
- Event publishing (outbox, correlation IDs, payload structure)
- Repository custom queries (filtering, pagination, complex joins)

**Priority 4 - INFRASTRUCTURE** (Must have >60% coverage):
- Mappers (DTO ↔ Entity conversions)
- Configuration classes
- Utility methods

---

## 📋 Test Implementation Plan

### Phase 1: Critical Business Logic (Days 1-2)

#### Customer-Facing Service

**1.1 CatalogServiceTest** (Priority 1 - NEW)
```
Target: CatalogService.java (255 lines)
Coverage Goal: >90% line, >85% branch

Test Categories:
├── Category Operations (8 tests)
│   ├── ✅ createCategory_success
│   ├── ✅ createCategory_withDescription_success
│   ├── ✅ updateCategory_success
│   ├── ✅ updateCategory_notFound_throwsException
│   ├── ✅ deleteCategory_success
│   ├── ✅ deleteCategory_withProducts_throwsException
│   ├── ✅ getCategoryById_success
│   └── ✅ getCategoryById_notFound_throwsException
│
├── Product Operations - Creation (6 tests)
│   ├── ✅ createProduct_success
│   ├── ✅ createProduct_duplicateSKU_throwsException
│   ├── ✅ createProduct_categoryNotFound_throwsException
│   ├── ✅ createProduct_capturesPriceSnapshot
│   ├── ✅ createProduct_defaultsIsActiveToTrue
│   └── ✅ createProduct_validatesInventoryNonNegative
│
├── Product Operations - Update/Delete (6 tests)
│   ├── ✅ updateProduct_success
│   ├── ✅ updateProduct_notFound_throwsException
│   ├── ✅ updateProduct_categoryNotFound_throwsException
│   ├── ✅ deleteProduct_softDelete_success
│   ├── ✅ deleteProduct_notFound_throwsException
│   └── ✅ getProductById_success
│
└── Product Listing & Filtering (6 tests)
    ├── ✅ listProducts_noFilters_returnsAll
    ├── ✅ listProducts_byCategoryId_filtersCorrectly
    ├── ✅ listProducts_byIsActive_filtersCorrectly
    ├── ✅ listProducts_byCategoryAndActive_filtersCorrectly
    ├── ✅ listProducts_pagination_worksCorrectly
    └── ✅ listProducts_emptyResult_returnsEmptyPage

Total: 26 tests
Mutation Testing Focus:
- Boundary conditions (null checks, empty strings)
- Boolean logic (isActive flags, existence checks)
- Exception paths (all throw statements)
```

**1.2 IdempotencyServiceTest** (Priority 1 - NEW)
```
Target: IdempotencyService.java
Coverage Goal: >95% line, >90% branch (CRITICAL for money safety)

Test Categories:
├── Key Validation (4 tests)
│   ├── ✅ validateAndStore_newKey_success
│   ├── ✅ validateAndStore_duplicateKeyMatchingFingerprint_returnsCache
│   ├── ✅ validateAndStore_duplicateKeyDifferentFingerprint_throwsException
│   └── ✅ validateAndStore_expiredKey_allowsReuse
│
├── Fingerprint Calculation (3 tests)
│   ├── ✅ calculateFingerprint_sameRequest_sameFingerprint
│   ├── ✅ calculateFingerprint_differentRequest_differentFingerprint
│   └── ✅ calculateFingerprint_orderIndependent_forCollections
│
└── Response Caching (3 tests)
    ├── ✅ cacheResponse_success
    ├── ✅ getCachedResponse_exists_returnsResponse
    └── ✅ getCachedResponse_notExists_returnsEmpty

Total: 10 tests
Mutation Testing Focus:
- Equality checks (fingerprint matching)
- TTL calculations (24h expiration)
- Cache hit/miss logic
```

**1.3 CheckoutServiceTest - EXPANSION** (Priority 1 - EXPAND EXISTING)
```
Target: CheckoutService.java
Current Coverage: ~40% → Goal: >90%

Additional Tests Needed (12 tests):
├── Inventory Validation (4 tests)
│   ├── ✅ checkout_insufficientInventory_singleItem_throwsException
│   ├── ✅ checkout_insufficientInventory_multipleItems_throwsException
│   ├── ✅ checkout_inventoryDecrementedAtomically
│   └── ✅ checkout_inventoryRollbackOnFailure
│
├── Order Number Generation (3 tests)
│   ├── ✅ checkout_generatesUniqueOrderNumber
│   ├── ✅ checkout_orderNumberFollowsPattern_ORD_YYYYMMDD_NNN
│   └── ✅ checkout_concurrentCheckouts_uniqueOrderNumbers
│
├── Outbox Publishing (3 tests)
│   ├── ✅ checkout_writesToOutbox_withCorrectPayload
│   ├── ✅ checkout_outboxIncludesCorrelationId
│   └── ✅ checkout_outboxFailure_rollsBackCheckout
│
└── Edge Cases (2 tests)
    ├── ✅ checkout_emptyCart_throwsException
    └── ✅ checkout_deletedProduct_throwsException

Total: 12 new tests (2 existing + 12 = 14 total)
Mutation Testing Focus:
- Transactional boundaries (rollback scenarios)
- Arithmetic operations (inventory decrement, subtotal calculation)
- Null safety (cart items, product references)
```

#### Order-Management Service

**1.4 OrderQueryServiceTest** (Priority 1 - NEW)
```
Target: OrderQueryService.java (226 lines)
Coverage Goal: >90% line, >85% branch

Test Categories:
├── Order Lookup (3 tests)
│   ├── ✅ getOrderByNumber_success
│   ├── ✅ getOrderByNumber_notFound_throwsException
│   └── ✅ getOrderByNumber_enrichesPaymentStatus
│
├── Order Listing & Filtering (7 tests)
│   ├── ✅ listOrders_noFilters_returnsAll
│   ├── ✅ listOrders_byStatus_filtersCorrectly
│   ├── ✅ listOrders_byCustomerEmail_filtersCorrectly
│   ├── ✅ listOrders_byDateRange_filtersCorrectly
│   ├── ✅ listOrders_combinedFilters_worksCorrectly
│   ├── ✅ listOrders_pagination_hasNext_true
│   └── ✅ listOrders_pagination_hasNext_false
│
├── Order Cancellation (5 tests)
│   ├── ✅ cancelOrder_statusPending_success
│   ├── ✅ cancelOrder_statusProcessing_success
│   ├── ✅ cancelOrder_statusPaid_throwsInvalidStateException
│   ├── ✅ cancelOrder_statusFulfilled_throwsInvalidStateException
│   └── ✅ cancelOrder_notFound_throwsException
│
└── Order Fulfillment (5 tests)
    ├── ✅ fulfillOrder_statusPaid_success
    ├── ✅ fulfillOrder_statusPending_throwsInvalidStateException
    ├── ✅ fulfillOrder_statusCancelled_throwsInvalidStateException
    ├── ✅ fulfillOrder_notFound_throwsException
    └── ✅ fulfillOrder_recordsTrackingInfo

Total: 20 tests
Mutation Testing Focus:
- State machine logic (canBeCancelled, status checks)
- Enum comparisons (OrderStatus equality)
- Pagination math (hasNext calculation, offset/limit)
```

**1.5 OrderProcessingServiceTest - EXPANSION** (Priority 1 - EXPAND EXISTING)
```
Target: OrderProcessingService.java
Current Coverage: ~50% → Goal: >90%

Additional Tests Needed (8 tests):
├── Idempotency (3 tests)
│   ├── ✅ processOrderCreatedEvent_duplicateEventId_skips
│   ├── ✅ processOrderCreatedEvent_recordsEventId
│   └── ✅ processOrderCreatedEvent_idempotencyCheckBeforeCreation
│
├── Order Creation (3 tests)
│   ├── ✅ processOrderCreatedEvent_createsOrderItems_correctly
│   ├── ✅ processOrderCreatedEvent_createsPaymentTransaction
│   └── ✅ processOrderCreatedEvent_setsInitialStatus_PENDING
│
└── Error Handling (2 tests)
    ├── ✅ processOrderCreatedEvent_invalidPayload_logsError
    └── ✅ processPaymentAsync_paymentFailure_updatesStatus

Total: 8 new tests (8 existing + 8 = 16 total)
Mutation Testing Focus:
- Idempotency checks (existsByEventId)
- Order item iteration (bulk insert logic)
- Async payment triggering
```

---

### Phase 2: Business Rules & Validation (Days 3-4)

#### Customer-Facing Service

**2.1 CartServiceTest - EXPANSION** (Priority 2 - EXPAND EXISTING)
```
Target: CartService.java
Current Coverage: ~60% → Goal: >85%

Additional Tests Needed (10 tests):
├── Redis Integration (4 tests)
│   ├── ✅ getCart_foundInRedis_skipsDatabase
│   ├── ✅ getCart_notInRedis_loadsFromDatabase
│   ├── ✅ addItemToCart_savesToRedis_withTTL
│   └── ✅ clearCart_removesFromRedis_andDatabase
│
├── Inventory Validation (3 tests)
│   ├── ✅ addItemToCart_insufficientInventory_throwsException
│   ├── ✅ updateCartItemQuantity_insufficientInventory_throwsException
│   └── ✅ addItemToCart_productNotFound_throwsException
│
└── Subtotal Calculation (3 tests)
    ├── ✅ addItemToCart_calculatesSubtotal_correctly
    ├── ✅ updateCartItemQuantity_recalculatesSubtotal
    └── ✅ removeCartItem_recalculatesSubtotal

Total: 10 new tests (8 existing + 10 = 18 total)
Mutation Testing Focus:
- Arithmetic operations (subtotal = quantity * price)
- Redis TTL settings
- Null checks (cart not found, product not found)
```

**2.2 OrderNumberServiceTest** (Priority 2 - NEW)
```
Target: OrderNumberService.java
Coverage Goal: >85% line, >80% branch

Test Categories:
├── Order Number Generation (4 tests)
│   ├── ✅ generateOrderNumber_followsPattern
│   ├── ✅ generateOrderNumber_incrementsSequence
│   ├── ✅ generateOrderNumber_resetsSequenceDaily
│   └── ✅ generateOrderNumber_padsSequenceWithZeros
│
└── Concurrency (2 tests)
    ├── ✅ generateOrderNumber_concurrent_allUnique
    └── ✅ generateOrderNumber_concurrent_noGaps

Total: 6 tests
Mutation Testing Focus:
- Date formatting (YYYYMMDD)
- Sequence increment logic
- String padding (001, 002, etc.)
```

#### Order-Management Service

**2.3 PaymentCompletedServiceTest - EXPANSION** (Priority 2 - EXPAND EXISTING)
```
Target: PaymentCompletedService.java
Current Coverage: ~50% → Goal: >85%

Additional Tests Needed (6 tests):
├── Event Publishing (3 tests)
│   ├── ✅ publishPaymentCompleted_success_includesTransactionId
│   ├── ✅ publishPaymentCompleted_failure_includesFailureReason
│   └── ✅ publishPaymentCompleted_includesCorrelationId
│
└── Order Status Updates (3 tests)
    ├── ✅ processPaymentCompleted_success_updatesToPAID
    ├── ✅ processPaymentCompleted_failure_updatesToFAILED
    └── ✅ processPaymentCompleted_duplicateEvent_skips

Total: 6 new tests (4 existing + 6 = 10 total)
Mutation Testing Focus:
- Status transitions (PROCESSING → PAID/FAILED)
- Conditional fields (externalTransactionId vs failureReason)
- Idempotency checks
```

---

### Phase 3: Controllers & Integration Points (Days 5-6)

#### Customer-Facing Service

**3.1 CategoryControllerTest** (Priority 3 - NEW)
```
Target: CategoryController.java
Coverage Goal: >80% line, >75% branch

Test Categories:
├── CRUD Operations (5 tests)
│   ├── ✅ createCategory_validRequest_returns201
│   ├── ✅ updateCategory_validRequest_returns200
│   ├── ✅ deleteCategory_success_returns204
│   ├── ✅ getCategoryById_exists_returns200
│   └── ✅ listCategories_returns200
│
├── Validation (3 tests)
│   ├── ✅ createCategory_invalidRequest_returns400
│   ├── ✅ updateCategory_notFound_returns404
│   └── ✅ deleteCategory_notFound_returns404
│
└── Authorization (2 tests)
    ├── ✅ createCategory_requiresManagerRole
    └── ✅ deleteCategory_requiresManagerRole

Total: 10 tests
Mutation Testing Focus:
- HTTP status codes
- Request validation (@Valid)
- Response mapping
```

**3.2 ProductControllerTest** (Priority 3 - NEW)
```
Target: ProductController.java
Coverage Goal: >80% line, >75% branch

Test Categories:
├── CRUD Operations (6 tests)
│   ├── ✅ createProduct_validRequest_returns201
│   ├── ✅ updateProduct_validRequest_returns200
│   ├── ✅ deleteProduct_success_returns204
│   ├── ✅ getProductById_exists_returns200
│   ├── ✅ listProducts_withFilters_returns200
│   └── ✅ listProducts_pagination_returns200
│
├── Validation (4 tests)
│   ├── ✅ createProduct_invalidRequest_returns400
│   ├── ✅ createProduct_duplicateSKU_returns409
│   ├── ✅ updateProduct_notFound_returns404
│   └── ✅ deleteProduct_notFound_returns404
│
└── Authorization (2 tests)
    ├── ✅ createProduct_requiresManagerRole
    └── ✅ updateProduct_requiresManagerRole

Total: 12 tests
```

**3.3 CartControllerTest** (Priority 3 - NEW)
```
Target: CartController.java
Coverage Goal: >80% line, >75% branch

Test Categories:
├── Cart Operations (5 tests)
│   ├── ✅ getCart_exists_returns200
│   ├── ✅ getCart_notExists_returnsEmptyCart
│   ├── ✅ clearCart_success_returns204
│   ├── ✅ addItemToCart_success_returns200
│   └── ✅ removeCartItem_success_returns200
│
├── Validation (3 tests)
│   ├── ✅ addItemToCart_invalidQuantity_returns400
│   ├── ✅ addItemToCart_productNotFound_returns404
│   └── ✅ updateCartItemQuantity_insufficientInventory_returns409
│
└── Session Handling (2 tests)
    ├── ✅ getCart_validSessionId_worksCorrectly
    └── ✅ getCart_invalidSessionId_returns400

Total: 10 tests
```

**3.4 CheckoutControllerTest** (Priority 3 - NEW)
```
Target: CheckoutController.java
Coverage Goal: >85% line, >80% branch (money operations)

Test Categories:
├── Checkout Flow (3 tests)
│   ├── ✅ checkout_validRequest_returns201
│   ├── ✅ checkout_emptyCart_returns400
│   └── ✅ checkout_insufficientInventory_returns409
│
├── Idempotency (3 tests)
│   ├── ✅ checkout_missingIdempotencyKey_returns400
│   ├── ✅ checkout_duplicateIdempotencyKey_returnsCachedResponse
│   └── ✅ checkout_duplicateKeyDifferentPayload_returns409
│
└── Validation (2 tests)
    ├── ✅ checkout_invalidCustomerInfo_returns400
    └── ✅ checkout_invalidShippingAddress_returns400

Total: 8 tests
Mutation Testing Focus:
- Idempotency header enforcement
- Response caching logic
- Validation error messages
```

#### Order-Management Service

**3.5 OrderControllerTest** (Priority 3 - NEW)
```
Target: OrderController.java
Coverage Goal: >80% line, >75% branch

Test Categories:
├── Order Operations (4 tests)
│   ├── ✅ getOrderByNumber_exists_returns200
│   ├── ✅ getOrderByNumber_notFound_returns404
│   ├── ✅ listOrders_withFilters_returns200
│   └── ✅ listOrders_pagination_returns200
│
├── Manager Operations (4 tests)
│   ├── ✅ cancelOrder_validRequest_returns200
│   ├── ✅ cancelOrder_invalidState_returns409
│   ├── ✅ fulfillOrder_validRequest_returns200
│   └── ✅ fulfillOrder_invalidState_returns409
│
└── Authorization (2 tests)
    ├── ✅ listOrders_requiresManagerRole
    └── ✅ cancelOrder_requiresManagerRole

Total: 10 tests
```

---

### Phase 4: Repository Custom Queries (Day 7)

**4.1 ProductRepositoryTest** (Priority 3 - NEW)
```
Target: ProductRepository.java custom queries
Coverage Goal: >75% line

Test Categories:
├── Custom Queries (4 tests)
│   ├── ✅ findByCategoryIdAndIsActive_filtersCorrectly
│   ├── ✅ findByCategoryId_returnsAllInCategory
│   ├── ✅ findByIsActive_filtersCorrectly
│   └── ✅ existsBySku_checksUniqueness
│
└── Locking (2 tests)
    ├── ✅ lockProductsForUpdate_acquiresLock
    └── ✅ lockProductsForUpdate_emptyList_noError

Total: 6 tests
```

**4.2 OrderRepositoryTest** (Priority 3 - NEW)
```
Target: OrderRepository.java custom queries
Coverage Goal: >75% line

Test Categories:
├── Custom Queries (4 tests)
│   ├── ✅ findByOrderNumber_success
│   ├── ✅ findWithFilters_noFilters_returnsAll
│   ├── ✅ findWithFilters_withStatus_filtersCorrectly
│   └── ✅ findWithFilters_withDateRange_filtersCorrectly
│
└── Pagination (2 tests)
    ├── ✅ findWithFilters_pagination_limit_worksCorrectly
    └── ✅ findWithFilters_pagination_offset_worksCorrectly

Total: 6 tests
```

---

## 🧬 Mutation Testing Strategy

### Key Mutation Operators to Test

**1. Arithmetic Operators** (Critical for money calculations)
```java
// Original: subtotal = quantity * priceSnapshot
// Mutant 1: subtotal = quantity + priceSnapshot  ❌ Should fail
// Mutant 2: subtotal = quantity / priceSnapshot  ❌ Should fail
// Mutant 3: subtotal = quantity - priceSnapshot  ❌ Should fail

Test: assert subtotal equals exactly (quantity * price)
```

**2. Relational Operators** (Critical for inventory/status checks)
```java
// Original: if (inventory >= quantity)
// Mutant 1: if (inventory > quantity)   ❌ Should fail (edge case: inventory == quantity)
// Mutant 2: if (inventory <= quantity)  ❌ Should fail
// Mutant 3: if (inventory < quantity)   ❌ Should fail

Test: assert exception when inventory == quantity - 1
Test: assert success when inventory == quantity (boundary)
```

**3. Conditional Boundaries** (Critical for status transitions)
```java
// Original: if (status == OrderStatus.PAID)
// Mutant 1: if (status != OrderStatus.PAID)  ❌ Should fail
// Mutant 2: if (true)                        ❌ Should fail
// Mutant 3: if (false)                       ❌ Should fail

Test: assert success only for PAID status
Test: assert exception for all other statuses (PENDING, PROCESSING, etc.)
```

**4. Logical Operators** (Critical for validation)
```java
// Original: if (categoryId != null && isActive != null)
// Mutant 1: if (categoryId != null || isActive != null)  ❌ Should fail
// Mutant 2: if (categoryId == null && isActive != null)  ❌ Should fail

Test: all 4 combinations (both null, one null, both non-null)
```

**5. Return Values** (Critical for existence checks)
```java
// Original: return repository.existsById(id);
// Mutant 1: return !repository.existsById(id);  ❌ Should fail
// Mutant 2: return true;                        ❌ Should fail
// Mutant 3: return false;                       ❌ Should fail

Test: verify true when exists, false when not exists
```

**6. Void Method Calls** (Critical for side effects)
```java
// Original: repository.save(order);
// Mutant 1: // repository.save(order);  ❌ Should fail (removed call)

Test: verify save was called with correct arguments
```

### Mutation Testing Tools

**Recommended**: PIT (Pitest) - industry standard for Java
```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.3</version>
    <configuration>
        <targetClasses>
            <param>com.ecommerce.customer.service.*</param>
            <param>com.ecommerce.order.service.*</param>
        </targetClasses>
        <targetTests>
            <param>com.ecommerce.customer.service.*Test</param>
            <param>com.ecommerce.order.service.*Test</param>
        </targetTests>
        <mutationThreshold>85</mutationThreshold>
        <coverageThreshold>80</coverageThreshold>
    </configuration>
</plugin>
```

---

## 📈 Success Metrics

### Coverage Targets

| Layer | Line Coverage | Branch Coverage | Mutation Score |
|-------|--------------|-----------------|----------------|
| **Services** | >90% | >85% | >85% |
| **Controllers** | >80% | >75% | >75% |
| **Repositories** | >75% | >70% | >70% |
| **Overall** | >80% | >70% | >75% |

### Quality Gates

**Before merging any test PR:**
1. ✅ All tests pass (0 failures, 0 errors)
2. ✅ Coverage thresholds met (JaCoCo report)
3. ✅ Mutation score >75% (PIT report)
4. ✅ No skipped tests without justification
5. ✅ Test execution time <30 seconds per service

**Before starting integration tests (T058-T063):**
1. ✅ All unit tests from this plan implemented
2. ✅ Overall coverage >80% line, >70% branch
3. ✅ All Priority 1 tests have >90% coverage
4. ✅ Mutation testing report shows >75% mutation kill rate
5. ✅ Code review completed with QA agent sign-off

---

## 🛠️ Testing Best Practices

### Mocking Strategy

**DO Mock:**
- External dependencies (Kafka, Redis, Database)
- Other services (CatalogService in CheckoutService tests)
- Repositories (in service tests)
- Mappers (in controller tests)

**DON'T Mock:**
- The class under test
- Value objects (DTOs, entities)
- Simple utilities (UUID generation, date formatting)

### Test Structure (AAA Pattern)

```java
@Test
void checkout_insufficientInventory_throwsException() {
    // Arrange
    UUID productId = UUID.randomUUID();
    Product product = createProduct(productId, 5); // 5 in stock
    Cart cart = createCartWithItem(productId, 10); // wants 10
    
    when(cartRepository.findBySessionId(any())).thenReturn(Optional.of(cart));
    when(productRepository.lockProductsForUpdate(any())).thenReturn(List.of(product));
    
    // Act & Assert
    assertThatThrownBy(() -> checkoutService.checkout("session", customerInfo))
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessageContaining("productId=" + productId)
        .hasMessageContaining("requested=10")
        .hasMessageContaining("available=5");
    
    // Verify no side effects
    verify(productRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
}
```

### Parameterized Tests for Branches

```java
@ParameterizedTest
@EnumSource(value = OrderStatus.class, names = {"PAID", "FULFILLED", "CANCELLED"})
void cancelOrder_invalidStatus_throwsException(OrderStatus status) {
    // Arrange
    Order order = createOrder(status);
    when(orderRepository.findByOrderNumber(any())).thenReturn(Optional.of(order));
    
    // Act & Assert
    assertThatThrownBy(() -> orderQueryService.cancelOrder("ORD-123", cancelRequest))
        .isInstanceOf(InvalidOrderStateException.class)
        .hasMessageContaining(status.name());
}
```

### Verify Mock Interactions

```java
@Test
void checkout_success_publishesToOutbox() {
    // Arrange
    // ... setup mocks
    
    // Act
    checkoutService.checkout("session", customerInfo);
    
    // Assert
    ArgumentCaptor<OrderCreatedOutbox> outboxCaptor = ArgumentCaptor.forClass(OrderCreatedOutbox.class);
    verify(outboxRepository).save(outboxCaptor.capture());
    
    OrderCreatedOutbox outbox = outboxCaptor.getValue();
    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(outbox.getEventType()).isEqualTo("OrderCreated");
    assertThat(outbox.getPayload()).contains("orderNumber");
}
```

---

## 📅 Implementation Timeline

| Phase | Days | Tests | Coverage Gain | Deliverable |
|-------|------|-------|---------------|-------------|
| **Phase 1** | 2 | 88 tests | +45% | Critical business logic covered |
| **Phase 2** | 2 | 32 tests | +20% | Business rules & validation covered |
| **Phase 3** | 2 | 50 tests | +15% | Controllers & integration points covered |
| **Phase 4** | 1 | 12 tests | +5% | Repository queries covered |
| **Total** | **7 days** | **182 tests** | **>80%** | **Ready for integration tests** |

---

## 🎯 Next Steps

1. **Review this plan** with team/stakeholders
2. **Set up mutation testing** (add PIT to POMs)
3. **Create test templates** (copy-paste skeletons for each test class)
4. **Start Phase 1** (CatalogServiceTest, IdempotencyServiceTest)
5. **Daily check-ins** to review coverage progress
6. **Update tasks.md** as tests are completed
7. **Generate coverage reports** after each phase
8. **Final review** before marking T092 complete

---

## 📚 References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Fluent Assertions](https://assertj.github.io/doc/)
- [PIT Mutation Testing](https://pitest.org/)
- [Testing Pyramid - Martin Fowler](https://martinfowler.com/articles/practical-test-pyramid.html)
- [Mutation Testing Best Practices](https://pedrorijo.com/blog/intro-mutation/)

---

**Plan Status**: ✅ READY FOR IMPLEMENTATION  
**Plan Author**: AI Implementation Agent  
**Plan Date**: 2025-10-06  
**Approved By**: _Pending Review_
