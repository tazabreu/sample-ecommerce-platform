# JPA Dependencies Audit (M002)

**Date**: 2025-10-05  
**Purpose**: Document all JPA/Hibernate dependencies before migration to Spring Data JDBC

---

## Direct JPA/Hibernate Dependencies

### Both Services (customer-facing-service & order-management-service)

1. **spring-boot-starter-data-jpa** (3.2.0)
   - **Action**: Remove, replace with `spring-boot-starter-data-jdbc`
   - **Scope**: compile
   - **Transitive Dependencies**:
     - `hibernate-core` (6.3.1.Final)
     - `jakarta.persistence-api` (3.1.0)
     - `hibernate-commons-annotations` (6.0.6.Final)
     - `spring-data-jpa` (3.2.0)
     - `spring-orm` (6.1.1)
     - `spring-aspects` (6.1.1)

2. **hibernate-validator** (8.0.1.Final)
   - **Action**: Keep (used for bean validation, not JPA-specific)
   - **Scope**: compile
   - **Note**: Part of `spring-boot-starter-validation`, not JPA-specific

### Customer-Facing Service Only

3. **jackson-datatype-hibernate5-jakarta** (2.15.3)
   - **Action**: Remove (used for Redis serialization of JPA lazy proxies)
   - **Scope**: compile
   - **Impact**: Update `RedisConfig` to remove Hibernate5Module registration
   - **Replacement**: Standard Jackson serialization (no lazy proxies in JDBC)

---

## Transitive JPA/Hibernate Dependencies

From `spring-boot-starter-data-jpa`:

| Dependency | Version | Scope | Action |
|------------|---------|-------|--------|
| `hibernate-core` | 6.3.1.Final | compile | Remove (via parent removal) |
| `jakarta.persistence-api` | 3.1.0 | compile | Remove (via parent removal) |
| `hibernate-commons-annotations` | 6.0.6.Final | runtime | Remove (via parent removal) |
| `spring-data-jpa` | 3.2.0 | compile | Replace with `spring-data-jdbc` |
| `spring-orm` | 6.1.1 | compile | Remove (not needed for JDBC) |
| `spring-aspects` | 6.1.1 | compile | Remove (JPA-specific AOP) |
| `jboss-logging` | 3.5.3.Final | compile | Remove (Hibernate dependency) |
| `byte-buddy` | 1.14.10 | runtime | Keep (used by other libs) |
| `classmate` | 1.6.0 | compile | Remove (Hibernate dependency) |
| `jandex` | 3.1.2 | runtime | Remove (Hibernate dependency) |
| `antlr4-runtime` | 4.10.1 | compile | Remove (HQL parser) |

---

## Dependencies to Keep (Not JPA-Specific)

| Dependency | Reason |
|------------|--------|
| `spring-boot-starter-jdbc` | Already included in JPA starter, will be explicit |
| `HikariCP` | Connection pool (used by JDBC too) |
| `spring-jdbc` | Core JDBC support |
| `hibernate-validator` | Bean Validation (JSR-380), not JPA-specific |
| `postgresql` | Database driver |
| `flyway-core` | Database migrations |

---

## Code Locations Using Hibernate-Specific Features

### Customer-Facing Service

1. **Entities** (7 files):
   - `Category.java` - `@Entity`, `@OneToMany`
   - `Product.java` - `@Entity`, `@ManyToOne`, `@Version`
   - `Cart.java` - `@Entity`, `@OneToMany`
   - `CartItem.java` - `@Entity`, `@ManyToOne`
   - `OrderCreatedOutbox.java` - `@Entity`, `@JdbcTypeCode` (Hibernate)
   - `CheckoutIdempotency.java` - `@Entity`
   - `OrderNumberSequence.java` - `@Entity`

