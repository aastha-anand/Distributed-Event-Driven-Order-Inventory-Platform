package com.aastha.inventorysystem.inventory.model;

import com.aastha.inventorysystem.order.service.OrderCancellationService;
import com.aastha.inventorysystem.product.model.Product;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderCancellationService.OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryReservation() {
        // Required by JPA
    }

    public InventoryReservation(
            OrderCancellationService.OrderItem orderItem,
            Product product,
            Integer quantity,
            OffsetDateTime expiresAt) {
        this.orderItem = orderItem;
        this.product = product;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public void markReleased() {
        this.status = ReservationStatus.RELEASED;
    }

    public void markConsumed() {
        this.status = ReservationStatus.CONSUMED;
    }

    public void markExpired() {
        this.status = ReservationStatus.EXPIRED;
    }

    public Long getId() {
        return id;
    }

    public OrderCancellationService.OrderItem getOrderItem() {
        return orderItem;
    }

    public Product getProduct() {
        return product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}