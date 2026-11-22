package com.aastha.inventorysystem.order.service;

public enum PaymentOutcome {
    /** Stock was deducted and the order is now CONFIRMED. */
    CONFIRMED,
    /** A repeated callback; nothing changed. */
    ALREADY_CONFIRMED,
    /**
     * Payment arrived after the reservation expired. Nothing was deducted;
     * a PAYMENT_RECONCILIATION_REQUIRED event was recorded so a downstream
     * consumer (refund / manual review) can act on the captured money.
     */
    RECONCILIATION_REQUIRED
}
