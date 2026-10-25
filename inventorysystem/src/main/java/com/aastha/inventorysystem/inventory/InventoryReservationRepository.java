package com.aastha.inventorysystem.inventory;

import com.aastha.inventorysystem.order.Order;
import com.aastha.inventorysystem.order.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.id = :id
        """)
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id
    );

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
