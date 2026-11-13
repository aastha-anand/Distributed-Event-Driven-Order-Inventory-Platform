package com.aastha.inventorysystem.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial update: any field left out (null) is not changed.
 * SKU is immutable and stock is changed only through inventory endpoints.
 */
public record ProductUpdateRequest(
        @Size(max = 255)
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name,

        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        Boolean active) {
}
