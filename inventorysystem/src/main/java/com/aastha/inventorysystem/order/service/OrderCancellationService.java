
package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import com.aastha.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import com.aastha.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.aastha.inventorysystem.inventory.model.ReservationStatus;

import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderStatus;
import com.aastha.inventorysystem.order.model.PaymentStatus;
import com.aastha.inventorysystem.order.repository.OrderRepository;
import com.aastha.inventorysystem.product.model.Product;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    public OrderCancellationService(
            OrderRepository orderRepository,
            InventoryReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository) {

        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public void cancelOrder(Long orderId) {

        // 1. Lock the order to prevent concurrent cancellation/payment updates.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId
                ));

        // 2. Make repeated cancellation requests idempotent.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        // 3. Only orders awaiting payment can be cancelled here.
        if (order.getStatus() != OrderStatus.RESERVED
                || order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only reserved orders awaiting payment can be cancelled"
            );
        }

        // 4. Lock the order's reservations.
        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdForUpdate(orderId);

        if (reservations.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No reservations found for order: " + orderId
            );
        }

        // Validate all reservations before modifying any stock.
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Order contains a reservation that is not active"
                );
            }
        }

        // 5. Release reserved stock and record each movement.
        for (InventoryReservation reservation : reservations) {

            Long productId = reservation.getProduct().getId();
            int quantity = reservation.getQuantity();

            int updated = inventoryRepository.releaseReservedStock(
                    productId,
                    quantity
            );

            if (updated != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Could not release reserved stock for product: "
                                + productId
                );
            }

            // Mark the reservation as released.
            reservation.markReleased();

            // Record the stock movement.
            InventoryMovement movement = new InventoryMovement(
                    productId,
                    "RELEASE",
                    0,
                    -quantity,
                    "ORDER-" + orderId
            );

            movementRepository.save(movement);
        }

        // 6. Mark the order as cancelled.
        order.markCancelled();
    }

    @Entity
    @Table(
            name = "order_items",
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uq_order_product",
                            columnNames = {"order_id", "product_id"}
                    )
            },
            indexes = {
                    @Index(
                            name = "idx_order_items_order_id",
                            columnList = "order_id"
                    )
            }
    )
    public static class OrderItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        private Order purchaseOrder;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "product_id", nullable = false)
        private Product product;

        @Column(nullable = false)
        private Integer quantity;

        @Column(
                name = "unit_price",
                nullable = false,
                precision = 12,
                scale = 2
        )
        private BigDecimal unitPrice;

        protected OrderItem() {
            // Required by JPA.
        }

        public OrderItem(
                Order purchaseOrder,
                Product product,
                Integer quantity,
                BigDecimal unitPrice) {

            this.purchaseOrder = purchaseOrder;
            this.product = product;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public BigDecimal getLineTotal() {
            return unitPrice.multiply(
                    BigDecimal.valueOf(quantity)
            );
        }

        public Long getId() {
            return id;
        }

        public Order getPurchaseOrder() {
            return purchaseOrder;
        }

        public Product getProduct() {
            return product;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }
}
