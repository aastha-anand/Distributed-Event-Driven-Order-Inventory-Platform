package com.aastha.inventorysystem.product.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank
                                    @Size(max = 100)
                                    String sku,

                             @NotBlank
                                    @Size(max = 255)
                                    String name,

                             @NotNull
                                    @DecimalMin("0.00")
                                    @Digits(integer = 10, fraction = 2)
                                   BigDecimal price,

                             @NotNull
                                    @Min(0)
                                    Integer initialStock) {
}
