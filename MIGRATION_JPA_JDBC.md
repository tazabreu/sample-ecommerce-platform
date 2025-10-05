# Migration Guide: Spring Data JPA → Spring Data JDBC

This guide provides a comprehensive, executable plan for migrating both microservices from Spring Data JPA to Spring Data JDBC. It follows TDD principles (fail-first tests), maintains database schemas unchanged, and identifies layers that need modification.

**Target Services:**
- `customer-facing-service` (7 entities: Category, Product, Cart, CartItem, OrderCreatedOutbox, CheckoutIdempotency, OrderNumberSequence)
- `order-management-service` (4 entities: Order, OrderItem, PaymentTransaction, ProcessedEvent)

**Migration Philosophy:**
- **No schema changes**: Flyway migrations remain untouched
- **Aggregate-driven design**: Replace implicit JPA persistence context with explicit aggregate boundaries
- **Test-first**: Update tests to fail before code changes, validate with green tests after
- **Layered approach**: Changes isolated to model → repository → service → configuration layers
- **Backward compatibility**: External APIs and contracts remain unchanged

---

## Phase 0: Prerequisites & Discovery

**Purpose**: Validate environment, document current state, establish rollback plan

### M001: Environment Validation

**Files**: Local environment, CI/CD pipelines

**Actions**:
1. Confirm Java 21 across all environments (no JDK 22+ features)
2. Verify Testcontainers (PostgreSQL 15, Redis 7, Kafka) functional locally
3. Run full test suite to establish baseline (`mvn clean verify`)
4. Document current test results (passing count, coverage %)
5. Create git branch `migration/jpa-to-jdbc` from main
6. Tag current commit as `pre-jdbc-migration` for rollback

**Acceptance Criteria**:
- All tests passing (baseline: 100% of existing contract/unit tests)
- Test coverage documented (target: maintain or improve current %)
- Branch created, rollback tag available

**Checkpoint 🛑**: Review with human before proceeding

---

### M002: Dependency Analysis

**Files**: Both services' `pom.xml`, `target/` directories after build

**Actions**:
1. Run `mvn dependency:tree > dependency-tree-before.txt` in both services
2. Identify all JPA/Hibernate transitive dependencies
3. Document current dependency versions (Spring Boot, MapStruct, Jackson)
4. Identify Jackson Hibernate module usage (`jackson-datatype-hibernate5-jakarta`)
5. Check for Hibernate-specific features in code (HQL, Criteria API, lazy proxies)

**Deliverable**: Create `/docs/jpa-dependencies-audit.md` with:
- List of all JPA/Hibernate dependencies (direct + transitive)
- Code locations using Hibernate-specific features (grep results)
- Redis serialization dependencies that need adjustment

**Checkpoint 🛑**: Review findings with human

---

## Phase 1: Test Preparation (TDD Gate)

**Purpose**: Update tests to work with JDBC semantics BEFORE changing production code. Tests must fail initially, guiding implementation.

### M003: Identify JPA-Specific Test Patterns

**Files**: All test files in both services (`src/test/java/`)

**Actions**:
1. Search for JPA-specific test code patterns:
   - `EntityManager` usage
   - `@DataJpaTest` annotations
   - `flush()` or `clear()` calls
   - Lazy loading expectations (proxy assertions)
   - `ReflectionTestUtils.setField()` for ID injection
   - Mock repository behavior assuming auto-generated IDs
2. Document test anti-patterns tied to JPA implementation details
3. Create checklist of tests requiring modification

**Deliverable**: `/docs/test-migration-checklist.md` with:
- List of tests with JPA-specific patterns (file + line numbers)
- Categorization: High/Medium/Low coupling to JPA
- Recommended refactoring strategy per test

**Checkpoint 🛑**: Review test coupling analysis

---

### M004: Update Repository Test Annotations (Customer Service)

**Files**: `customer-facing-service/src/test/java/.../repository/*Test.java` (if they exist)

**Actions**:
1. Replace `@DataJpaTest` with `@DataJdbcTest`
2. Remove `EntityManager` injections
3. Update test setup to avoid JPA-specific flush/clear calls
4. Ensure tests use standard `Repository.save()` and query methods
5. Run tests → **EXPECT FAILURES** (entities still use JPA annotations)

**Expected Result**: Tests fail with compilation or runtime errors (annotations mismatch)

**Notes**: If no repository slice tests exist, skip to service tests

---

### M005: Update Repository Test Annotations (Order Service)

**Files**: `order-management-service/src/test/java/.../repository/*Test.java`

**Actions**: Same as M004 for order-management-service

**Expected Result**: Tests fail (JPA annotations still present)

---

### M006: Update Service Unit Tests (Customer Service)

**Files**: `CartServiceTest.java`, `CheckoutServiceTest.java`, `CatalogServiceTest.java`

**Key Changes**:
1. **ID Management**: Remove `ReflectionTestUtils.setField()` for IDs; instead:
   - In mocks: Use `when(repo.save(any())).thenAnswer(invocation -> { Entity e = invocation.getArgument(0); e.setId(UUID.randomUUID()); return e; })`
   - Entities will need `setId()` methods post-migration
2. **Relationship Handling**: Update mocks to return entities with initialized collections (no lazy proxies)
3. **Timestamp Handling**: Manually set `createdAt`/`updatedAt` in mocks (no auto-population)
4. **Product/Category References**: Expect `categoryId` (UUID) instead of `category` (entity)

**Specific Test Updates**:
- `CartServiceTest`: Mock cart items with product IDs instead of lazy-loaded products
- `CheckoutServiceTest`: Update product locking mock to return product with ID already set
- `CatalogServiceTest`: Category-product relationship now via `categoryId` field

**Expected Result**: Tests fail (entity models still use JPA annotations)

---

### M007: Update Service Unit Tests (Order Service)

**Files**: `OrderProcessingServiceTest.java`, `OrderQueryServiceTest.java`, `PaymentCompletedServiceTest.java`

**Key Changes**:
1. Update mocks to set IDs explicitly (no JPA auto-generation assumption)
2. Order/OrderItem relationship: Items stored inline (no lazy fetch)
3. Order/PaymentTransaction: One-to-one becomes separate aggregate or ID reference
4. JSONB handling: Ensure `shippingAddress` map serialization/deserialization works

**Specific Test Updates**:
- `OrderProcessingServiceTest.mockOrderRepositorySave()`: Enhance to handle nested OrderItems
- Payment transaction creation: Expect explicit save, not cascade
- ProcessedEvent: Simple entity, minimal changes needed

**Expected Result**: Tests fail (entity models still use JPA)

---

### M008: Update Contract Tests (Both Services)

**Files**: All `*ContractTest.java` files

**Actions**:
1. Review contract tests for JPA-specific assumptions
2. Most contract tests should be **unchanged** (they test HTTP contracts, not persistence)
3. Verify Testcontainers PostgreSQL still works with JDBC
4. Check for any `@Transactional` test methods that might behave differently

