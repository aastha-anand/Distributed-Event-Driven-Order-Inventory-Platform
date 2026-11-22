package com.example.inventorysystem.order.dto;

import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;

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
