package com.example.inventorysystem.order.service;

import com.example.inventorysystem.event.OrderEventType;
import com.example.inventorysystem.event.OutboxService;
import com.example.inventorysystem.inventory.model.InventoryReservation;
import com.example.inventorysystem.inventory.model.ReservationStatus;
import com.example.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.repository.OrderRepository;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderExpiryServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock OrderPaymentService paymentService;
    @Mock OutboxService outboxService;
    @Mock PlatformTransactionManager txManager;

    OrderExpiryService service;
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new OrderExpiryService(
                orderRepository, reservationRepository, paymentService, outboxService, txManager);
    }

    private void expiredIds(Long... ids) {
        when(orderRepository.findExpiredOrderIds(eq(OrderStatus.RESERVED), eq(now), any(Pageable.class)))
                .thenReturn(List.of(ids));
    }

    @Test
    void expiresReservedOrdersPastTheirDeadline() {
        Order o = TestData.reservedOrder(1L, now.minusMinutes(5));
        InventoryReservation r = TestData.reservation(o, TestData.product(1L, "A", "1.00", true), 2);
        expiredIds(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r));

        assertThat(service.processExpiredOrders(now)).isEqualTo(1);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(paymentService).releaseReservations(List.of(r), 1L, ReservationStatus.EXPIRED);
        verify(outboxService).recordOrderEvent(OrderEventType.ORDER_EXPIRED, o, null);
    }

    @Test
    void skipsOrderThatDisappearedBeforeTheLockWasTaken() {
        expiredIds(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThat(service.processExpiredOrders(now)).isZero();
        verify(paymentService, never()).releaseReservations(any(), any(), any());
    }

    @Test
    void skipsOrderThatWasPaidMeanwhile() {
        Order o = TestData.reservedOrder(1L, now.minusMinutes(5));
        o.markConfirmed();
        expiredIds(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));

        assertThat(service.processExpiredOrders(now)).isZero();
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void skipsOrderWhoseDeadlineIsStillInTheFuture() {
        Order o = TestData.reservedOrder(1L, now.plusMinutes(5));
        expiredIds(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));

        assertThat(service.processExpiredOrders(now)).isZero();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void oneFailingOrderDoesNotStopTheRest() {
        Order bad = TestData.reservedOrder(1L, now.minusMinutes(5));
        Order good = TestData.reservedOrder(2L, now.minusMinutes(5));
        expiredIds(1L, 2L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bad));
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(good));
        when(reservationRepository.findByOrderIdForUpdate(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        doThrow(new IllegalStateException("cannot release"))
                .when(paymentService).releaseReservations(any(), eq(1L), any());

        assertThat(service.processExpiredOrders(now)).isEqualTo(1);

        assertThat(good.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(bad.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void scheduledEntryPointRunsTheSweep() {
        when(orderRepository.findExpiredOrderIds(eq(OrderStatus.RESERVED), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        assertThatCode(() -> service.processExpiredOrders()).doesNotThrowAnyException();

        verify(orderRepository).findExpiredOrderIds(eq(OrderStatus.RESERVED), any(OffsetDateTime.class), any(Pageable.class));
    }

}
