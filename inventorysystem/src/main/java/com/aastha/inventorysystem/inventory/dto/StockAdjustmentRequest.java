package com.aastha.inventorysystem.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull Long productId,
        @NotNull Integer delta,
        @Size(max = 150) String reference
) {
}
