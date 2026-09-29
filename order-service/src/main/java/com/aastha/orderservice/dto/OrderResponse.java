package com.aastha.orderservice.dto;

import com.aastha.orderservice.entity.Order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse (
        UUID id,
        String productId,
        Integer quantity,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
){
    public static OrderResponse from(Order order){
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
