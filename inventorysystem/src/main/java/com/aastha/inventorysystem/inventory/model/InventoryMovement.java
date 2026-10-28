package com.aastha.inventorysystem.inventory.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "movement_type", nullable = false, length = 30)
    private String movementType;

    @Column(name = "on_hand_delta", nullable = false)
    private int onHandDelta;

    @Column(name = "reserved_delta", nullable = false)
    private int reservedDelta;

    @Column(length = 150)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(
            Long productId,
            String movementType,
            int onHandDelta,
            int reservedDelta,
            String reference) {
        this.productId = productId;
        this.movementType = movementType;
        this.onHandDelta = onHandDelta;
        this.reservedDelta = reservedDelta;
        this.reference = reference;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getMovementType() {
        return movementType;
    }

    public int getOnHandDelta() {
        return onHandDelta;
    }

    public int getReservedDelta() {
        return reservedDelta;
    }

    public String getReference() {
        return reference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

}
