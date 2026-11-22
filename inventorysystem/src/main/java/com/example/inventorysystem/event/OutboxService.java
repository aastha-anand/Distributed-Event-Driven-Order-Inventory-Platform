package com.example.inventorysystem.event;

import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OutboxService {
    public static final String ORDER_EVENTS_TOPIC = "inventory.order-events";

    private final OutboxEventRepository outboxRepository;

    public OutboxService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrderEvent(
            OrderEventType type,
            Order order,
            List<OrderItem> items) {

        StringBuilder json = new StringBuilder();
        json.append("{\"eventType\":\"").append(type).append('"')
                .append(",\"orderId\":").append(order.getId())
                .append(",\"status\":\"").append(order.getStatus()).append('"')
                .append(",\"paymentStatus\":\"")
                .append(order.getPaymentStatus()).append('"')
                .append(",\"totalAmount\":")
                .append(order.getTotalAmount().toPlainString())
                .append(",\"occurredAt\":\"")
                .append(OffsetDateTime.now(ZoneOffset.UTC)).append('"');

        if (items != null) {
            json.append(",\"items\":[");
            for (int i = 0; i < items.size(); i++) {
                OrderItem item = items.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"productId\":")
                        .append(item.getProduct().getId())
                        .append(",\"quantity\":")
                        .append(item.getQuantity())
                        .append('}');
            }
            json.append(']');
        }
        json.append('}');

        outboxRepository.save(new OutboxEvent(
                "ORDER",
                String.valueOf(order.getId()),
                type.name(),
                ORDER_EVENTS_TOPIC,
                json.toString()));
    }
}
