package com.aastha.inventorysystem.product.dto;

import java.math.BigDecimal;

public record ProductResponse(Long id,
                              String sku,
                              String name,
                              BigDecimal price,
                              int onHand,
                              int reserved,
                              int available) {
}