**Expected Result**: Contract tests likely still pass (they test external API, not internal persistence)

**Note**: If any contract tests directly assert database state via EntityManager, update them to use repository queries

---

## Phase 2: Entity Model Transformation

**Purpose**: Convert JPA entities to Spring Data JDBC aggregates. This is the core of the migration.

### M009: Create JDBC Configuration (Customer Service)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/JdbcConfig.java`

**Actions**:
1. Create new configuration class extending `AbstractJdbcConfiguration`
2. Register custom converters for JSONB fields (if any beyond standard types)
3. Enable auditing callbacks for `createdAt`/`updatedAt` timestamps
4. Configure naming strategy (snake_case column mapping)

**Example Configuration**:

```java
@Configuration
@EnableJdbcAuditing
public class JdbcConfig extends AbstractJdbcConfiguration {

    private final ObjectMapper objectMapper;

    public JdbcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Converter for JSONB in OrderCreatedOutbox (if needed)
    @Override
    public List<?> userConverters() {
        return List.of(
            new JsonbWritingConverter(objectMapper),
            new JsonbReadingConverter(objectMapper)
        );
    }

    // Custom NamingStrategy if needed (default: snake_case)
    @Bean
    NamingStrategy namingStrategy() {
        return new NamingStrategy() {
            @Override
            public String getSchema() {
                return "";
            }

            @Override
            public String getTableName(Class<?> type) {
                // Return lowercase with underscores (matches Flyway migrations)
                return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, type.getSimpleName()) + "s";
            }

            @Override
            public String getColumnName(RelationalPersistentProperty property) {
                return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, property.getName());
            }
        };
    }
}
```

**Deliverable**: Configuration class ready, not yet active (JPA still in dependencies)

---

### M010: Create JDBC Configuration (Order Service)

**Files**: `order-management-service/src/main/java/com/ecommerce/order/config/JdbcConfig.java`

**Actions**: Same as M009, with additional converters for:
- `Map<String, String>` ↔ JSONB for `Order.shippingAddress`
- Enum converters for `OrderStatus`, `PaymentStatus` (if not handled by default)

**Example JSONB Converter**:

```java
@WritingConverter
class MapToJsonbConverter implements Converter<Map<String, String>, String> {
    private final ObjectMapper objectMapper;

    MapToJsonbConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(Map<String, String> source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new ConverterException("Failed to convert Map to JSON", e);
        }
    }
}

@ReadingConverter
class JsonbToMapConverter implements Converter<String, Map<String, String>> {
    private final ObjectMapper objectMapper;

    JsonbToMapConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> convert(String source) {
        try {
            return objectMapper.readValue(source, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new ConverterException("Failed to convert JSON to Map", e);
        }
    }
}
```

---

### M011: Create Audit Callback for Timestamps (Both Services)

**Files**: `**/config/AuditingCallback.java` in both services

**Purpose**: Replace Hibernate's `@CreationTimestamp` and `@UpdateTimestamp` with explicit callbacks

**Implementation**:

```java
@Component
public class AuditingCallback implements BeforeConvertCallback<Object> {

    @Override
    public Object onBeforeConvert(Object aggregate) {
        if (aggregate instanceof Auditable) {
            Auditable auditable = (Auditable) aggregate;
            Instant now = Instant.now();
            
            if (auditable.getCreatedAt() == null) {
                auditable.setCreatedAt(now);
            }
            auditable.setUpdatedAt(now);
        }
        return aggregate;
    }
}

// Marker interface for auditable entities
public interface Auditable {
    Instant getCreatedAt();
    void setCreatedAt(Instant createdAt);
    Instant getUpdatedAt();
    void setUpdatedAt(Instant updatedAt);
}
```

**Deliverable**: Callback registered, entities will implement `Auditable` interface

---

### M012: Transform Category Entity (Simple Aggregate Root)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Category.java`

**Current State**: JPA entity with `@OneToMany` to Product

**Target State**: JDBC aggregate root without bidirectional relationship

**Transformation Steps**:

1. **Remove JPA Annotations**:
   - `@Entity` → Remove
   - `@Table` → `@org.springframework.data.relational.core.mapping.Table`
   - `@Id` → `@org.springframework.data.annotation.Id`
   - `@GeneratedValue(strategy = GenerationType.UUID)` → Remove (manual UUID generation)
   - `@Column` → Remove (JDBC uses naming strategy)
   - `@CreationTimestamp`, `@UpdateTimestamp` → Remove (use callback)
   - `@OneToMany(mappedBy = "category", ...)` → **REMOVE** (no bidirectional refs)

2. **Add JDBC Annotations**:
   ```java
   @Table("categories")
   public class Category implements Auditable {
       @Id
       private UUID id;
       
       private String name;
       private String description;
       private Instant createdAt;
       private Instant updatedAt;
   ```

3. **Add Setters for JDBC Hydration**:
   ```java
   public void setId(UUID id) { this.id = id; }
   public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
   public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
   ```

4. **Manual UUID Generation** (in service layer before save):
   ```java
   // In CatalogService.createCategory()
   if (category.getId() == null) {
       category.setId(UUID.randomUUID());
   }
   categoryRepository.save(category);
   ```

5. **Remove Products Collection**:
   - Delete `private List<Product> products = new ArrayList<>();`
   - Delete `getProducts()` method
   - Categories no longer directly navigate to products

**Acceptance Criteria**:
- Entity compiles with Spring Data JDBC imports
- No JPA imports remaining
- Tests still failing (repository not yet updated)

---

### M013: Transform Product Entity (Aggregate Root with Reference)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Product.java`

**Current State**: JPA entity with `@ManyToOne` to Category, `@Version` for optimistic locking

**Target State**: JDBC aggregate root with `categoryId` reference, manual version management

**Transformation Steps**:

1. **Replace Annotations** (same pattern as Category)

2. **Replace Entity Reference with ID**:
   ```java
   // BEFORE (JPA):
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "category_id", nullable = false)
   private Category category;
   
   // AFTER (JDBC):
   @Column("category_id")
   private UUID categoryId;
   ```

3. **Preserve Optimistic Locking**:
   ```java
   @Version
   private Long version;
   
   public Long getVersion() { return version; }
   public void setVersion(Long version) { this.version = version; }
   ```
   Note: Spring Data JDBC supports `@Version`, but increments manually (no automatic JPA magic)

4. **Add Setters**:
   ```java
   public void setId(UUID id) { this.id = id; }
   public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
   public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
   public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
   ```

5. **Update Business Methods**:
   ```java
   // Remove any methods navigating to category entity
   // If needed, fetch category separately via repository
   ```

**Service Layer Impact** (to be addressed in M023):
- `CatalogService.createProduct()`: Set `categoryId` instead of `category` object
- Validate category existence before setting ID
- Optimistic locking exception changes from `OptimisticLockException` to `OptimisticLockingFailureException`