2. **Repositories** (7 files):
   - All extend `JpaRepository`
   - `ProductRepository` - uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`
   - `CartRepository` - uses `@EntityGraph`
   - Several use `@Query` with JPQL

3. **Configuration**:
   - `application.yml` - `spring.jpa.*` properties
   - `RedisConfig.java` - `Hibernate5JakartaModule` registration

4. **Tests**:
   - None use `@DataJpaTest` (already using `@SpringBootTest`)
   - No direct `EntityManager` usage found

### Order-Management Service

1. **Entities** (4 files):
   - `Order.java` - `@Entity`, `@OneToMany`, `@OneToOne`, `@JdbcTypeCode`
   - `OrderItem.java` - `@Entity`, `@ManyToOne`
   - `PaymentTransaction.java` - `@Entity`, `@OneToOne`
   - `ProcessedEvent.java` - `@Entity`

2. **Repositories** (4 files):
   - All extend `JpaRepository`
   - `OrderRepository` - uses `@Query` with JPQL (including ENUM issue)

3. **Configuration**:
   - `application.yml` - `spring.jpa.*` properties

4. **Tests**:
   - None use `@DataJpaTest`
   - No direct `EntityManager` usage found

---

## Hibernate-Specific Annotations Used

| Annotation | Usage Count | Replacement |
|------------|-------------|-------------|
| `@Entity` | 11 | Remove (JDBC uses `@Table`) |
| `@Table` | 11 | Keep (Spring Data JDBC uses same) |
| `@Id` | 11 | Change import to `org.springframework.data.annotation.Id` |
| `@GeneratedValue` | 11 | Remove (manual UUID generation) |
| `@Column` | ~50 | Remove (JDBC uses naming strategy) |
| `@OneToMany` | 4 | Replace with `@MappedCollection` |
| `@ManyToOne` | 4 | Replace with UUID reference |
| `@OneToOne` | 2 | Decision needed (separate aggregate or embedded) |
| `@JoinColumn` | 6 | Remove (no FK navigation in JDBC) |
| `@CreationTimestamp` | 11 | Replace with `BeforeConvertCallback` |
| `@UpdateTimestamp` | 9 | Replace with `BeforeConvertCallback` |
| `@Version` | 1 | Keep (JDBC supports optimistic locking) |
| `@Enumerated` | ~8 | Keep (JDBC supports enums) |
| `@JdbcTypeCode` | 2 | Replace with custom converters |
| `@EntityGraph` | 1 | Remove (JDBC always eager loads aggregates) |
| `@Lock` | 1 | Replace with SQL `FOR UPDATE` or keep `@Lock` |

---

## Redis Serialization Impact

### Current Setup (JPA):
```java
@Bean
public Jackson2JsonRedisSerializer<Cart> cartSerializer() {
    Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(Cart.class);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Hibernate5JakartaModule()); // Handles lazy proxies
    serializer.setObjectMapper(mapper);
    return serializer;
}
```

### After JDBC Migration:
```java
@Bean
public Jackson2JsonRedisSerializer<Cart> cartSerializer() {
    Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(Cart.class);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule()); // For Instant serialization
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    serializer.setObjectMapper(mapper);
    return serializer;
}
```

**Impact**: No lazy loading in JDBC, so no need for Hibernate module. Standard Jackson serialization works.

---

## Migration Impact Summary

### High Impact (Requires Code Changes)
- ✅ 11 entity classes (annotation changes)
- ✅ 11 repository interfaces (extend different base, JPQL → SQL)
- ✅ Service layer (explicit saves, ID generation)
- ✅ 1 Redis configuration (remove Hibernate module)
- ✅ 2 application.yml files (remove JPA properties)

### Medium Impact (Configuration Only)
- ✅ 2 pom.xml files (dependency swap)
- ✅ Test configuration (if any `@DataJpaTest` found)

### Low Impact (No Changes Needed)
- ✅ Flyway migrations (unchanged)
- ✅ Database schema (unchanged)
- ✅ External APIs/contracts (unchanged)
- ✅ Business logic (unchanged)

---

## Verification Commands

After migration, verify no JPA dependencies remain:

```bash
# Check for JPA/Hibernate in dependency tree
mvn dependency:tree | grep -i hibernate
mvn dependency:tree | grep -i "jakarta.persistence"

# Should return no results

# Check for JPA imports in code
grep -r "import jakarta.persistence" src/
grep -r "import org.hibernate" src/

# Should return no results (except hibernate-validator which is OK)
```

---

## Next Steps

Proceed to **M003**: Identify JPA-Specific Test Patterns

This audit confirms:
- ✅ All JPA dependencies identified
- ✅ Code locations documented
- ✅ Replacement strategy clear
- ✅ No hidden Hibernate features (HQL, Criteria API) found
- ✅ Migration is straightforward (no complex JPA features used)
