# JPA → JDBC Migration Handoff

**Date**: 2025-10-05  
**Branch**: `migration/jpa-to-jdbc`  
**Repository**: https://github.com/tazabreu/sample-ecommerce-platform  
**Commit**: `854996f` - "🔧 chore(deps): replace Spring Data JPA with Spring Data JDBC (M029-M030)"

---

## 🎯 Mission

Continue the Spring Data JPA → Spring Data JDBC migration following the comprehensive guide in `MIGRATION_JPA_JDBC.md`. The foundation has been laid; now we need to transform entities, repositories, services, and configurations.

---

## ✅ Completed Work (Phase 0 & Partial Phase 5)

### Phase 0: Prerequisites & Discovery ✅
- **M001**: Environment validated, migration branch created, rollback tag `pre-jdbc-migration` set
- **M002**: Comprehensive dependency audit completed (see `docs/jpa-dependencies-audit.md`)
- **Baseline documented**: `MIGRATION_BASELINE.md` - 4 known failing tests due to PostgreSQL ENUM casting (will be fixed by migration)

### Phase 5: Configuration & Dependencies (Partial) ✅
- **M029**: Customer service `pom.xml` updated
  - ✅ `spring-boot-starter-data-jpa` → `spring-boot-starter-data-jdbc`
  - ✅ `jackson-datatype-hibernate5-jakarta` → `jackson-datatype-jsr310`
- **M030**: Order service `pom.xml` updated
  - ✅ `spring-boot-starter-data-jpa` → `spring-boot-starter-data-jdbc`

---

## 🚧 Remaining Work (In Order of Execution)

### Phase 2: Entity Model Transformation (M009-M021)

**M009**: Create JDBC Configuration (Customer Service)
- File: `customer-facing-service/src/main/java/com/ecommerce/customer/config/JdbcConfig.java`
- Extend `AbstractJdbcConfiguration`
- Register custom converters for JSONB (if needed)
- Enable auditing with `@EnableJdbcAuditing`
- Configure naming strategy (snake_case)

**M010**: Create JDBC Configuration (Order Service)
- File: `order-management-service/src/main/java/com/ecommerce/order/config/JdbcConfig.java`
- Same as M009, plus converters for `Map<String, String>` ↔ JSONB

**M011**: Create Audit Callback for Timestamps (Both Services)
- Files: `**/config/AuditingCallback.java` and `**/model/Auditable.java`
- Replace Hibernate's `@CreationTimestamp` and `@UpdateTimestamp`

**M012-M021**: Transform Entities (11 total)

**Customer Service Entities** (7):
1. `Category.java` - Simple aggregate root, remove `@OneToMany` to Product
2. `Product.java` - Replace `category` entity with `categoryId` UUID, keep `@Version`
3. `Cart.java` - Aggregate root with `@MappedCollection` for CartItems
4. `CartItem.java` - Child entity, remove back-refs, store `productId` only
5. `OrderCreatedOutbox.java` - Simple entity, custom JSONB converter
6. `CheckoutIdempotency.java` - Simple entity
7. `OrderNumberSequence.java` - Simple entity

**Order Service Entities** (4):
1. `Order.java` - Complex aggregate with `@MappedCollection` for OrderItems, JSONB address
2. `OrderItem.java` - Child entity within Order aggregate
3. `PaymentTransaction.java` - **Decision needed**: Separate aggregate or embedded?
4. `ProcessedEvent.java` - Simple entity

**Key Transformation Rules**:
- `@Entity` → Remove
- `@Table` → `@org.springframework.data.relational.core.mapping.Table`
- `@Id` → `@org.springframework.data.annotation.Id`
- `@GeneratedValue` → Remove (manual UUID generation)
- `@Column` → Remove (naming strategy handles it)
- `@OneToMany` → `@MappedCollection(idColumn = "parent_id")`
- `@ManyToOne` → Replace with UUID field
- `@CreationTimestamp`/`@UpdateTimestamp` → Remove (use callback)
- Add setters for all fields (JDBC needs them for hydration)

### Phase 3: Repository Layer Updates (M022-M023)