---

### M014: Transform Cart Entity (Aggregate Root with Children)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Cart.java`

**Current State**: JPA entity with `@OneToMany` cascade to CartItem

**Target State**: JDBC aggregate root with embedded `CartItem` collection

**Transformation Steps**:

1. **Replace Annotations**

2. **Transform Collection Mapping**:
   ```java
   // BEFORE (JPA):
   @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
   private List<CartItem> items = new ArrayList<>();
   
   // AFTER (JDBC):
   @MappedCollection(idColumn = "cart_id", keyColumn = "id")
   private Set<CartItem> items = new HashSet<>();
   ```
   
   **Why Set instead of List?**
   - JDBC aggregates work best with Sets (no order guarantees without explicit sorting)
   - If order matters, add `order_index` column and use `@MappedCollection(idColumn = "cart_id", keyColumn = "id")`

3. **Child Entity Changes** (CartItem):
   - Remove `@ManyToOne` back-reference to Cart
   - CartItem becomes a value object within Cart aggregate
   - Foreign key `cart_id` managed by JDBC via `@MappedCollection`

4. **Business Methods Update**:
   ```java
   public void addItem(CartItem item) {
       items.add(item);
       calculateSubtotal(); // Must explicitly recalculate
   }
   ```

**Key Insight**: Cart is the aggregate root; all CartItem changes go through Cart

---

### M015: Transform CartItem Entity (Child within Aggregate)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/CartItem.java`

**Current State**: JPA entity with `@ManyToOne` to Cart and Product

**Target State**: Child entity within Cart aggregate, stores `productId` only

**Transformation Steps**:

1. **Replace Annotations**

2. **Remove Back-Reference to Cart**:
   ```java
   // BEFORE (JPA):
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "cart_id", nullable = false)
   private Cart cart;
   
   // AFTER (JDBC):
   // No cart reference at all (managed by @MappedCollection in Cart)
   ```

3. **Replace Product Reference with ID**:
   ```java
   // BEFORE (JPA):
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "product_id", nullable = false)
   private Product product;
   
   // AFTER (JDBC):
   @Column("product_id")
   private UUID productId;
   ```

4. **Constructor Adjustment**:
   ```java
   // BEFORE:
   public CartItem(Cart cart, Product product, int quantity, BigDecimal priceSnapshot) { ... }
   
   // AFTER:
   public CartItem(UUID productId, int quantity, BigDecimal priceSnapshot) {
       this.productId = productId;
       this.quantity = quantity;
       this.priceSnapshot = priceSnapshot;
       this.subtotal = calculateSubtotal();
   }
   ```

5. **Remove Navigation Methods**:
   ```java
   // Delete: getProductSku(), getProductName(), isPriceUpToDate()
   // These require fetching Product separately via repository
   ```

**Service Layer Impact** (M023):
- `CartService.addItemToCart()`: Create CartItem with `productId` instead of Product entity
- Fetch product details separately when needed (e.g., for display)

---

### M016: Transform OrderCreatedOutbox Entity (Simple Aggregate)

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/OrderCreatedOutbox.java`

**Current State**: JPA entity with JSONB payload using Hibernate `@JdbcTypeCode`

**Target State**: JDBC aggregate with custom converter for JSONB

**Transformation Steps**:

1. **Replace Annotations** (standard pattern)

2. **JSONB Handling**:
   ```java
   // BEFORE (JPA with Hibernate):
   @JdbcTypeCode(SqlTypes.JSON)
   @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
   private String payload;
   
   // AFTER (JDBC):
   @Column("payload")
   private String payload; // Converter registered in JdbcConfig handles JSONB
   ```

3. **Timestamp Management**:
   ```java
   // BEFORE:
   @PrePersist
   protected void onCreate() {
       createdAt = LocalDateTime.now();
   }
   
   // AFTER:
   // Remove @PrePersist, use BeforeConvertCallback or set explicitly in service
   ```

4. **Enum Handling**:
   ```java
   @Enumerated(EnumType.STRING) // Keep as-is, JDBC supports string enums
   @Column(name = "status", length = 20, nullable = false)
   private Status status = Status.PENDING;
   ```

**Note**: This entity is relatively simple, minimal changes needed

---

### M017: Transform CheckoutIdempotency and OrderNumberSequence Entities

**Files**: `CheckoutIdempotency.java`, `OrderNumberSequence.java`

**Actions**: Apply standard transformation (replace annotations, add setters)

**Notes**: These are simple lookup tables with no relationships, straightforward migration

---

### M018: Transform Order Entity (Complex Aggregate Root)

**Files**: `order-management-service/src/main/java/com/ecommerce/order/model/Order.java`

**Current State**: JPA entity with `@OneToMany` to OrderItem, `@OneToOne` to PaymentTransaction, JSONB address

**Target State**: JDBC aggregate root with embedded OrderItems, separate PaymentTransaction aggregate

**Transformation Steps**:

1. **Replace Annotations** (standard pattern)

2. **JSONB Shipping Address**:
   ```java
   // BEFORE (Hibernate):
   @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
   @JdbcTypeCode(SqlTypes.JSON)
   private Map<String, String> shippingAddress;
   
   // AFTER (JDBC):
   @Column("shipping_address")
   private Map<String, String> shippingAddress; // Custom converter in JdbcConfig
   ```

3. **OrderItems Collection**:
   ```java
   // BEFORE (JPA):
   @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
   private List<OrderItem> items = new ArrayList<>();
   
   // AFTER (JDBC):
   @MappedCollection(idColumn = "order_id")
   private Set<OrderItem> items = new HashSet<>();
   ```

4. **PaymentTransaction Relationship**:
   
   **Option A: Separate Aggregate** (Recommended):
   ```java
   // Remove @OneToOne, replace with:
   // (No field at all; fetch via PaymentTransactionRepository.findByOrderId())
   ```
   
   **Option B: Embedded within Order Aggregate**:
   ```java
   @MappedCollection(idColumn = "order_id")
   private PaymentTransaction paymentTransaction;
   ```
   
   **Decision Point 🛑**: Discuss with human which approach fits business logic better
   - Separate: PaymentTransaction lifecycle independent of Order (can be updated separately)
   - Embedded: PaymentTransaction always loaded with Order (simpler queries, tighter coupling)

5. **Enum Handling**:
   ```java
   @Enumerated(EnumType.STRING) // Keep as-is
   @Column(name = "status", nullable = false, columnDefinition = "order_status")
   private OrderStatus status = OrderStatus.PENDING;
   ```
   Note: PostgreSQL ENUM type works with JDBC, column definition matches Flyway

**Service Layer Impact** (M025):
- Explicit save after Order mutations
- PaymentTransaction fetched/saved separately if using Option A

---

### M019: Transform OrderItem Entity (Child within Aggregate)

**Files**: `order-management-service/src/main/java/com/ecommerce/order/model/OrderItem.java`

**Actions**:

1. **Remove Back-Reference to Order**:
   ```java
   // BEFORE:
   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "order_id", nullable = false)
   private Order order;
   
   // AFTER:
   // No order reference (managed by @MappedCollection in Order)
   ```

2. **Keep ProductId as-is** (already a UUID, no FK):
   ```java
   @Column(name = "product_id", nullable = false)
   private UUID productId;
   ```

3. **Standard Annotation Replacement**

**Note**: OrderItem is immutable (no `updatedAt`), use only `@CreationTimestamp` or callback

---

### M020: Transform PaymentTransaction Entity

**Files**: `order-management-service/src/main/java/com/ecommerce/order/model/PaymentTransaction.java`

**Decision-Dependent**:

**If Separate Aggregate** (from M018 Option A):
- Remove `@OneToOne(mappedBy = "order", ...)`
- Add `@Column("order_id") private UUID orderId;`
- Becomes independent aggregate root

**If Embedded in Order** (from M018 Option B):
- Remove `@OneToOne`
- No explicit order reference (child of Order aggregate)
- Foreign key managed by `@MappedCollection` in Order

**Standard Transformations**:
- Replace JPA annotations with JDBC
- Add setters for hydration
- Enum handling as-is

---

### M021: Transform ProcessedEvent Entity

**Files**: `order-management-service/src/main/java/com/ecommerce/order/model/ProcessedEvent.java`

**Actions**: Straightforward transformation (simple entity, no relationships)

1. Replace annotations
2. Add setters
3. Use `@CreationTimestamp` or callback for `processedAt`

---

## Phase 3: Repository Layer Updates

**Purpose**: Convert JpaRepository interfaces to JDBC-compatible CrudRepository with SQL queries

### M022: Update Repositories (Customer Service)

**Files**: All repositories in `customer-facing-service/src/main/java/com/ecommerce/customer/repository/`

**CategoryRepository.java**:
```java
// BEFORE:
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByName(String name);
    boolean existsByName(String name);
}

