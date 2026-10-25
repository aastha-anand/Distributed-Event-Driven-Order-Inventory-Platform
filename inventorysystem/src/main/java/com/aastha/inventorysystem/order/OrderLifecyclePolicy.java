package com.aastha.inventorysystem.order;

import com.aastha.inventorysystem.inventory.ReservationStatus;

public final class OrderLifecyclePolicy {
    private OrderLifecyclePolicy() {

    }
    public static boolean canTransitionOrder(
            OrderStatus current,
            OrderStatus next){
        if(current==null || next==null){
            return false;
        }
        return switch (current) {
            case CREATED ->
                    next == OrderStatus.RESERVED;
            case RESERVED ->
                    next == OrderStatus.CONFIRMED
                            || next == OrderStatus.CANCELLED
                            || next == OrderStatus.EXPIRED
                            || next == OrderStatus.PAYMENT_FAILED;

            case CONFIRMED, CANCELLED, EXPIRED, PAYMENT_FAILED -> false;
        };
    }

    public static boolean canTransitionPayment(
            PaymentStatus current,
            PaymentStatus next) {

        if (current == null || next == null) {
            return false;
        }

        return switch (current) {
            case NOT_STARTED ->
                    next == PaymentStatus.PENDING;

            case PENDING ->
                    next == PaymentStatus.SUCCEEDED
                            || next == PaymentStatus.FAILED;

            case SUCCEEDED, FAILED ->
                    false;
        };
    }

    public static boolean canTransitionReservation(
            ReservationStatus current,
            ReservationStatus next) {

        if (current == null || next == null) {
            return false;
        }

        return current == ReservationStatus.ACTIVE
                && (next == ReservationStatus.CONSUMED
                || next == ReservationStatus.RELEASED
                || next == ReservationStatus.EXPIRED);
    }

    public static void requireOrderTransition(
            OrderStatus current,
            OrderStatus next) {

        if (!canTransitionOrder(current, next)) {
            throw new IllegalStateException(
                    "Invalid order transition: "
                            + current + " -> " + next
            );
        }
    }

    public static void requirePaymentTransition(
            PaymentStatus current,
            PaymentStatus next) {

        if (!canTransitionPayment(current, next)) {
            throw new IllegalStateException(
                    "Invalid payment transition: "
                            + current + " -> " + next
            );
        }
    }

    public static void requireReservationTransition(
            ReservationStatus current,
            ReservationStatus next) {

        if (!canTransitionReservation(current, next)) {
            throw new IllegalStateException(
                    "Invalid reservation transition: "
                            + current + " -> " + next
            );
        }
    }
}