**M022**: Update Repositories (Customer Service) - 7 repositories
- `JpaRepository` → `CrudRepository` or `PagingAndSortingRepository`
- JPQL `@Query` → SQL `@Query`
- Remove `@EntityGraph` (JDBC always eager loads aggregates)
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` → Keep or use SQL `FOR UPDATE`

**M023**: Update Repositories (Order Service) - 4 repositories
- Same transformations as M022
- **Critical**: Fix ENUM casting in `OrderRepository.findWithFilters()`
  ```sql
  -- Add explicit cast:
  (:status IS NULL OR status = CAST(:status AS order_status))
  ```

### Phase 4: Service Layer Adjustments (M024-M028)

**M024**: Update CartService
- Add explicit `cartRepository.save(cart)` after mutations
- Generate UUIDs before save: `if (cart.getId() == null) cart.setId(UUID.randomUUID())`
- Fetch products separately (no lazy loading)

**M025**: Update CheckoutService
- Change exception: `OptimisticLockException` → `OptimisticLockingFailureException`
- Explicit save after product inventory decrement
- Generate IDs for outbox entries

**M026**: Update CatalogService
- Validate category exists: `categoryRepository.existsById(categoryId)`
- Set `product.setCategoryId(uuid)` instead of `product.setCategory(entity)`
- Explicit save after updates

**M027**: Update OrderProcessingService
- Generate UUIDs for Order and OrderItems before save
- If PaymentTransaction is separate aggregate, save it separately
- Explicit save after all mutations

**M028**: Update OrderQueryService and PaymentCompletedService
- Explicit saves after mutations
- No lazy loading assumptions

### Phase 5: Configuration (Remaining) (M031-M032)

**M031**: Update Application Configuration
- Remove `spring.jpa.*` properties from `application.yml` (both services)
- Add `spring.data.jdbc.*` if needed (usually defaults work)
- Keep datasource and Flyway settings unchanged

**M032**: Update Redis Configuration
- File: `customer-facing-service/src/main/java/com/ecommerce/customer/config/RedisConfig.java`
- Remove `Hibernate5JakartaModule` registration
- Add `JavaTimeModule` for Instant serialization

### Phase 6: Test Fixes & Validation (M033-M038)

**M033**: Fix Service Unit Tests (Customer Service)
- Update mocks to set IDs on save
- Update entity construction to use `setId()` and `setCategoryId()`
- Verify explicit save calls

**M034**: Fix Service Unit Tests (Order Service)
- Same as M033

**M035**: Fix Contract Tests (Both Services)
- Should mostly pass (they test external APIs)
- Verify Testcontainers PostgreSQL works with JDBC

**M036-M037**: Run Full Test Suite
- Customer service: `mvn clean verify -pl customer-facing-service`
- Order service: `mvn clean verify -pl order-management-service`
- **Expected**: All 46 tests passing (including the 4 currently failing)

**M038**: Integration Testing with Full Stack
- Start infrastructure: `docker-compose up -d`
- Run both services
- Execute manual tests: `cd manual-tests && python test_runner.py`

---

## 📋 Key Files to Modify

### Customer-Facing Service (20 files)
**Entities** (7):
- `model/Category.java`
- `model/Product.java`
- `model/Cart.java`
- `model/CartItem.java`
- `model/OrderCreatedOutbox.java`
- `model/CheckoutIdempotency.java`
- `model/OrderNumberSequence.java`

**Repositories** (7):
- `repository/CategoryRepository.java`
- `repository/ProductRepository.java`
- `repository/CartRepository.java`
- `repository/CartItemRepository.java` (may be removed)
- `repository/OrderCreatedOutboxRepository.java`
- `repository/CheckoutIdempotencyRepository.java`
- `repository/OrderNumberSequenceRepository.java`

**Services** (3):
- `service/CartService.java`
- `service/CheckoutService.java`
- `service/CatalogService.java`

**Configuration** (3):
- `config/JdbcConfig.java` (create new)
- `config/AuditingCallback.java` (create new)
- `config/RedisConfig.java` (update)
- `resources/application.yml` (update)

### Order-Management Service (12 files)
**Entities** (4):
- `model/Order.java`
- `model/OrderItem.java`
- `model/PaymentTransaction.java`
- `model/ProcessedEvent.java`

**Repositories** (4):
- `repository/OrderRepository.java`
- `repository/OrderItemRepository.java` (may be removed)
- `repository/PaymentTransactionRepository.java`
- `repository/ProcessedEventRepository.java`

**Services** (3):
- `service/OrderProcessingService.java`
- `service/OrderQueryService.java`
- `service/PaymentCompletedService.java`

**Configuration** (2):
- `config/JdbcConfig.java` (create new)
- `config/AuditingCallback.java` (create new)
- `resources/application.yml` (update)

---

## 🎓 Reference Materials

1. **Migration Guide**: `MIGRATION_JPA_JDBC.md` (comprehensive 1900-line guide)
2. **Dependency Audit**: `docs/jpa-dependencies-audit.md`
3. **Baseline State**: `MIGRATION_BASELINE.md`
4. **Code Templates**: See Appendix in `MIGRATION_JPA_JDBC.md`
   - A1: JDBC Entity Template
   - A2: Parent-Child Aggregate Template
   - A3: Service Layer Pattern
   - A4: JSONB Converter Registration

---

## ⚠️ Critical Decision Points

### 🛑 Checkpoint 1: PaymentTransaction Aggregate Boundary (M018)
**Question**: Should PaymentTransaction be:
- **Option A**: Separate aggregate (fetch/save independently)
- **Option B**: Embedded in Order aggregate (always loaded together)

**Recommendation**: Option A (separate aggregate) for flexibility

### 🛑 Checkpoint 2: CartItemRepository Necessity (M022)
**Question**: Should CartItemRepository exist?
- If CartItem only accessed via Cart: Delete repository
- If independent queries needed: Keep and update to JDBC

**Recommendation**: Delete (enforce aggregate boundary)

---

## 🚨 Known Issues

### Pre-Existing Issue: PostgreSQL ENUM Casting
**Status**: Will be fixed by migration  
**Affected Tests**: 4 tests in `OrderContractTest`  
**Error**: `ERROR: operator does not exist: order_status = character varying`  
**Fix Location**: M023 - Add explicit cast in `OrderRepository.findWithFilters()`

---

## 📦 Deliverables

After completing the migration:

1. **All tests passing** (46/46, including the 4 currently failing)
2. **No JPA/Hibernate dependencies** in `mvn dependency:tree`
3. **Updated documentation** (if architectural changes made)
4. **Commit history** following established style (emoji + scope + description)

---

## 🔧 Development Commands

```bash
# Compile and check for errors (without running tests)
mvn clean compile -DskipTests

