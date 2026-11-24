package com.example.inventorysystem.order.service;

import com.example.inventorysystem.event.OrderEventType;
import com.example.inventorysystem.event.OutboxService;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.model.InventoryReservation;
import com.example.inventorysystem.inventory.model.ReservationStatus;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;
import com.example.inventorysystem.order.repository.OrderRepository;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaymentServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock OutboxService outboxService;
    @InjectMocks OrderPaymentService service;

    private InventoryReservation reservation(Order order, long productId, int qty) {
        return TestData.reservation(order, TestData.product(productId, "S" + productId, "5.00", true), qty);
    }

    private Order lock(Order o) {
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    // ---------- paymentSucceeded ----------

    @Test
    void successOnUnknownOrderIs404() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.paymentSucceeded(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void repeatedSuccessCallbackDoesNotTouchStock() {
        Order o = TestData.reservedOrder(1L);
        o.markConfirmed();
        lock(o);

        assertThat(service.paymentSucceeded(1L)).isEqualTo(PaymentOutcome.ALREADY_CONFIRMED);
        verify(inventoryRepository, never()).consumeReservedStock(anyLong(), anyInt());
    }

    @Test
    void successOnOrderNotAwaitingPaymentIs409() {
        Order o = TestData.reservedOrder(1L);
        o.markCancelled();
        lock(o);

        assertThatThrownBy(() -> service.paymentSucceeded(1L))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void latePaymentIsFlaggedForReconciliationAndNothingIsDeducted() {
        Order o = lock(TestData.reservedOrder(1L, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));

        assertThat(service.paymentSucceeded(1L)).isEqualTo(PaymentOutcome.RECONCILIATION_REQUIRED);

        verify(outboxService).recordOrderEvent(OrderEventType.PAYMENT_RECONCILIATION_REQUIRED, o, null);
        verify(inventoryRepository, never()).consumeReservedStock(anyLong(), anyInt());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void successWithoutActiveReservationsIsIllegalState() {
        Order o = lock(TestData.reservedOrder(1L));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.paymentSucceeded(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void successWithNonActiveReservationIsIllegalState() {
        Order o = lock(TestData.reservedOrder(1L));
        InventoryReservation r = reservation(o, 1, 2);
        r.markReleased();
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r));

        assertThatThrownBy(() -> service.paymentSucceeded(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void successWhenStockCannotBeConsumedIsIllegalState() {
        Order o = lock(TestData.reservedOrder(1L));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(reservation(o, 1, 2)));
        when(inventoryRepository.consumeReservedStock(1L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.paymentSucceeded(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("product 1");
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void successDeductsStockConfirmsOrderAndEmitsEvent() {
        Order o = lock(TestData.reservedOrder(1L));
        InventoryReservation r1 = reservation(o, 1, 2);
        InventoryReservation r2 = reservation(o, 2, 3);
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r1, r2));
        when(inventoryRepository.consumeReservedStock(anyLong(), anyInt())).thenReturn(1);

        assertThat(service.paymentSucceeded(1L)).isEqualTo(PaymentOutcome.CONFIRMED);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(r1.getStatus()).isEqualTo(ReservationStatus.CONSUMED);
        assertThat(r2.getStatus()).isEqualTo(ReservationStatus.CONSUMED);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(InventoryMovement::getMovementType).containsOnly("SALE");
        assertThat(captor.getAllValues()).extracting(InventoryMovement::getOnHandDelta).containsExactly(-2, -3);
        verify(outboxService).recordOrderEvent(OrderEventType.ORDER_CONFIRMED, o, null);
    }

    // ---------- paymentFailed ----------

    @Test
    void failureOnUnknownOrderIs404() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.paymentFailed(1L))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void repeatedFailureCallbackDoesNotReleaseStockTwice() {
        Order o = TestData.reservedOrder(1L);
        o.markPaymentFailed();
        lock(o);

        service.paymentFailed(1L);

        verify(inventoryRepository, never()).releaseReservedStock(anyLong(), anyInt());
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void failureOnConfirmedOrderIs409() {
        Order o = TestData.reservedOrder(1L);
        o.markConfirmed();
        lock(o);

        assertThatThrownBy(() -> service.paymentFailed(1L))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void failureReleasesReservationsAndEmitsEvent() {
        Order o = lock(TestData.reservedOrder(1L));
        InventoryReservation r = reservation(o, 1, 4);
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r));
        when(inventoryRepository.releaseReservedStock(1L, 4)).thenReturn(1);

        service.paymentFailed(1L);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(outboxService).recordOrderEvent(OrderEventType.ORDER_PAYMENT_FAILED, o, null);
    }

    // ---------- releaseReservations ----------

    @Test
    void releaseAsExpiredUsesDistinctReferenceAndStatus() {
        Order o = TestData.reservedOrder(1L);
        InventoryReservation r = reservation(o, 1, 2);
        when(inventoryRepository.releaseReservedStock(1L, 2)).thenReturn(1);

        service.releaseReservations(List.of(r), 1L, ReservationStatus.EXPIRED);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getReference()).isEqualTo("ORDER-1-EXPIRED");
        assertThat(captor.getValue().getMovementType()).isEqualTo("RELEASE");
    }

    @Test
    void releaseAsReleasedUsesPlainReference() {
        Order o = TestData.reservedOrder(1L);
        InventoryReservation r = reservation(o, 1, 2);
        when(inventoryRepository.releaseReservedStock(1L, 2)).thenReturn(1);

        service.releaseReservations(List.of(r), 1L, ReservationStatus.RELEASED);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getReference()).isEqualTo("ORDER-1");
    }

    @Test
    void releaseFailureIsIllegalState() {
        Order o = TestData.reservedOrder(1L);
        InventoryReservation r = reservation(o, 1, 2);
        when(inventoryRepository.releaseReservedStock(1L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.releaseReservations(List.of(r), 1L, ReservationStatus.RELEASED))
                .isInstanceOf(IllegalStateException.class);
        verify(movementRepository, never()).save(any());
    }
}
