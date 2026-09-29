package com.aastha.orderservice.service;

import com.aastha.orderservice.entity.Order;
import com.aastha.orderservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.order-events}")
    private String orderEventsTopic;

    public void publishOrderCreated(Order order){
        OrderCreatedEvent event=new OrderCreatedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                Instant.now());

        kafkaTemplate.send(orderEventsTopic,order.getId().toString(),event)
                .whenComplete((result,ex)->{
                    if(ex!=null){
                        log.error("Failed to publish order created for order id: {}",order.getId(),ex);
                    }
                    else{
                        log.info("Published order created event for eventId={} orderId: {} ",event.eventId(),order.getId());
                    }
                });
    }

}
