package com.example.inventorysystem.event;

import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderItem;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock OutboxEventRepository repository;
    @InjectMocks OutboxService service;

    private OutboxEvent captureSaved() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsEventWithItemsAsJson() {
        Order order = TestData.reservedOrder(55L);
        OrderItem i1 = TestData.orderItem(order, TestData.product(1L, "A", "10.00", true), 2);
        OrderItem i2 = TestData.orderItem(order, TestData.product(2L, "B", "5.00", true), 3);

        service.recordOrderEvent(OrderEventType.ORDER_RESERVED, order, List.of(i1, i2));

        OutboxEvent saved = captureSaved();
        assertThat(saved.getAggregateType()).isEqualTo("ORDER");
        assertThat(saved.getAggregateId()).isEqualTo("55");
        assertThat(saved.getEventType()).isEqualTo("ORDER_RESERVED");
        assertThat(saved.getTopic()).isEqualTo(OutboxService.ORDER_EVENTS_TOPIC);
        assertThat(saved.getPayload())
                .contains("\"eventType\":\"ORDER_RESERVED\"")
                .contains("\"orderId\":55")
                .contains("\"status\":\"RESERVED\"")
                .contains("\"paymentStatus\":\"PENDING\"")
                .contains("\"totalAmount\":20.00")
                .contains("\"items\":[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":3}]");
    }

    @Test
    void omitsItemsArrayWhenNoneGiven() {
        Order order = TestData.reservedOrder(56L);

        service.recordOrderEvent(OrderEventType.ORDER_CANCELLED, order, null);

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(saved.getPayload()).doesNotContain("items");
        assertThat(saved.getPayload()).startsWith("{").endsWith("}");
    }
}
