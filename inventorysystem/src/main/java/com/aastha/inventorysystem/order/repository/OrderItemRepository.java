package com.aastha.inventorysystem.order.repository;

import com.aastha.inventorysystem.order.service.OrderCancellationService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderCancellationService.OrderItem, Long> {
    List<OrderCancellationService.OrderItem> findByPurchaseOrder_IdOrderByIdAsc(Long OrderId);
}
