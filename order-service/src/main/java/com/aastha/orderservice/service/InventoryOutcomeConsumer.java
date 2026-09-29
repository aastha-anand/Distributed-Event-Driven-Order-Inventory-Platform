package com.aastha.orderservice.service;

import com.aastha.orderservice.entity.OrderStatus;
import com.aastha.orderservice.event.InventoryOutcomeEvent;
import com.aastha.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryOutcomeConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "order-service")
    @Transactional
    public void onInventoryOutcome(InventoryOutcomeEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if ("InventoryReserved".equals(event.type())) {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setFailureReason(null);
            } else if ("InventoryFailed".equals(event.type())) {
                order.setStatus(OrderStatus.FAILED);
                order.setFailureReason(event.reason());
            } else {
                log.warn("Unknown inventory outcome type '{}' for event {}", event.type(), event.eventId());
                return;
            }
            orderRepository.save(order);
            log.info("Order {} updated to {}", order.getId(), order.getStatus());
        }, () -> log.warn("Received outcome for unknown order {}", event.orderId()));
    }
}