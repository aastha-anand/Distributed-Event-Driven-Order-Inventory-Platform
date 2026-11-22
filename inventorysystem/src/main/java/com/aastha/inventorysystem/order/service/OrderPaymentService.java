package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.event.OrderEventType;
import com.aastha.inventorysystem.event.OutboxService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OrderPaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderPaymentService.class);

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxService outboxService;

    public OrderPaymentService(
            OrderRepository orderRepository,
            InventoryReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public PaymentOutcome paymentSucceeded(Long orderId) {
        Order order = lockOrder(orderId);

        // Repeated success callbacks must not deduct stock twice.
        if (order.getStatus() == OrderStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentOutcome.ALREADY_CONFIRMED;
        }

        requireReservedAndPending(order);

        if (!order.getExpiresAt().isAfter(
                OffsetDateTime.now(ZoneOffset.UTC))) {
            // Money may have been captured for an order we can no longer
            // fulfil. Returning normally (not throwing) commits the outbox
            // row, so the reconciliation signal is durable; the controller
            // still answers 409. The expiry job releases the stock as usual.
            log.warn("Payment for order {} arrived after its reservation "
                    + "expired; flagged for reconciliation", orderId);
            outboxService.recordOrderEvent(
                    OrderEventType.PAYMENT_RECONCILIATION_REQUIRED,
                    order, null);
            return PaymentOutcome.RECONCILIATION_REQUIRED;
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
        outboxService.recordOrderEvent(
                OrderEventType.ORDER_CONFIRMED, order, null);
        return PaymentOutcome.CONFIRMED;
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

        releaseReservations(
                reservations, orderId, ReservationStatus.RELEASED);

        order.markPaymentFailed();
        outboxService.recordOrderEvent(
                OrderEventType.ORDER_PAYMENT_FAILED, order, null);
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

    /**
     * Gives reserved stock back to the available pool. Must run inside the
     * caller's transaction, with the order and reservation rows already locked.
     *
     * @param finalStatus RELEASED (cancel / payment failure) or EXPIRED (timeout)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservations(
            List<InventoryReservation> reservations,
            Long orderId,
            ReservationStatus finalStatus) {

        String reference = finalStatus == ReservationStatus.EXPIRED
                ? "ORDER-" + orderId + "-EXPIRED"
                : "ORDER-" + orderId;

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

            if (finalStatus == ReservationStatus.EXPIRED) {
                reservation.markExpired();
            } else {
                reservation.markReleased();
            }

            // The movement-type CHECK constraint has no EXPIRE value,
            // so expiry is recorded as a RELEASE with a distinct reference.
            movementRepository.save(
                    new InventoryMovement(
                            productId,
                            "RELEASE",
                            0,
                            -quantity,
                            reference));
        }
    }
}
