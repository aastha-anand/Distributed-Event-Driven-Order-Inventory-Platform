package com.aastha.inventorysystem.order.model;

import com.aastha.inventorysystem.policy.OrderLifecyclePolicy;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_orders_status_expiry",
                        columnList = "status, expires_at"
                )
        }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            length = 150
    )
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_STARTED;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {
        // Required by JPA.
    }

    public Order(
            String idempotencyKey,
            String requestHash,
            BigDecimal totalAmount,
            OffsetDateTime expiresAt) {

        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.totalAmount = totalAmount;
        this.expiresAt = expiresAt;
        this.status = OrderStatus.CREATED;
        this.paymentStatus = PaymentStatus.NOT_STARTED;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markReserved() {
        OrderLifecyclePolicy.requireOrderTransition(
                this.status, OrderStatus.RESERVED
        );
        OrderLifecyclePolicy.requirePaymentTransition(
                this.paymentStatus, PaymentStatus.PENDING
        );

        this.status = OrderStatus.RESERVED;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    public void markConfirmed() {
        OrderLifecyclePolicy.requireOrderTransition(
                this.status, OrderStatus.CONFIRMED
        );
        OrderLifecyclePolicy.requirePaymentTransition(
                this.paymentStatus, PaymentStatus.SUCCEEDED
        );

        this.status = OrderStatus.CONFIRMED;
        this.paymentStatus = PaymentStatus.SUCCEEDED;
    }

    public void markPaymentFailed() {
        OrderLifecyclePolicy.requireOrderTransition(
                this.status, OrderStatus.PAYMENT_FAILED
        );
        OrderLifecyclePolicy.requirePaymentTransition(
                this.paymentStatus, PaymentStatus.FAILED
        );

        this.status = OrderStatus.PAYMENT_FAILED;
        this.paymentStatus = PaymentStatus.FAILED;
    }

    public void markCancelled() {
        OrderLifecyclePolicy.requireOrderTransition(
                this.status, OrderStatus.CANCELLED
        );

        this.status = OrderStatus.CANCELLED;
    }

    public void markExpired() {
        OrderLifecyclePolicy.requireOrderTransition(
                this.status, OrderStatus.EXPIRED
        );

        this.status = OrderStatus.EXPIRED;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