# Run tests for specific service
mvn test -pl customer-facing-service -DskipTests=false

# Run full build
mvn clean verify

# Check dependencies
mvn dependency:tree | grep -i hibernate  # Should return nothing

# Start infrastructure
docker-compose up -d

# View logs
docker-compose logs -f postgres
```

---

## 📝 Commit Message Style

Follow the established pattern:
```
<emoji> <type>(<scope>): <description>

<body with bullet points>
```

Examples:
- `✨ feat(entities): transform JPA entities to JDBC aggregates (M012-M021)`
- `🔧 chore(repos): update repositories from JPA to JDBC (M022-M023)`
- `♻️ refactor(services): add explicit saves and ID generation (M024-M028)`

---

## ⏱️ Estimated Effort

- **M009-M021** (Entities + Config): 6-8 hours
- **M022-M023** (Repositories): 3-4 hours
- **M024-M028** (Services): 4-5 hours
- **M031-M032** (Config): 1 hour
- **M033-M038** (Tests): 4-6 hours

**Total**: 18-24 hours of focused work

---

## ✅ Success Criteria

- [ ] All 11 entities transformed to JDBC
- [ ] All 11 repositories updated (JpaRepository → CrudRepository)
- [ ] All service methods use explicit saves
- [ ] UUIDs generated manually before saves
- [ ] ENUM casting issue fixed in OrderRepository
- [ ] Redis config updated (no Hibernate module)
- [ ] Application.yml files cleaned (no JPA properties)
- [ ] All 46 tests passing
- [ ] No JPA/Hibernate dependencies in `mvn dependency:tree`
- [ ] Manual tests passing (checkout flow works end-to-end)

---

## 🎯 Next Immediate Actions

1. **Start with M009**: Create `JdbcConfig.java` for customer-facing-service
2. **Then M010**: Create `JdbcConfig.java` for order-management-service
3. **Then M011**: Create `AuditingCallback.java` and `Auditable.java` interface
4. **Then M012**: Transform `Category.java` (simplest entity, good starting point)
5. **Continue systematically** through M013-M021 (remaining entities)

---

## 📞 Questions or Issues?

- Review `MIGRATION_JPA_JDBC.md` for detailed guidance on each task
- Check `docs/jpa-dependencies-audit.md` for dependency-specific questions
- Refer to code templates in Appendix of migration guide
- The migration guide has 7 checkpoints (🛑) for human review if needed

---

**Good luck with the migration! The foundation is solid, and the path forward is clear.** 🚀