// AFTER:
public interface CategoryRepository extends CrudRepository<Category, UUID> {
    Optional<Category> findByName(String name);
    boolean existsByName(String name);
}
```
**Changes**: Replace `JpaRepository` → `CrudRepository`, derived queries work the same

---

**ProductRepository.java**:

Key Changes:
1. Replace `JpaRepository` → `CrudRepository` or `PagingAndSortingRepository` (if pagination needed)
2. Replace JPQL `@Query` with SQL
3. Remove `@Lock(LockModeType.PESSIMISTIC_WRITE)` → Use SQL `SELECT ... FOR UPDATE`

```java
// BEFORE (JPQL):
@Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.inventoryQuantity > 0 AND p.isActive = true")
List<Product> findInStockProductsByCategory(@Param("categoryId") UUID categoryId);

// AFTER (SQL):
@Query("SELECT * FROM products WHERE category_id = :categoryId AND inventory_quantity > 0 AND is_active = true")
List<Product> findInStockProductsByCategory(@Param("categoryId") UUID categoryId);
```

**Pessimistic Locking for Inventory**:
```java
// BEFORE (JPA):
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Product> findByIdWithLock(UUID id);

// AFTER (JDBC):
@Query("SELECT * FROM products WHERE id = :id FOR UPDATE")
Optional<Product> findByIdWithLock(@Param("id") UUID id);
```

**Pagination**:
```java
// Change signature to use Pageable
public interface ProductRepository extends PagingAndSortingRepository<Product, UUID>, CrudRepository<Product, UUID> {
    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);
}
```

---

**CartRepository.java**:

Key Changes:
1. Remove `@EntityGraph` (no lazy loading in JDBC, always fetches full aggregate)
2. Replace JPQL with SQL

```java
// BEFORE (with EntityGraph):
@EntityGraph(attributePaths = {"items", "items.product"})
Optional<Cart> findWithItemsBySessionId(String sessionId);

// AFTER (JDBC always fetches items):
Optional<Cart> findBySessionId(String sessionId); // Items loaded automatically via @MappedCollection
```

**Delete Queries**:
```java
// BEFORE:
@Modifying
@Query("DELETE FROM Cart c WHERE c.sessionId = :sessionId")
int deleteBySessionId(@Param("sessionId") String sessionId);

// AFTER (SQL):
@Modifying
@Query("DELETE FROM carts WHERE session_id = :sessionId")
int deleteBySessionId(@Param("sessionId") String sessionId);
```

---

**CartItemRepository.java**:

May become **unnecessary** if CartItem is always accessed via Cart aggregate. Consider:
- If CartItem queries needed independently: Keep repository, update to JDBC
- If CartItem only manipulated via Cart: Delete repository, use Cart methods

**Decision Point 🛑**: Discuss with human whether CartItemRepository should exist (aggregate boundary enforcement)

---

**OrderCreatedOutboxRepository, CheckoutIdempotencyRepository, OrderNumberSequenceRepository**:
- Replace `JpaRepository` → `CrudRepository`
- Update any JPQL queries to SQL
- Remove `@Transactional` from repository interface (transactions belong in service layer)

---

### M023: Update Repositories (Order Service)

**Files**: All repositories in `order-management-service/src/main/java/com/ecommerce/order/repository/`

**OrderRepository.java**:

```java
// BEFORE:
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o WHERE " +
           "(:customerEmail IS NULL OR o.customerEmail = :customerEmail) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR o.createdAt <= :endDate)")
    Page<Order> findWithFilters(...);
}

// AFTER (SQL):
public interface OrderRepository extends PagingAndSortingRepository<Order, UUID>, CrudRepository<Order, UUID> {
    @Query("SELECT * FROM orders WHERE " +
           "(:customerEmail IS NULL OR customer_email = :customerEmail) AND " +
           "(:status IS NULL OR status = CAST(:status AS order_status)) AND " + // Explicit cast for ENUM
           "(:startDate IS NULL OR created_at >= :startDate) AND " +
           "(:endDate IS NULL OR created_at <= :endDate)")
    Page<Order> findWithFilters(...);
}
```

**Note on Enums**: PostgreSQL custom types require explicit casting in raw SQL

---

**PaymentTransactionRepository.java**:

If PaymentTransaction is separate aggregate (M018 Option A):
```java
public interface PaymentTransactionRepository extends CrudRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByOrderId(UUID orderId);
}
```

If embedded in Order (M018 Option B):
- Repository may not be needed (PaymentTransaction fetched via Order)

---

**OrderItemRepository.java**:

Similar decision as CartItemRepository:
- If always accessed via Order aggregate: Consider removing
- If independent analytics queries needed: Keep and update to JDBC

---

**ProcessedEventRepository.java**:

Straightforward update:
```java
public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, UUID> {
    boolean existsByEventId(UUID eventId);
}
```

---

## Phase 4: Service Layer Adjustments

**Purpose**: Update service methods to explicitly manage persistence (no lazy loading, manual save calls)

### M024: Update CartService

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CartService.java`

