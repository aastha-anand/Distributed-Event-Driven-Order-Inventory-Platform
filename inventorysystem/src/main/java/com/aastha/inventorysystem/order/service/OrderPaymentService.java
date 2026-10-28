package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import com.aastha.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.aastha.inventorysystem.inventory.model.ReservationStatus;
import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderStatus;
import com.aastha.inventorysystem.order.model.PaymentStatus;
import com.aastha.inventorysystem.order.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    public OrderPaymentService(
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
    public void paymentSucceeded(Long orderId) {
        Order order = lockOrder(orderId);

        // Repeated success callbacks must not deduct stock twice.
        if (order.getStatus() == OrderStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        requireReservedAndPending(order);

        if (!order.getExpiresAt().isAfter(
                OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation has expired; payment needs reconciliation");
        }

        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdForUpdate(orderId);

        requireActiveReservations(reservations);

        for (InventoryReservation reservation : reservations) {
            Long productId = reservation.getProduct().getId();
            int quantity = reservation.getQuantity();

            int updated = inventoryRepository.consumeReservedStock(
                    productId, quantity);

            if (updated != 1) {
                throw new IllegalStateException(
                        "Could not consume reserved stock for product "
                                + productId);
            }

            reservation.markConsumed();

            movementRepository.save(
                    new InventoryMovement(
                            productId,
                            "SALE",
                            -quantity,
                            -quantity,
                            "ORDER-" + orderId));
        }

        order.markConfirmed();
    }

    @Transactional
    public void paymentFailed(Long orderId) {
        Order order = lockOrder(orderId);

        // Repeated failure callbacks must not release stock twice.
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED
                && order.getPaymentStatus() == PaymentStatus.FAILED) {
            return;
        }

        requireReservedAndPending(order);

        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdForUpdate(orderId);

        requireActiveReservations(reservations);

        releaseReservations(reservations, orderId, "RELEASE");

        order.markPaymentFailed();
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void requireReservedAndPending(Order order) {
        if (order.getStatus() != OrderStatus.RESERVED
                || order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not awaiting payment");
        }
    }

    private void requireActiveReservations(
            List<InventoryReservation> reservations) {
        if (reservations.isEmpty()
                || reservations.stream().anyMatch(
                r -> r.getStatus() != ReservationStatus.ACTIVE)) {
            throw new IllegalStateException(
                    "Order does not have a complete set of active reservations");
        }
    }

    void releaseReservations(
            List<InventoryReservation> reservations,
            Long orderId,
            String movementType) {

        for (InventoryReservation reservation : reservations) {
            Long productId = reservation.getProduct().getId();
            int quantity = reservation.getQuantity();

            int updated = inventoryRepository.releaseReservedStock(
                    productId, quantity);

            if (updated != 1) {
                throw new IllegalStateException(
                        "Could not release reserved stock for product "
                                + productId);
            }

            reservation.markReleased();

            movementRepository.save(
                    new InventoryMovement(
                            productId,
                            movementType,
                            0,
                            -quantity,
                            "ORDER-" + orderId));
        }
    }
}
