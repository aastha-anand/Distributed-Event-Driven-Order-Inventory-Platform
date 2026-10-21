package com.aastha.inventorysystem.inventory.dto;

public record InventoryResponse(
        Long productId,
        int onHand,
        int reserved,
        int available

) {
}
