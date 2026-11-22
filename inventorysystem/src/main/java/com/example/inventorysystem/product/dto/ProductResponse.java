package com.example.inventorysystem.product.dto;

import java.math.BigDecimal;

public record ProductResponse(Long id,
                              String sku,
                              String name,
                              BigDecimal price,
                              boolean active,
                              int onHand,
                              int reserved,
                              int available) {
}