**Key Changes**:

1. **Explicit Save After Mutations**:
   ```java
   // BEFORE (JPA):
   public Cart addItemToCart(String sessionId, UUID productId, int quantity) {
       Cart cart = getCart(sessionId);
       cart.addItem(cartItem); // JPA persists on transaction commit
       return cart;
   }
   
   // AFTER (JDBC):
   public Cart addItemToCart(String sessionId, UUID productId, int quantity) {
       Cart cart = getCart(sessionId);
       cart.addItem(cartItem);
       cart = cartRepository.save(cart); // Explicit save required
       saveToRedis(cart);
       return cart;
   }
   ```

2. **ID Generation Before Save**:
   ```java
   // Generate UUIDs for cart and items before save
   if (cart.getId() == null) {
       cart.setId(UUID.randomUUID());
   }
   for (CartItem item : cart.getItems()) {
       if (item.getId() == null) {
           item.setId(UUID.randomUUID());
       }
   }
   cartRepository.save(cart);
   ```

3. **No Lazy Loading**:
   ```java
   // JDBC always fetches full aggregate (cart + items)
   // No need for special "findWithItems" methods
   Cart cart = cartRepository.findBySessionId(sessionId).orElse(null);
   // cart.getItems() is already populated
   ```

4. **Product Fetching**:
   ```java
   // CartItem now stores productId, not Product entity
   // Fetch product separately when needed:
   Product product = productRepository.findById(cartItem.getProductId())
       .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
   ```

**Acceptance Criteria**:
- Every cart mutation ends with `cartRepository.save(cart)`
- UUIDs generated before save (not by database)
- Redis serialization still works (may need to remove Jackson Hibernate module)

---

### M025: Update CheckoutService

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CheckoutService.java`

**Key Changes**:

1. **Optimistic Locking Exception**:
   ```java
   // BEFORE:
   catch (OptimisticLockException e) { ... }
   
   // AFTER:
   catch (OptimisticLockingFailureException e) { ... }
   ```

2. **Product Locking**:
   ```java
   // findByIdWithLock now uses SQL SELECT FOR UPDATE (from M022)
   Product product = productRepository.findByIdWithLock(productId)
       .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
   product.decrementInventory(quantity);
   productRepository.save(product); // Explicit save
   ```

3. **Outbox Persistence**:
   ```java
   // Generate ID before save
   OrderCreatedOutbox outbox = new OrderCreatedOutbox(aggregateId, payload);
   outbox.setId(UUID.randomUUID());
   outboxRepository.save(outbox);
   ```

4. **Transaction Isolation**:
   ```java
   @Transactional(isolation = Isolation.SERIALIZABLE) // Still works with JDBC
   public CheckoutResponse checkout(CheckoutRequest request) { ... }
   ```

---

### M026: Update CatalogService

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CatalogService.java`

**Key Changes**:

1. **Category-Product Relationship**:
   ```java
   // BEFORE:
   public Product createProduct(CreateProductRequest request) {
       Category category = categoryRepository.findById(request.categoryId())
           .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
       Product product = new Product(..., category);
       return productRepository.save(product);
   }
   
   // AFTER:
   public Product createProduct(CreateProductRequest request) {
       // Validate category exists
       if (!categoryRepository.existsById(request.categoryId())) {
           throw new ResourceNotFoundException("Category not found: " + request.categoryId());
       }
       Product product = new Product(...);
       product.setId(UUID.randomUUID());
       product.setCategoryId(request.categoryId()); // Store ID, not entity
       return productRepository.save(product);
   }
   ```

2. **Update Operations**:
   ```java
   public Product updateProduct(UUID productId, UpdateProductRequest request) {
       Product product = productRepository.findById(productId)
           .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
       
       // Update fields
       product.setName(request.name());
       product.setPrice(request.price());
       
       // Explicit save (no dirty checking)
       return productRepository.save(product);
   }
   ```

---

### M027: Update OrderProcessingService

**Files**: `order-management-service/src/main/java/com/ecommerce/order/service/OrderProcessingService.java`

**Key Changes**:

1. **Order Creation**:
   ```java
   // Generate ID before save
   Order order = createOrderFromEvent(event);
   order.setId(UUID.randomUUID());
   order = orderRepository.save(order);
   ```

2. **OrderItem Handling**:
   ```java
   // BEFORE (JPA cascade):
   List<OrderItem> items = createOrderItemsFromEvent(event, order);
   items.forEach(order::addItem); // JPA saves items automatically
   
   // AFTER (JDBC):
   List<OrderItem> items = createOrderItemsFromEvent(event, order);
   items.forEach(item -> {
       item.setId(UUID.randomUUID());
       order.addItem(item);
   });
   order = orderRepository.save(order); // Saves order + items together (aggregate)
   ```

3. **PaymentTransaction** (if separate aggregate):
   ```java
   // BEFORE (JPA cascade):
   PaymentTransaction tx = new PaymentTransaction(order, ...);
   order.setPaymentTransaction(tx); // Saved via cascade
   
   // AFTER (JDBC - separate aggregate):
   order = orderRepository.save(order); // Save order first to get ID
   
   PaymentTransaction tx = new PaymentTransaction();
   tx.setId(UUID.randomUUID());
   tx.setOrderId(order.getId());
   tx.setAmount(order.getSubtotal());
   tx.setStatus(PaymentStatus.PENDING);
   paymentTransactionRepository.save(tx); // Explicit save
   ```

4. **Idempotency Check** (unchanged):
   ```java
   if (processedEventRepository.existsByEventId(event.eventId())) {
       return; // Works the same with JDBC
   }
   ```

---

### M028: Update OrderQueryService and PaymentCompletedService

**Files**: `OrderQueryService.java`, `PaymentCompletedService.java`

**Key Changes**:
- Explicit `save()` after any Order or PaymentTransaction mutation
- UUID generation before save
- No lazy loading assumptions (Order always fetches items)
- If PaymentTransaction is separate aggregate, fetch via repository:
  ```java
  PaymentTransaction tx = paymentTransactionRepository.findByOrderId(orderId)
      .orElseThrow(() -> new IllegalStateException("Payment transaction not found"));
  ```

---

## Phase 5: Configuration & Dependencies

**Purpose**: Remove JPA dependencies, activate JDBC configuration

### M029: Update pom.xml (Customer Service)

**Files**: `customer-facing-service/pom.xml`

**Changes**:

1. **Remove JPA Dependencies**:
```xml
   <!-- REMOVE:
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-jpa</artifactId>
   </dependency>
   -->
   ```

2. **Add JDBC Dependency**:
   ```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
```

