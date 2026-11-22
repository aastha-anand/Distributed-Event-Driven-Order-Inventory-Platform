package com.example.inventorysystem.order.service;

import com.example.inventorysystem.event.OutboxEventRepository;
import com.example.inventorysystem.inventory.model.Inventory;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.order.dto.OrderItemRequest;
import com.example.inventorysystem.order.dto.OrderRequest;
import com.example.inventorysystem.order.dto.OrderResponse;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.product.dto.ProductRequest;
import com.example.inventorysystem.support.PostgresIntegrationBase;
import com.example.inventorysystem.product.service.ProductService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the headline claims against a real PostgreSQL: no overselling under
 * concurrency, idempotent order creation, exactly-once stock deduction, and
 * a durable reconciliation signal for late payments.
 */
class OrderFlowIT extends PostgresIntegrationBase {

    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired OrderPaymentService paymentService;
    @Autowired OrderCancellationService cancellationService;
    @Autowired OrderExpiryService expiryService;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryMovementRepository movementRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    // ---------------------------------------------------------------- helpers

    private Long newProduct(int stock) {
        return productService.create(new ProductRequest(
                "SKU-" + UUID.randomUUID(), "Widget",
                new BigDecimal("100.00"), stock)).id();
    }

    private static OrderRequest order(Long productId, int quantity) {
        return new OrderRequest(
                List.of(new OrderItemRequest(productId, quantity)));
    }

    private Inventory inventory(Long productId) {
        return inventoryRepository.findById(productId).orElseThrow();
    }

    private long movements(Long productId, String type) {
        return movementRepository
                .findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter((InventoryMovement m) -> type.equals(m.getMovementType()))
                .count();
    }

    /** Runs {@code tasks} callables on {@code threads} threads released together. */
    private static <T> List<T> concurrently(
            int threads, List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void neverOversellsUnderConcurrentLoad() throws Exception {
        int stock = 100;
        int attempts = 500;
        Long productId = newProduct(stock);
        String run = UUID.randomUUID().toString();

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String key = "oversell-" + run + "-" + i;
            tasks.add(() -> {
                try {
                    orderService.createOrder(key, order(productId, 1));
                    return true;
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                        return false;      // "Insufficient stock": expected
                    }
                    throw e;
                }
            });
        }

        long accepted = concurrently(64, tasks).stream().filter(b -> b).count();

        Inventory inv = inventory(productId);
        assertEquals(stock, accepted, "exactly the available units are sold");
        assertEquals(stock, inv.getReserved());
        assertEquals(stock, inv.getOnHand());
        assertEquals(0, inv.getAvailable());
        assertEquals(stock, movements(productId, "RESERVATION"));
    }

    @Test
    void sameIdempotencyKeyCreatesExactlyOneOrder() throws Exception {
        Long productId = newProduct(10);
        String key = "idem-" + UUID.randomUUID();

        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> orderService
                    .createOrder(key, order(productId, 2)).orderId());
        }

        Set<Long> orderIds = new HashSet<>(concurrently(32, tasks));

        assertEquals(1, orderIds.size(), "all retries replay the same order");
        assertEquals(2, inventory(productId).getReserved(),
                "stock is reserved once, not once per retry");
        assertEquals(1, movements(productId, "RESERVATION"));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        Long productId = newProduct(10);
        String key = "idem-conflict-" + UUID.randomUUID();

        orderService.createOrder(key, order(productId, 2));

        ResponseStatusException ex = org.junit.jupiter.api.Assertions
                .assertThrows(ResponseStatusException.class,
                        () -> orderService.createOrder(key, order(productId, 3)));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(2, inventory(productId).getReserved());
    }

    @Test
    void concurrentPaymentCallbacksDeductStockExactlyOnce() throws Exception {
        Long productId = newProduct(10);
        OrderResponse created = orderService.createOrder(
                "pay-" + UUID.randomUUID(), order(productId, 3));

        List<Callable<PaymentOutcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> paymentService.paymentSucceeded(created.orderId()));
        }
        List<PaymentOutcome> outcomes = concurrently(20, tasks);

        assertEquals(1, outcomes.stream()
                .filter(o -> o == PaymentOutcome.CONFIRMED).count());
        assertEquals(19, outcomes.stream()
                .filter(o -> o == PaymentOutcome.ALREADY_CONFIRMED).count());

        Inventory inv = inventory(productId);
        assertEquals(7, inv.getOnHand());
        assertEquals(0, inv.getReserved());
        assertEquals(1, movements(productId, "SALE"));
    }

    @Test
    void cancelReleasesReservationAndIsIdempotent() {
        Long productId = newProduct(10);
        OrderResponse created = orderService.createOrder(
                "cancel-" + UUID.randomUUID(), order(productId, 4));
        assertEquals(4, inventory(productId).getReserved());

        cancellationService.cancelOrder(created.orderId());
        cancellationService.cancelOrder(created.orderId());   // no-op

        assertEquals(0, inventory(productId).getReserved());
        assertEquals(10, inventory(productId).getOnHand());
        assertEquals(1, movements(productId, "RELEASE"));
        assertEquals(OrderStatus.CANCELLED,
                orderService.getOrder(created.orderId()).status());
    }

    @Test
    void expirySweepReleasesUnpaidReservations() {
        Long productId = newProduct(10);
        OrderResponse created = orderService.createOrder(
                "expire-" + UUID.randomUUID(), order(productId, 5));
        assertEquals(5, inventory(productId).getReserved());

        OffsetDateTime later = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(16);
        // The sweep handles 100 orders per pass and other tests leave
        // reservations behind, so drain it.
        while (expiryService.processExpiredOrders(later) > 0) {
            // keep sweeping
        }

        assertEquals(0, inventory(productId).getReserved());
        assertEquals(OrderStatus.EXPIRED,
                orderService.getOrder(created.orderId()).status());
    }

    @Test
    void latePaymentIsFlaggedForReconciliationAndDoesNotDeductStock() {
        Long productId = newProduct(10);
        OrderResponse created = orderService.createOrder(
                "late-" + UUID.randomUUID(), order(productId, 2));

        // Pretend the 15-minute hold ran out before the provider called back.
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                entityManager.createNativeQuery(
                                "UPDATE orders SET expires_at = now() - interval '1 minute' "
                                        + "WHERE id = :id")
                        .setParameter("id", created.orderId())
                        .executeUpdate());

        PaymentOutcome outcome = paymentService.paymentSucceeded(created.orderId());

        assertEquals(PaymentOutcome.RECONCILIATION_REQUIRED, outcome);
        assertEquals(10, inventory(productId).getOnHand(), "no stock deducted");
        assertEquals(0, movements(productId, "SALE"));
        assertTrue(outboxRepository.findAll().stream().anyMatch(e ->
                        "PAYMENT_RECONCILIATION_REQUIRED".equals(e.getEventType())
                                && String.valueOf(created.orderId())
                                .equals(e.getAggregateId())),
                "a durable reconciliation event was recorded");
    }
}
