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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancellationServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock OutboxService outboxService;
    @InjectMocks OrderCancellationService service;

    private static int code(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    private InventoryReservation reservation(Order order, long productId, int qty) {
        return TestData.reservation(order, TestData.product(productId, "S" + productId, "5.00", true), qty);
    }

    @Test
    void unknownOrderIs404() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelOrder(1L)).satisfies(t -> assertThat(code(t)).isEqualTo(404));
    }

    @Test
    void alreadyCancelledOrderIsANoOp() {
        Order o = TestData.reservedOrder(1L);
        o.markCancelled();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));

        service.cancelOrder(1L);

        verify(reservationRepository, never()).findByOrderIdForUpdate(anyLong());
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void confirmedOrderCannotBeCancelled() {
        Order o = TestData.reservedOrder(1L);
        o.markConfirmed();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.cancelOrder(1L)).satisfies(t -> assertThat(code(t)).isEqualTo(409));
    }

    @Test
    void orderWithoutReservationsIs409() {
        Order o = TestData.reservedOrder(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.cancelOrder(1L)).satisfies(t -> assertThat(code(t)).isEqualTo(409));
    }

    @Test
    void inactiveReservationBlocksCancellation() {
        Order o = TestData.reservedOrder(1L);
        InventoryReservation r = reservation(o, 1, 2);
        r.markConsumed();
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r));

        assertThatThrownBy(() -> service.cancelOrder(1L)).satisfies(t -> assertThat(code(t)).isEqualTo(409));
        verify(inventoryRepository, never()).releaseReservedStock(anyLong(), anyInt());
    }

    @Test
    void failedStockReleaseIs409() {
        Order o = TestData.reservedOrder(1L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(reservation(o, 1, 2)));
        when(inventoryRepository.releaseReservedStock(1L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.cancelOrder(1L)).satisfies(t -> assertThat(code(t)).isEqualTo(409));
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void cancelReleasesEveryReservationAndEmitsEvent() {
        Order o = TestData.reservedOrder(1L);
        InventoryReservation r1 = reservation(o, 1, 2);
        InventoryReservation r2 = reservation(o, 2, 3);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        when(reservationRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(r1, r2));
        when(inventoryRepository.releaseReservedStock(anyLong(), anyInt())).thenReturn(1);

        service.cancelOrder(1L);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(r1.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(r2.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(InventoryMovement::getReservedDelta).containsExactly(-2, -3);
        assertThat(captor.getAllValues()).extracting(InventoryMovement::getMovementType).containsOnly("RELEASE");
        verify(outboxService).recordOrderEvent(OrderEventType.ORDER_CANCELLED, o, null);
    }
}