3. **Remove Jackson Hibernate Module** (used for Redis serialization):
   ```xml
   <!-- REMOVE:
   <dependency>
       <groupId>com.fasterxml.jackson.datatype</groupId>
       <artifactId>jackson-datatype-hibernate5-jakarta</artifactId>
   </dependency>
   -->
   ```
   **Impact**: Update Redis serialization configuration to not register Hibernate module

4. **Keep All Other Dependencies**:
   - PostgreSQL driver
   - Flyway
   - Redis, Kafka
   - Validation, Actuator, Security
   - Micrometer, MapStruct

**Verification**:
```bash
mvn dependency:tree > dependency-tree-after.txt
diff dependency-tree-before.txt dependency-tree-after.txt
# Ensure no Hibernate transitive dependencies remain
```

---

### M030: Update pom.xml (Order Service)

**Files**: `order-management-service/pom.xml`

**Changes**: Same as M029 (remove JPA, add JDBC)

**Note**: Order service doesn't use Redis, so no Jackson Hibernate module to remove

---

### M031: Update Application Configuration

**Files**: `application.yml`, `application-test.yml` in both services

**Changes**:

1. **Remove JPA-Specific Properties**:
   ```yaml
   # REMOVE:
   spring:
     jpa:
       hibernate:
         ddl-auto: validate
       show-sql: false
       open-in-view: false
   ```

2. **Add JDBC Properties** (if needed):
   ```yaml
   spring:
     data:
       jdbc:
         repositories:
           enabled: true
   ```
   (Usually default config works, explicit properties rarely needed)

3. **Keep Database Connection Settings** (unchanged):
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/customer_db
       username: postgres
       password: postgres
   ```

4. **Flyway Settings** (unchanged):
   ```yaml
   spring:
     flyway:
       enabled: true
       locations: classpath:db/migration
   ```

---

### M032: Update Redis Configuration

**Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/RedisConfig.java`

**Changes**:

Remove Hibernate5Module registration:
```java
// BEFORE:
@Bean
public Jackson2JsonRedisSerializer<Cart> cartSerializer() {
    Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(Cart.class);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Hibernate5JakartaModule()); // REMOVE THIS
    serializer.setObjectMapper(mapper);
    return serializer;
}

// AFTER:
@Bean
public Jackson2JsonRedisSerializer<Cart> cartSerializer() {
    Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(Cart.class);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule()); // Keep for Instant serialization
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    serializer.setObjectMapper(mapper);
    return serializer;
}
```

**Acceptance Criteria**: Redis serialization/deserialization works without JPA-specific modules

---

## Phase 6: Test Fixes & Validation

**Purpose**: Make all tests pass with JDBC implementation

### M033: Fix Service Unit Tests (Customer Service)

**Files**: `CartServiceTest.java`, `CheckoutServiceTest.java`, `CatalogServiceTest.java`

**Actions**:

1. **Update Mock Repository Behavior**:
   ```java
   // Mocks must now set IDs on save
   when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
       Cart cart = invocation.getArgument(0);
       if (cart.getId() == null) {
           cart.setId(UUID.randomUUID());
       }
       // Also set IDs for CartItems
       for (CartItem item : cart.getItems()) {
           if (item.getId() == null) {
               item.setId(UUID.randomUUID());
           }
       }
       return cart;
   });
   ```

2. **Update Entity Construction in Tests**:
   ```java
   // BEFORE:
   Product product = new Product("SKU", "Name", "Desc", price, 100, category);
   
   // AFTER:
   Product product = new Product("SKU", "Name", "Desc", price, 100, null);
   product.setId(UUID.randomUUID());
   product.setCategoryId(categoryId); // Use ID instead of entity
   ```

3. **Verify Explicit Save Calls**:
   ```java
   // Tests should verify save() is called after mutations
   verify(cartRepository, times(1)).save(cart);
   ```

**Run Tests**: `mvn test -Dtest=CartServiceTest`

**Expected Result**: All tests pass

---

### M034: Fix Service Unit Tests (Order Service)

**Files**: `OrderProcessingServiceTest.java`, `OrderQueryServiceTest.java`, `PaymentCompletedServiceTest.java`

**Actions**: Similar to M033
- Update mocks to set IDs
- Handle Order/OrderItem aggregate properly
- If PaymentTransaction is separate, mock its repository too

**Run Tests**: `mvn test -pl order-management-service`

**Expected Result**: All tests pass

---

### M035: Fix Contract Tests (Both Services)

**Files**: All `*ContractTest.java`

**Actions**:
1. Verify Testcontainers PostgreSQL still works
2. Ensure Spring Boot context loads with JDBC configuration
3. Contract tests should mostly pass (they test external APIs, not internal persistence)

**If Failures Occur**:
- Check for direct database assertions using EntityManager → Replace with repository queries
- Verify test data setup doesn't rely on JPA cascading → Set IDs explicitly

**Run Tests**: `mvn test -Dtest=*ContractTest`

**Expected Result**: All contract tests pass

---

### M036: Run Full Test Suite (Customer Service)

**Command**: `mvn clean verify -pl customer-facing-service`

**Expected Result**: All tests pass (unit + contract + integration)

**If Failures Occur**:
- Check logs for SQL errors (JPQL remnants, wrong column names)
- Verify JSONB converters working
- Check transaction boundaries (JDBC transactions work the same, but explicit save needed)

---

### M037: Run Full Test Suite (Order Service)

**Command**: `mvn clean verify -pl order-management-service`

**Expected Result**: All tests pass

---

### M038: Integration Testing with Full Stack

**Actions**:
1. Start infrastructure: `docker-compose up -d`
2. Run both services: `mvn spring-boot:run` (in separate terminals)
3. Execute manual test scenarios from `manual-tests/test_runner.py`
4. Verify:
   - Checkout flow works end-to-end
   - Inventory decrement persists correctly
   - Order processing via Kafka successful
   - Redis cart caching works
   - No lazy loading errors in logs

**Deliverable**: All manual tests pass, services operational

---

## Phase 7: Performance & Quality Validation

**Purpose**: Ensure JDBC migration maintains or improves performance and code quality

### M039: Query Analysis & Optimization

**Actions**:
1. Enable SQL logging: `logging.level.org.springframework.jdbc.core=DEBUG`
2. Run checkout flow, capture SQL queries
3. Compare query count vs. baseline (JPA):
   - Expected: Fewer queries (no N+1 problems from lazy loading)
   - Document actual query count for reference
4. Analyze query plans:
   ```sql
   EXPLAIN ANALYZE SELECT * FROM carts WHERE session_id = 'test-123';
   ```
5. Document findings in `/docs/jdbc-performance-analysis.md`

**Acceptance Criteria**:
- No N+1 query patterns detected
- Query count documented and compared to JPA baseline
- All queries use appropriate indexes (verified with EXPLAIN)
- Findings documented for future optimization opportunities

---

### M040: Memory Profiling

