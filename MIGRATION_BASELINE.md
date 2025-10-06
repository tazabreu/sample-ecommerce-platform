# JPA → JDBC Migration Baseline

**Date**: 2025-10-05  
**Branch**: `migration/jpa-to-jdbc`  
**Rollback Tag**: `pre-jdbc-migration`  
**Java Version**: 21 (Maven using JDK 25, compiling to target 21)  
**Maven Version**: 3.9.11  

---

## Environment Validation (M001)

✅ **Java Configuration**: Project configured for Java 21  
✅ **Maven**: Version 3.9.11 installed and functional  
✅ **Git Branch**: Created `migration/jpa-to-jdbc`  
✅ **Rollback Tag**: Created `pre-jdbc-migration` tag  

---

## Known Pre-Migration Issues

### Issue 1: PostgreSQL ENUM Type Casting in OrderRepository

**Status**: Known issue, will be fixed by JDBC migration  
**Affected Tests**: 4 tests in `OrderContractTest`  
- `listOrders_shouldReturnPagedResponse`
- `listOrders_withStatusFilter_shouldFilterResults`
- `listOrders_withEmailFilter_shouldFilterResults`
- `listOrders_withDateRangeFilter_shouldFilterResults`

**Error**:
```
ERROR: operator does not exist: order_status = character varying
Hint: No operator matches the given name and argument types. You might need to add explicit type casts.
```

**Root Cause**:  
The `OrderRepository.findWithFilters()` method uses JPQL that Hibernate translates to SQL without proper ENUM casting:

```java
@Query("SELECT o FROM Order o WHERE " +
       "(:customerEmail IS NULL OR o.customerEmail = :customerEmail) AND " +
       "(:status IS NULL OR o.status = :status) AND " +
       "(:startDate IS NULL OR o.createdAt >= :startDate) AND " +
       "(:endDate IS NULL OR o.createdAt <= :endDate)")
Page<Order> findWithFilters(@Param("customerEmail") String customerEmail,
                             @Param("status") OrderStatus status,
                             @Param("startDate") Instant startDate,
                             @Param("endDate") Instant endDate,
                             Pageable pageable);
```

**Resolution Plan**:  
This will be fixed in **M023** (Update Repositories - Order Service) when we convert JPQL to SQL with explicit ENUM casting:

```sql
SELECT * FROM orders WHERE 
  (:customerEmail IS NULL OR customer_email = :customerEmail) AND 
  (:status IS NULL OR status = CAST(:status AS order_status)) AND  -- Explicit cast
  (:startDate IS NULL OR created_at >= :startDate) AND 
  (:endDate IS NULL OR created_at <= :endDate)
```

**Impact**: This issue does NOT block migration. The JDBC migration will fix it as part of the normal repository transformation process.

---

## Test Baseline

### Customer-Facing Service
- **Total Tests**: 14
- **Passing**: 14 ✅
- **Failing**: 0
- **Status**: All tests passing

### Order-Management Service
- **Total Tests**: 32
- **Passing**: 28
- **Failing**: 4 (known ENUM casting issue)
- **Status**: Failing tests are expected and will be fixed by migration

### Overall Baseline
- **Total Tests**: 46
- **Passing**: 42 (91.3%)
- **Known Issues**: 4 (will be fixed by migration)

---

## Migration Strategy

Given the pre-existing ENUM casting issue:

1. **Proceed with migration** - The issue will be fixed naturally during repository transformation (M023)
2. **Skip baseline test validation** - We know the 4 failing tests are due to a JPA limitation that JDBC will resolve
3. **Validate after M023** - Re-run tests after repository updates to confirm fix

---

## Dependencies Audit (M002) - To Be Completed

Will document JPA/Hibernate dependencies in next step.

---

## Success Criteria

After migration completion:
- ✅ All 46 tests passing (including the 4 currently failing)
- ✅ No JPA/Hibernate dependencies remaining
- ✅ ENUM casting issue resolved
- ✅ External APIs unchanged (contract tests validate this)
