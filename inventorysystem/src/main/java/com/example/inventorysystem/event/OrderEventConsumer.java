package com.example.inventorysystem.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
    private static final Logger log=
            LoggerFactory.getLogger(OrderEventConsumer.class);

    @KafkaListener(
            topics = OutboxService.ORDER_EVENTS_TOPIC,
            groupId = "inventory-notifications")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        log.info("Order event received: key={} partition={} offset={} value={}",
                record.key(), record.partition(), record.offset(), record.value());
    }
}
