package com.aastha.inventorysystem.event;

public enum OrderEventType {
    ORDER_RESERVED,
    ORDER_CONFIRMED,
    ORDER_PAYMENT_FAILED,
    ORDER_CANCELLED,
    ORDER_EXPIRED,
    PAYMENT_RECONCILIATION_REQUIRED
}
