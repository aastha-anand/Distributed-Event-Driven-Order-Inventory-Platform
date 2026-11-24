package com.example.inventorysystem.order.service;

import com.example.inventorysystem.event.OrderEventType;
import com.example.inventorysystem.event.OutboxService;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.model.InventoryReservation;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.example.inventorysystem.order.dto.OrderItemRequest;
import com.example.inventorysystem.order.dto.OrderRequest;
import com.example.inventorysystem.order.dto.OrderResponse;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderItem;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;
import com.example.inventorysystem.order.repository.OrderItemRepository;
import com.example.inventorysystem.order.repository.OrderRepository;
import com.example.inventorysystem.product.model.Product;
import com.example.inventorysystem.product.repository.ProductRepository;
import com.example.inventorysystem.support.TestData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock OutboxService outboxService;
    @Mock PlatformTransactionManager txManager;

    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    OrderService service;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new OrderService(orderRepository, orderItemRepository, productRepository,
                inventoryRepository, reservationRepository, movementRepository,
                outboxService, txManager, meters);
    }

    private static OrderRequest req(OrderItemRequest... items) {
        return new OrderRequest(List.of(items));
    }

    private static OrderItemRequest item(Long productId, Integer qty) {
        return new OrderItemRequest(productId, qty);
    }

    private static int code(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private double outcome(String tag) {
        return meters.get("inventory.orders").tag("outcome", tag).counter().count();
    }

    private void stubHappyPersistence() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 900L);
            return o;
        });
        when(inventoryRepository.reserveStock(anyLong(), anyInt())).thenReturn(1);
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- request validation ----------

    @Test
    void rejectsMissingOrBlankOrTooLongIdempotencyKey() {
        OrderRequest ok = req(item(1L, 1));
        assertThatThrownBy(() -> service.createOrder(null, ok)).satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("  ", ok)).satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k".repeat(151), ok))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
    }

    @Test
    void rejectsNullEmptyOrOversizedItemLists() {
        assertThatThrownBy(() -> service.createOrder("k", null)).satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", new OrderRequest(null)))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", new OrderRequest(List.of())))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        List<OrderItemRequest> tooMany = new ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            tooMany.add(item(i, 1));
        }
        assertThatThrownBy(() -> service.createOrder("k", new OrderRequest(tooMany)))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
    }

    @Test
    void rejectsInvalidItems() {
        List<OrderItemRequest> withNullItem = new ArrayList<>();
        withNullItem.add(null);
        assertThatThrownBy(() -> service.createOrder("k", new OrderRequest(withNullItem)))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", req(item(null, 1))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", req(item(0L, 1))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, null))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 0))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
    }

    @Test
    void rejectsDuplicateProducts() {
        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 1), item(1L, 2))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
    }

    // ---------- idempotency ----------

    @Test
    void sameKeyAndSameBodyReplaysExistingOrder() throws Exception {
        Order existing = TestData.reservedOrder(7L);
        ReflectionTestUtils.setField(existing, "requestHash", sha256("1:2|3:1"));
        Product p = TestData.product(1L, "A", "10.00", true);
        when(orderRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));
        when(orderItemRepository.findByPurchaseOrder_IdOrderByIdAsc(7L))
                .thenReturn(List.of(TestData.orderItem(existing, p, 2)));

        // items given out of order: hash is order-independent
        OrderResponse res = service.createOrder("k", req(item(3L, 1), item(1L, 2)));

        assertThat(res.orderId()).isEqualTo(7L);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).lineTotal()).isEqualByComparingTo("20.00");
        verify(orderRepository, never()).saveAndFlush(any());
        assertThat(outcome("accepted")).isEqualTo(1.0);
    }

    @Test
    void sameKeyWithDifferentBodyIs409() {
        Order existing = TestData.reservedOrder(7L);
        when(orderRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 2))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(409));
    }

    @Test
    void raceLoserReplaysTheWinnersOrder() throws Exception {
        Order winner = TestData.reservedOrder(8L);
        ReflectionTestUtils.setField(winner, "requestHash", sha256("1:2"));
        Product p = TestData.product(1L, "A", "10.00", true);
        when(orderRepository.findByIdempotencyKey("k"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(productRepository.findAllById(any())).thenReturn(List.of(p));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("uk_orders_idempotency_key"));
        when(orderItemRepository.findByPurchaseOrder_IdOrderByIdAsc(8L))
                .thenReturn(List.of(TestData.orderItem(winner, p, 2)));

        OrderResponse res = service.createOrder("k", req(item(1L, 2)));

        assertThat(res.orderId()).isEqualTo(8L);
        assertThat(outcome("replay_after_race")).isEqualTo(1.0);
    }

    @Test
    void integrityViolationWithoutExistingOrderIsRethrown() {
        Product p = TestData.product(1L, "A", "10.00", true);
        when(orderRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(productRepository.findAllById(any())).thenReturn(List.of(p));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("something else"));

        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 2))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- business rules ----------

    @Test
    void unknownProductIs404AndIsNotCountedAsStockRejection() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(TestData.product(1L, "A", "1.00", true)));

        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 1), item(2L, 1))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(404));
        assertThat(outcome("insufficient_stock")).isZero();
    }

    @Test
    void inactiveProductIs409ButNotAStockRejection() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(TestData.product(1L, "A", "1.00", false)));

        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 1))))
                .satisfies(t -> {
                    assertThat(code(t)).isEqualTo(409);
                    assertThat(((ResponseStatusException) t).getReason()).contains("inactive");
                });
        assertThat(outcome("insufficient_stock")).isZero();
    }

    @Test
    void insufficientStockIs409AndCounted() {
        stubHappyPersistence();
        when(productRepository.findAllById(any()))
                .thenReturn(List.of(TestData.product(1L, "A", "1.00", true)));
        when(inventoryRepository.reserveStock(1L, 5)).thenReturn(0);

        assertThatThrownBy(() -> service.createOrder("k", req(item(1L, 5))))
                .satisfies(t -> assertThat(code(t)).isEqualTo(409));
        assertThat(outcome("insufficient_stock")).isEqualTo(1.0);
        verify(outboxService, never()).recordOrderEvent(any(), any(), any());
    }

    @Test
    void happyPathReservesStockRecordsMovementsAndOutboxEvent() {
        stubHappyPersistence();
        Product a = TestData.product(1L, "A", "10.00", true);
        Product b = TestData.product(2L, "B", "2.50", true);
        when(productRepository.findAllById(any())).thenReturn(List.of(a, b));

        // submitted out of order on purpose
        OrderResponse res = service.createOrder("key-1", req(item(2L, 4), item(1L, 2)));

        assertThat(res.orderId()).isEqualTo(900L);
        assertThat(res.status()).isEqualTo(OrderStatus.RESERVED);
        assertThat(res.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.totalAmount()).isEqualByComparingTo("30.00");
        assertThat(res.expiresAt()).isNotNull();
        assertThat(res.items()).extracting("productId").containsExactly(1L, 2L);
        verify(inventoryRepository).reserveStock(1L, 2);
        verify(inventoryRepository).reserveStock(2L, 4);
        verify(reservationRepository, org.mockito.Mockito.times(2)).save(any(InventoryReservation.class));
        verify(movementRepository, org.mockito.Mockito.times(2)).save(any(InventoryMovement.class));
        verify(outboxService).recordOrderEvent(
                org.mockito.ArgumentMatchers.eq(OrderEventType.ORDER_RESERVED),
                any(Order.class), any());
        assertThat(outcome("accepted")).isEqualTo(1.0);
    }

    // ---------- reads ----------

    @Test
    void getOrderReturnsItemsOrNotFound() {
        Order o = TestData.reservedOrder(5L);
        Product p = TestData.product(1L, "A", "10.00", true);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByPurchaseOrder_IdOrderByIdAsc(5L))
                .thenReturn(List.of(TestData.orderItem(o, p, 1)));
        when(orderRepository.findById(6L)).thenReturn(Optional.empty());

        OrderResponse res = service.getOrder(5L);

        assertThat(res.orderId()).isEqualTo(5L);
        assertThat(res.totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(res.items().get(0).productName()).isEqualTo("Name-A");
        assertThatThrownBy(() -> service.getOrder(6L)).satisfies(t -> assertThat(code(t)).isEqualTo(404));
    }
}
