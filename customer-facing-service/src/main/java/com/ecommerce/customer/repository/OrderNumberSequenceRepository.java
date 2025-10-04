package com.ecommerce.customer.repository;

import com.ecommerce.customer.model.OrderNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for order number sequence operations.
 * Provides pessimistic locking for concurrent-safe sequence generation.
 */
@Repository
public interface OrderNumberSequenceRepository extends JpaRepository<OrderNumberSequence, String> {

    /**
     * Find order number sequence by date key with pessimistic write lock.
     * Ensures only one transaction can increment the sequence at a time.
     *
     * @param dateKey the date key in YYYYMMDD format
     * @return the order number sequence, if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ons FROM OrderNumberSequence ons WHERE ons.dateKey = :dateKey")
    Optional<OrderNumberSequence> findByDateKeyWithLock(@Param("dateKey") String dateKey);
}
