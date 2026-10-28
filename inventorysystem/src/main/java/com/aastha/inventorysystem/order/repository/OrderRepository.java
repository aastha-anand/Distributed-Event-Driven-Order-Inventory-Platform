package com.aastha.inventorysystem.order.repository;

import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Idempotency: check whether this request key already exists
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // Lock an order while processing payment, cancellation or expiry
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.id = :id
        """)
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // Fetch a batch of expired orders for background processing
    @Query("""
        SELECT o.id
        FROM Order o
        WHERE o.status = :status
          AND o.expiresAt <= :now
        ORDER BY o.expiresAt ASC
        """)
    List<Long> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );
}
