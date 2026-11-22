package com.example.inventorysystem.policy;

import com.example.inventorysystem.inventory.model.ReservationStatus;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The state machine is what stops stock being deducted or released twice,
 * so its rules are pinned down here without needing a database.
 */
class OrderLifecyclePolicyTest {

    private static Order newOrder() {
        return new Order("key-1", "hash", new BigDecimal("10.00"),
                OffsetDateTime.now().plusMinutes(15));
    }

    @Test
    void createdOrderCanOnlyBecomeReserved() {
        assertTrue(OrderLifecyclePolicy.canTransitionOrder(
                OrderStatus.CREATED, OrderStatus.RESERVED));
        assertFalse(OrderLifecyclePolicy.canTransitionOrder(
                OrderStatus.CREATED, OrderStatus.CONFIRMED));
        assertFalse(OrderLifecyclePolicy.canTransitionOrder(
                OrderStatus.CREATED, OrderStatus.CANCELLED));
    }

    @Test
    void reservedOrderCanReachEveryOutcome() {
        for (OrderStatus next : new OrderStatus[]{
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED,
                OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED}) {
            assertTrue(OrderLifecyclePolicy.canTransitionOrder(
                    OrderStatus.RESERVED, next), "RESERVED -> " + next);
        }
    }

    @Test
    void terminalOrderStatesCannotMove() {
        for (OrderStatus terminal : new OrderStatus[]{
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED,
                OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED}) {
            for (OrderStatus next : OrderStatus.values()) {
                assertFalse(OrderLifecyclePolicy.canTransitionOrder(
                        terminal, next), terminal + " -> " + next);
            }
        }
    }

    @Test
    void paymentOnlyMovesForwardOnce() {
        assertTrue(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.NOT_STARTED, PaymentStatus.PENDING));
        assertTrue(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.PENDING, PaymentStatus.SUCCEEDED));
        assertTrue(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.PENDING, PaymentStatus.FAILED));
        assertFalse(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED));
        assertFalse(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.FAILED, PaymentStatus.SUCCEEDED));
        assertFalse(OrderLifecyclePolicy.canTransitionPayment(
                PaymentStatus.NOT_STARTED, PaymentStatus.SUCCEEDED));
    }

    @Test
    void reservationCanBeResolvedExactlyOnce() {
        for (ReservationStatus next : new ReservationStatus[]{
                ReservationStatus.CONSUMED, ReservationStatus.RELEASED,
                ReservationStatus.EXPIRED}) {
            assertTrue(OrderLifecyclePolicy.canTransitionReservation(
                    ReservationStatus.ACTIVE, next));
            // Once resolved, it can never be consumed or released again.
            assertFalse(OrderLifecyclePolicy.canTransitionReservation(
                    next, ReservationStatus.CONSUMED));
            assertFalse(OrderLifecyclePolicy.canTransitionReservation(
                    next, ReservationStatus.RELEASED));
        }
    }

    @Test
    void nullStatesAreRejected() {
        assertFalse(OrderLifecyclePolicy.canTransitionOrder(null, OrderStatus.RESERVED));
        assertFalse(OrderLifecyclePolicy.canTransitionOrder(OrderStatus.CREATED, null));
        assertThrows(IllegalStateException.class,
                () -> OrderLifecyclePolicy.requireOrderTransition(null, OrderStatus.RESERVED));
    }

    @Test
    void orderHappyPathEndsConfirmedAndPaid() {
        Order order = newOrder();
        assertEquals(OrderStatus.CREATED, order.getStatus());

        order.markReserved();
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());

        order.markConfirmed();
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, order.getPaymentStatus());
    }

    @Test
    void confirmedOrderCannotBeCancelledOrFailedAfterwards() {
        Order order = newOrder();
        order.markReserved();
        order.markConfirmed();

        assertThrows(IllegalStateException.class, order::markCancelled);
        assertThrows(IllegalStateException.class, order::markPaymentFailed);
        assertThrows(IllegalStateException.class, order::markExpired);
    }

    @Test
    void cancelledOrderCannotBePaid() {
        Order order = newOrder();
        order.markReserved();
        order.markCancelled();

        assertThrows(IllegalStateException.class, order::markConfirmed);
    }

    @Test
    void orderCannotBeConfirmedWithoutBeingReservedFirst() {
        assertThrows(IllegalStateException.class, () -> newOrder().markConfirmed());
    }
}
