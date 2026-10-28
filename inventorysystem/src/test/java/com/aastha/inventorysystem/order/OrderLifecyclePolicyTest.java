package com.aastha.inventorysystem.order;





import com.aastha.inventorysystem.inventory.model.ReservationStatus;
import com.aastha.inventorysystem.order.model.OrderStatus;
import com.aastha.inventorysystem.policy.OrderLifecyclePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderLifecyclePolicyTest {

    @Test
    void reservedOrderCanBeConfirmed() {
        assertTrue(
                OrderLifecyclePolicy.canTransitionOrder(
                        OrderStatus.RESERVED,
                        OrderStatus.CONFIRMED
                )
        );
    }

    @Test
    void confirmedOrderCannotBeCancelled() {
        assertFalse(
                OrderLifecyclePolicy.canTransitionOrder(
                        OrderStatus.CONFIRMED,
                        OrderStatus.CANCELLED
                )
        );
    }

    @Test
    void activeReservationCanBeConsumed() {
        assertTrue(
                OrderLifecyclePolicy.canTransitionReservation(
                        ReservationStatus.ACTIVE,
                        ReservationStatus.CONSUMED
                )
        );
    }

    @Test
    void consumedReservationCannotBeReleased() {
        assertFalse(
                OrderLifecyclePolicy.canTransitionReservation(
                        ReservationStatus.CONSUMED,
                        ReservationStatus.RELEASED
                )
        );
    }

    @Test
    void invalidTransitionThrowsException() {
        assertThrows(
                IllegalStateException.class,
                () -> OrderLifecyclePolicy.requireOrderTransition(
                        OrderStatus.CONFIRMED,
                        OrderStatus.CANCELLED
                )
        );
    }
}

