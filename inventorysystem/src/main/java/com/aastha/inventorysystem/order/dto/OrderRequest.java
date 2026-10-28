package com.aastha.inventorysystem.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequest(
        @NotEmpty(message = "An order must contain at least one item")
        @Size(max = 50, message = "An order cannot contain more than 50 items")
        List<@Valid OrderItemRequest> items
) {
}
