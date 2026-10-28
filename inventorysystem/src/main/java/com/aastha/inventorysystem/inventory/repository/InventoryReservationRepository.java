package com.aastha.inventorysystem.inventory.repository;

import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import com.aastha.inventorysystem.inventory.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByOrderItem_id(Long OrderItemId);

    List<InventoryReservation>
    findByOrderItem_PurchaseOrder_IdOrderByIdAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
  select r FROM InventoryReservation r
  where r.orderItem.purchaseOrder.id=:orderId
  order by r.id ASC
""")
    List<InventoryReservation> findByOrderIdForUpdate(
            @Param("orderId") Long orderId);

    List<InventoryReservation> findByStatusAndExpiresAtLessThanEqual(
            ReservationStatus status,
            OffsetDateTime now
    );
}
