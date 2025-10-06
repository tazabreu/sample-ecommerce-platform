# Migration State: JPA → JDBC (2025-10-05T23:10:44Z)

## Completed Work
- [X] Spring Data JDBC dependencies applied to both services  
  **Files**: `customer-facing-service/pom.xml:33`, `order-management-service/pom.xml:34`
- [X] JDBC configuration, auditing callbacks, and Redis serialization updated  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/JdbcConfig.java:14`, `.../AuditingCallback.java:12`, `.../RedisConfig.java:35`, `order-management-service/src/main/java/com/ecommerce/order/config/JdbcConfig.java:31`
- [X] Aggregates rewritten for JDBC semantics with explicit setters/UUID handling  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/*`, `order-management-service/src/main/java/com/ecommerce/order/model/*`
- [X] Repositories migrated to SQL queries (ENUM casting fixed)  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/repository/*`, `order-management-service/src/main/java/com/ecommerce/order/repository/OrderRepository.java:113`
- [X] Services updated to explicit persistence flow with manual IDs  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CheckoutService.java:131`, `order-management-service/src/main/java/com/ecommerce/order/service/OrderProcessingService.java:145`
- [X] Tests adjusted for manual ID handling  
  **Files**: `customer-facing-service/src/test/java/com/ecommerce/customer/service/CartServiceTest.java:66`, `CheckoutServiceTest.java:57`

## Open Issues (Prioritize First)
- [ ] Move datasource configuration from `server.datasource` into `spring.datasource` in base profiles to restore default-profile connectivity  
  **Files**: `customer-facing-service/src/main/resources/application.yml:18`, `order-management-service/src/main/resources/application.yml:18`
- [ ] Remove Hibernate logging categories left over from JPA era  
  **Files**: `customer-facing-service/src/main/resources/application.yml:134`, `order-management-service/src/main/resources/application.yml:134`
- [ ] Align cart items mapping with schema (either add `item_index` column or drop `keyColumn`)  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Cart.java:52`, `customer-facing-service/src/main/resources/db/migration/V4__create_cart_items_table.sql`
- [ ] Update migration docs/checklists and confirm contract tests after JDBC switch  
  **Files**: `MIGRATION_HANDOFF.md:32`, `docs/test-migration-checklist.md` (missing), `order-management-service/src/main/java/com/ecommerce/order/repository/OrderRepository.java:113`

## Consistency Improvements
- [ ] Seed cart `items` collection eagerly and drop repeated `initializeItems()` calls  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Cart.java:52`, `customer-facing-service/src/main/java/com/ecommerce/customer/service/CartService.java:321`, `customer-facing-service/src/main/java/com/ecommerce/customer/config/AuditingCallback.java:16`
- [ ] Rename `CartItem#calculateSubtotal()` to a mutation-specific helper and keep subtotal recalculation internal  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/CartItem.java:188`
- [ ] Reuse loaded `Product` during cart quantity updates to avoid duplicate lookups  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CartService.java:219`
- [ ] Batch-lock products during checkout instead of one query per item  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CheckoutService.java:183`
- [ ] Build order events from the locked product snapshot rather than re-querying inside stream  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/service/CheckoutService.java:237`
- [ ] Replace `@Async` payment follow-up with an after-commit listener to avoid duplicate fetches  
  **Files**: `order-management-service/src/main/java/com/ecommerce/order/service/OrderProcessingService.java:238`
- [ ] Standardize order item collection as `List` end-to-end to prevent copies between `List` ↔ `Set`  
  **Files**: `order-management-service/src/main/java/com/ecommerce/order/model/Order.java:74`, `order-management-service/src/main/java/com/ecommerce/order/service/OrderProcessingService.java:206`
- [ ] Evaluate necessity of `StatefulPersistable` now that IDs are assigned before save  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/model/Category.java:29`, `.../OrderNumberSequence.java:13`

## Inconsistencies to Reconcile
- [ ] Auditing callback behavior diverges: customer service mutates child timestamps while order service only touches root aggregates. Decide on shared strategy or document rationale  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/AuditingCallback.java:16`, `order-management-service/src/main/java/com/ecommerce/order/config/AuditingCallback.java:9`
- [ ] Persistable state handling exists only in customer service; order service relies on defaults. Either share the callback via shared lib or drop from customer side for symmetry  
  **Files**: `customer-facing-service/src/main/java/com/ecommerce/customer/config/PersistableStateCallback.java`, absence in order service
- [ ] README and project layout still describe modules as “JPA entities/repositories,” conflicting with current JDBC implementation  
  **Files**: `README.md:74`

## Documentation Drift
- [ ] Specs still direct engineers to build JPA entities/repositories even though JDBC migration is complete  
  **Files**: `specs/001-e-commerce-platform/tasks.md:129-163`, `specs/001-e-commerce-platform/plan.md:38`
- [ ] Research notes cite Spring Data JPA guidance; update to JDBC references where appropriate  
  **Files**: `specs/001-e-commerce-platform/research.md:327`
- [ ] README project structure section labels packages as “JPA repositories/entities”; update wording to “Spring Data JDBC”  
  **Files**: `README.md:74`
- [ ] Migration tasks (T054-T056) remain unchecked in specs despite migration being largely implemented; align status and acceptance criteria to current reality  
  **Files**: `specs/001-e-commerce-platform/tasks.md:313-356`

