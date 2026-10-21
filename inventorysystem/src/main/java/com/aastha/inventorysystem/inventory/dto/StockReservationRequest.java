package com.aastha.inventorysystem.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockReservationRequest(
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 150) String orderReference
) {
}
