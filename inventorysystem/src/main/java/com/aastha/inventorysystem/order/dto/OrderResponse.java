package com.aastha.inventorysystem.order.dto;

import com.aastha.inventorysystem.order.OrderStatus;
import com.aastha.inventorysystem.order.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(Long orderId,
                            OrderStatus status,
                            PaymentStatus paymentStatus,
                            BigDecimal totalAmount,
                            OffsetDateTime expiresAt,
                            List<OrderItemResponse> items) {
}