**Actions**:
1. Run JVM with profiling: `java -Xmx512m -XX:+PrintGCDetails -jar target/customer-facing-service.jar`
2. Execute load test (e.g., 1000 checkout requests)
3. Compare memory usage vs. JPA baseline
4. Expected: Different memory profile (no Hibernate session cache, no lazy loading proxies)
5. Document memory characteristics for operational tuning

**Deliverable**: Memory profile report documenting JDBC memory behavior vs. JPA

---

### M041: Load Testing

**Actions**:
1. Use load testing tool (JMeter, Gatling, or k6)
2. Test scenarios:
   - 100 concurrent users browsing catalog
   - 50 concurrent checkouts
   - 20 concurrent order lookups
3. Measure:
   - Throughput (requests/sec)
   - Latency (p50, p95, p99)
   - Error rate
4. Compare vs. JPA baseline for reference

**Acceptance Criteria**:
- Throughput documented and compared to JPA baseline
- Latency metrics captured (p50, p95, p99)
- 0% error rate under load
- Results documented for operational planning

---

### M042: Code Quality & Coverage

**Actions**:
1. Run SonarQube analysis: `mvn sonar:sonar`
2. Verify:
   - 0 bugs, 0 vulnerabilities
   - Code coverage ≥ baseline (target: 80%+)
   - Technical debt ≤ baseline
3. Address any new code smells introduced during migration

**Acceptance Criteria**: SonarQube gate passes

---

### M043: Dependency Audit

**Actions**:
1. Run `mvn dependency:tree` and verify no Hibernate/JPA dependencies
2. Check for version conflicts: `mvn dependency:analyze`
3. Update documentation with new dependency structure

**Deliverable**: Clean dependency tree, no JPA remnants

---

## Phase 8: Documentation & Rollout

**Purpose**: Document changes, prepare rollout plan, enable team

### M044: Update Architecture Documentation

**Files**: `/docs/architecture.md`, `README.md`

**Actions**:
1. Update architecture diagrams to reflect JDBC (no lazy loading, explicit aggregates)
2. Document aggregate boundaries:
   - Cart → CartItem (aggregate)
   - Order → OrderItem (aggregate)
   - PaymentTransaction (separate or embedded - based on M018 decision)
3. Document persistence patterns:
   - Explicit save required after mutations
   - UUID generation before save
   - No lazy loading (eager by default)
4. Update quickstart guide with JDBC-specific notes

---

### M045: Update Developer Guide

**Files**: `/docs/developer-guide.md` (create if not exists)

**Content**:
1. **Aggregate Design Patterns**:
   - When to use `@MappedCollection`
   - How to structure parent-child relationships
   - Cross-aggregate references (use IDs, not entities)

2. **Common Patterns**:
   ```java
   // Creating new entities
   Entity entity = new Entity(...);
   entity.setId(UUID.randomUUID());
   entity = repository.save(entity);
   
   // Updating entities
   Entity entity = repository.findById(id).orElseThrow();
   entity.updateField(value);
   entity = repository.save(entity); // Explicit save
   
   // Handling aggregates
   Parent parent = repository.findById(id).orElseThrow();
   Child child = new Child(...);
   child.setId(UUID.randomUUID());
   parent.addChild(child);
   parent = repository.save(parent); // Saves parent + children
   ```

3. **Gotchas**:
   - Forgetting explicit save → data loss
   - Assuming lazy loading → NullPointerException
   - Using JPA exception types → Wrong exception caught

---

### M046: Update tasks.md

**Files**: `/specs/001-e-commerce-platform/tasks.md`

**Actions**:
1. Mark T054 and T055 as complete
2. Update task descriptions to reflect JDBC implementation
3. Add notes about JDBC-specific considerations in future tasks

---

### M047: Create Rollout Plan

**Files**: `/docs/jdbc-rollout-plan.md`

**Content**:
1. **Rollout Stages**:
   - Stage 1: Deploy to dev environment, monitor for 48h
   - Stage 2: Deploy to staging, run full regression suite
   - Stage 3: Canary deploy to 10% production traffic
   - Stage 4: Full production rollout
   - Stage 5: Monitor for 1 week, validate stability

2. **Rollback Plan**:
   - Git tag `pre-jdbc-migration` ready
   - Database schema unchanged (instant rollback to JPA if needed)
   - Monitoring alerts configured for error rate spikes

3. **Success Metrics**:
   - Error rate stable (no significant increase from baseline)
   - Latency stable (p50, p95, p99 within acceptable range)
   - No data inconsistency reports
   - All contract tests passing
   - Integration tests green

---

### M048: Team Training Session

**Actions**:
1. Schedule 2-hour training session with development team
2. Cover:
   - Why JDBC over JPA (benefits, trade-offs)
   - Aggregate design principles
   - Code examples: Before/After
   - Common pitfalls and how to avoid them
3. Q&A session
4. Share recorded session for future onboarding

---

### M049: Update CI/CD Pipeline

**Files**: `.github/workflows/`, Jenkins files, or equivalent

**Actions**:
1. Verify CI pipeline runs with JDBC dependencies
2. Update Dockerfile if needed (Hibernate removal may reduce image size)
3. Add performance benchmarking step (compare vs. baseline)
4. Configure alerts for test failure or performance regression

---

## Phase 9: Post-Migration Validation

**Purpose**: Monitor production, validate success, optimize further

### M050: Production Monitoring (Week 1)

**Actions**:
1. Monitor key metrics:
   - Error rate
   - Latency (p50, p95, p99)
   - Throughput
   - Memory usage
   - Database connection pool saturation
2. Set up alerts for:
   - Error rate spikes
   - Significant latency changes
   - Memory leak patterns (heap usage increasing over time)
3. Daily standups to review metrics
4. Compare trends to pre-migration baseline

**Deliverable**: Metrics dashboard showing stable operation, documented comparison to JPA baseline

---

### M051: Identify Optimization Opportunities

**Actions**:
1. Review slow query logs
2. Identify read-heavy aggregates that could benefit from:
   - Projection queries (fetch subset of fields)
   - Custom result mappings
   - Caching layer
3. Document optimization backlog

**Example Optimization**:
```java
// Instead of fetching full Order aggregate for list view:
@Query("SELECT id, order_number, customer_name, status, subtotal, created_at FROM orders WHERE status = :status")
List<OrderSummary> findOrderSummariesByStatus(@Param("status") String status);

// Where OrderSummary is a projection interface or DTO
```

---

### M052: Performance Tuning

**Actions** (based on M051 findings):
1. Implement projection queries for read-heavy endpoints if beneficial
2. Optimize aggregate boundaries if needed (split large aggregates)
3. Add database indexes if query analysis revealed missing indexes
4. Configure connection pool tuning (HikariCP settings)
5. Apply optimizations based on actual performance characteristics

**Acceptance Criteria**: Identified performance optimizations implemented and validated, results documented

---

### M053: Celebrate Success! 🎉

