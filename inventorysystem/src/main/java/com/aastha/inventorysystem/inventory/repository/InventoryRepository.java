package com.aastha.inventorysystem.inventory.repository;

import com.aastha.inventorysystem.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :id")
    Optional<Inventory> findByProductIdForUpdate(
            @Param("id") Long id);


    @Modifying
    @Query(value = """
    UPDATE inventory
    SET on_hand = on_hand - :quantity,
        reserved = reserved - :quantity,
        version = version + 1
    WHERE product_id = :id
      AND :quantity > 0
      AND reserved >= :quantity
      AND on_hand >= :quantity
    """, nativeQuery = true)
    int consumeReservedStock(
            @Param("id") Long productId,
            @Param("quantity") int quantity
    );



    @Modifying
    @Query(value = """
    UPDATE inventory
    SET reserved = reserved - :quantity,
        version = version + 1
    WHERE product_id = :id
      AND :quantity > 0
      AND reserved >= :quantity
    """, nativeQuery = true)
    int releaseReservedStock(
            @Param("id") Long productId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query(value = """
    UPDATE inventory
    SET reserved = reserved + :quantity,
        version = version + 1
    WHERE product_id = :id
      AND :quantity > 0
      AND on_hand - reserved >= :quantity
    """, nativeQuery = true)
    int reserveStock(
            @Param("id") Long productId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query(value = """
    UPDATE inventory
    SET on_hand = on_hand + :delta,
        version = version + 1
    WHERE product_id = :id
      AND on_hand + :delta >= reserved
    """, nativeQuery = true)
    int adjustStock(
            @Param("id") Long productId,
            @Param("delta") int delta
    );


}
