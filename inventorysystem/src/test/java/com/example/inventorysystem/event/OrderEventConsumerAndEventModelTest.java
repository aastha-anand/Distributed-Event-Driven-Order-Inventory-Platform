package com.example.inventorysystem.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OrderEventConsumerAndEventModelTest {

    @Test
    void consumerAcceptsRecords() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(OutboxService.ORDER_EVENTS_TOPIC, 0, 5L, "10", "{\"orderId\":10}");

        assertThatCode(() -> new OrderEventConsumer().onOrderEvent(record)).doesNotThrowAnyException();
    }

    @Test
    void outboxEventLifecycle() {
        OutboxEvent e = new OutboxEvent("ORDER", "10", "ORDER_CONFIRMED", "topic", "{}");
        ReflectionTestUtils.setField(e, "id", 3L);

        assertThat(e.getId()).isEqualTo(3L);
        assertThat(e.getAggregateType()).isEqualTo("ORDER");
        assertThat(e.getAggregateId()).isEqualTo("10");
        assertThat(e.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(e.getTopic()).isEqualTo("topic");
        assertThat(e.getPayload()).isEqualTo("{}");
        assertThat(e.getCreatedAt()).isNull();
        assertThat(e.getPublishedAt()).isNull();

        e.onCreate();
        assertThat(e.getCreatedAt()).isNotNull();

        e.markPublished();
        assertThat(e.getPublishedAt()).isNotNull();
    }

    @Test
    void allEventTypesExist() {
        assertThat(OrderEventType.values()).hasSize(6);
        assertThat(OrderEventType.valueOf("PAYMENT_RECONCILIATION_REQUIRED")).isNotNull();
    }
}
