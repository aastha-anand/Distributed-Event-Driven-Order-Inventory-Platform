package com.aastha.inventorysystem.inventory.dto;

import com.aastha.inventorysystem.inventory.model.InventoryMovement;

import java.time.OffsetDateTime;

public record MovementResponse(
        Long id,
        String movementType,
        int onHandDelta,
        int reservedDelta,
        String reference,
        OffsetDateTime createdAt) {

    public static MovementResponse from(InventoryMovement m) {
        return new MovementResponse(
                m.getId(),
                m.getMovementType(),
                m.getOnHandDelta(),
                m.getReservedDelta(),
                m.getReference(),
                m.getCreatedAt());
    }
}
