package com.aastha.orderservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryOutcomeEvent(
        UUID eventId,
        UUID orderId,
        String productId,
        Integer quantity,
        String type,
        String reason,
        Instant timestamp
        ) {
}