**Actions**:
1. Team retrospective: What went well, what could be improved
2. Document lessons learned for future migrations
3. Share success story internally (blog post, tech talk)
4. Archive JPA implementation branch (don't delete, keep for reference)

---

## Migration Task Summary

| Phase | Tasks | Estimated Effort | Risk Level |
|-------|-------|-----------------|------------|
| 0. Prerequisites | M001-M002 | 4 hours | Low |
| 1. Test Preparation | M003-M008 | 12 hours | Medium |
| 2. Entity Transformation | M009-M021 | 24 hours | High |
| 3. Repository Updates | M022-M023 | 8 hours | Medium |
| 4. Service Adjustments | M024-M028 | 16 hours | High |
| 5. Configuration & Dependencies | M029-M032 | 4 hours | Medium |
| 6. Test Fixes | M033-M038 | 16 hours | High |
| 7. Performance & Quality | M039-M043 | 12 hours | Medium |
| 8. Documentation & Rollout | M044-M049 | 12 hours | Low |
| 9. Post-Migration | M050-M053 | 8 hours | Low |
| **TOTAL** | **53 tasks** | **116 hours** | **Mixed** |

**Recommended Approach**: Execute in 2-3 week sprint with 2-3 developers

---

## Risk Mitigation

### High-Risk Areas

1. **Complex Aggregates** (Order with OrderItem + PaymentTransaction):
   - Mitigation: Extensive testing of aggregate saves, consider benchmark vs. JPA
   - Checkpoint: M018 decision on PaymentTransaction aggregate boundary

2. **Forgotten Explicit Save Calls**:
   - Mitigation: Code review checklist, add static analysis rule if possible
   - Testing: Every service test should verify `save()` invocations

3. **JSONB Serialization** (shippingAddress):
   - Mitigation: Unit tests for converters, verify against production data samples
   - Fallback: Keep Hibernate JSONB approach as reference

4. **Redis Serialization** (Cart caching):
   - Mitigation: Test Redis round-trip early (M032), have fallback to PostgreSQL-only

5. **Optimistic Locking** (Product inventory):
   - Mitigation: Verify version increment behavior, load test concurrent updates
   - Fallback: Switch to pessimistic locking if optimistic proves unreliable

### Rollback Scenarios

| Scenario | Trigger | Rollback Action | Recovery Time |
|----------|---------|-----------------|---------------|
| Tests fail post-migration | > 5% test failure rate | Revert to `pre-jdbc-migration` tag | 1 hour |
| Production errors spike | Error rate significantly above baseline | Emergency rollback to JPA version | 30 minutes |
| Functional issues | Unexpected behavior, data integrity concerns | Rollback + root cause analysis | 1 hour |
| Data inconsistency | Reports of missing/incorrect data | Immediate rollback, data audit | 2 hours |

---

## Success Criteria (Final Gate)

- [ ] All 53 migration tasks completed
- [ ] 100% of existing tests passing (baseline maintained)
- [ ] No JPA/Hibernate dependencies in `dependency:tree`
- [ ] Code coverage ≥ 80% (baseline maintained)
- [ ] Performance benchmarks executed and results documented
- [ ] No N+1 query patterns detected in critical paths
- [ ] Team training completed, documentation updated
- [ ] SonarQube gate passing (0 bugs, 0 vulnerabilities)
- [ ] All contract tests passing (external API unchanged)
- [ ] Integration tests passing with Testcontainers (PostgreSQL, Redis, Kafka)

---

## Checkpoints for Human Review 🛑

1. **M002**: Review dependency audit before proceeding
2. **M003**: Review test coupling analysis (decide which tests to modify)
3. **M018**: Decision on PaymentTransaction aggregate boundary (separate vs. embedded)
4. **M022**: Decision on CartItemRepository necessity (aggregate boundary)
5. **M038**: Full integration testing validation before proceeding to performance phase
6. **M043**: Final dependency audit before documentation phase
7. **M049**: Rollout plan review before production deployment

---

## Appendix: Reference Code Snippets

### A1: JDBC Entity Template

```java
@Table("entity_name")
public class EntityName implements Auditable {
    
    @Id
    private UUID id;
    
    private String field;
    
    @Version
    private Long version;
    
    private Instant createdAt;
    private Instant updatedAt;
    
    // JDBC requires public/package setters for hydration
    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Getters
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

### A2: Parent-Child Aggregate Template

```java
// Parent (Aggregate Root)
@Table("parents")
public class Parent implements Auditable {
    @Id
    private UUID id;
    
    @MappedCollection(idColumn = "parent_id")
    private Set<Child> children = new HashSet<>();
    
    public void addChild(Child child) {
        children.add(child);
    }
    
    // Setters, getters, Auditable impl
}

// Child (Part of Parent Aggregate)
@Table("children")
public class Child {
    @Id
    private UUID id;
    
    // No back-reference to Parent!
    // Foreign key managed by @MappedCollection
    
    private String data;
    
    public void setId(UUID id) { this.id = id; }
    public UUID getId() { return id; }
}
```

### A3: Service Layer Pattern

```java
@Service
@Transactional
public class AggregateService {
    
    private final ParentRepository parentRepository;
    
    public Parent createParent(CreateRequest request) {
        // 1. Create entity
        Parent parent = new Parent(request.data());
        
        // 2. Generate ID before save
        parent.setId(UUID.randomUUID());
        
        // 3. Add children with IDs
        Child child = new Child(request.childData());
        child.setId(UUID.randomUUID());
        parent.addChild(child);
        
        // 4. Explicit save
        parent = parentRepository.save(parent);
        
        return parent;
    }
    
    public Parent updateParent(UUID id, UpdateRequest request) {
        // 1. Fetch
        Parent parent = parentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        
        // 2. Mutate
        parent.updateData(request.data());
        
        // 3. Explicit save
        parent = parentRepository.save(parent);
        
        return parent;
    }
}
```

### A4: JSONB Converter Registration

```java
@Configuration
@EnableJdbcAuditing
public class JdbcConfig extends AbstractJdbcConfiguration {
    
    @Bean
    public JdbcCustomConversions jdbcCustomConversions(ObjectMapper objectMapper) {
        return new JdbcCustomConversions(List.of(
            new MapToJsonbConverter(objectMapper),
            new JsonbToMapConverter(objectMapper)
        ));
    }
}
```

---

## Conclusion

This migration plan provides a comprehensive, step-by-step guide to transition from Spring Data JPA to Spring Data JDBC. It prioritizes:

1. **Safety**: Test-driven approach, rollback plan, checkpoints
2. **Clarity**: Explicit transformation rules, code examples
3. **Completeness**: Covers all layers (model, repository, service, config, tests)
4. **Pragmatism**: Identifies decision points, trade-offs, and risks

Execute tasks sequentially, validate at each checkpoint, and maintain open communication with the team. The result will be a leaner, more transparent persistence layer with improved performance and maintainability.

**Good luck with the migration!** 🚀