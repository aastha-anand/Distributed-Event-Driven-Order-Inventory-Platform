package com.aastha.inventorysystem.order.repository;

import com.aastha.inventorysystem.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByPurchaseOrder_IdOrderByIdAsc(Long orderId);
}
