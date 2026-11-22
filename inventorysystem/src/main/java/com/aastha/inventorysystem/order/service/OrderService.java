package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.event.OrderEventType;
import com.aastha.inventorysystem.event.OutboxService;
import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import com.aastha.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderItem;
import com.aastha.inventorysystem.order.dto.OrderRequest;
import com.aastha.inventorysystem.order.dto.OrderItemRequest;
import com.aastha.inventorysystem.order.dto.OrderItemResponse;
import com.aastha.inventorysystem.order.dto.OrderResponse;
import com.aastha.inventorysystem.order.repository.OrderItemRepository;
import com.aastha.inventorysystem.order.repository.OrderRepository;
import com.aastha.inventorysystem.product.model.Product;
import com.aastha.inventorysystem.product.repository.ProductRepository;
import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class OrderService {

    private static final int RESERVATION_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Counter ordersAccepted;
    private final Counter ordersReplayedAfterRace;
    private final Counter ordersRejectedStock;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryMovementRepository movementRepository,
            OutboxService outboxService,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
        this.outboxService = outboxService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.ordersAccepted = Counter.builder("inventory.orders")
                .tag("outcome", "accepted").register(meterRegistry);
        this.ordersReplayedAfterRace = Counter.builder("inventory.orders")
                .tag("outcome", "replay_after_race").register(meterRegistry);
        this.ordersRejectedStock = Counter.builder("inventory.orders")
                .tag("outcome", "insufficient_stock").register(meterRegistry);
    }

    /**
     * Not @Transactional on purpose. If two requests with the same
     * Idempotency-Key race, the loser fails on the unique index while inserting;
     * that failure poisons its transaction, so it must be caught OUTSIDE the
     * transaction and answered by replaying the winner's order.
     */
    public OrderResponse createOrder(
            String idempotencyKey,
            OrderRequest request) {

        try {
            OrderResponse created = transactionTemplate.execute(
                    status -> doCreateOrder(idempotencyKey, request));
            ordersAccepted.increment();
            return created;
        } catch (ResponseStatusException rejected) {
            if (rejected.getStatusCode().value() == HttpStatus.CONFLICT.value()
                    && rejected.getReason() != null
                    && rejected.getReason().startsWith("Insufficient stock")) {
                ordersRejectedStock.increment();
            }
            throw rejected;
        } catch (DataIntegrityViolationException raceLoser) {
            ordersReplayedAfterRace.increment();
            return readOnlyTransactionTemplate.execute(status ->
                    orderRepository.findByIdempotencyKey(idempotencyKey)
                            .map(existing -> replay(existing,
                                    calculateRequestHash(request)))
                            .orElseThrow(() -> raceLoser));
        }
    }

    private OrderResponse doCreateOrder(
            String idempotencyKey,
            OrderRequest request) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key header is required");
        }

        if (request == null || request.items() == null
                || request.items().isEmpty()
                || request.items().size() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order must contain between 1 and 50 items");
        }

        Set<Long> productIds = new HashSet<>();
        for (OrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null
                    || item.productId() <= 0
                    || item.quantity() == null
                    || item.quantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Each item needs a positive product ID and quantity");
            }

            if (!productIds.add(item.productId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate product IDs are not allowed");
            }
        }

        String requestHash = calculateRequestHash(request);

        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        Map<Long, Product> products = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            products.put(product.getId(), product);
        }

        if (products.size() != productIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more products do not exist");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.items()) {
            Product product = products.get(item.productId());

            if (!product.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Product " + item.productId() + " is inactive");
            }

            total = total.add(
                    product.getPrice().multiply(
                            BigDecimal.valueOf(item.quantity())));
        }

        OffsetDateTime expiresAt =
                OffsetDateTime.now(ZoneOffset.UTC)
                        .plusMinutes(RESERVATION_MINUTES);

        Order order = new Order(
                idempotencyKey, requestHash, total, expiresAt);

        orderRepository.saveAndFlush(order);

        List<OrderItem> savedItems = new ArrayList<>();

        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted((a, b) -> a.productId().compareTo(b.productId()))
                .toList();

        for (OrderItemRequest item : sortedItems) {
            Product product = products.get(item.productId());

            int updated = inventoryRepository.reserveStock(
                    product.getId(), item.quantity());

            if (updated != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock or inventory missing for product "
                                + product.getId());
            }

            OrderItem orderItem = orderItemRepository.save(
                    new OrderItem(
                            order,
                            product,
                            item.quantity(),
                            product.getPrice()));

            reservationRepository.save(
                    new InventoryReservation(
                            orderItem,
                            product,
                            item.quantity(),
                            expiresAt));

            movementRepository.save(
                    new InventoryMovement(
                            product.getId(),
                            "RESERVATION",
                            0,
                            item.quantity(),
                            "ORDER-" + order.getId()));

            savedItems.add(orderItem);
        }

        order.markReserved();

        // Same transaction as the order + reservations: all commit or none do.
        outboxService.recordOrderEvent(
                OrderEventType.ORDER_RESERVED, order, savedItems);

        savedItems.sort(
                (a, b) -> a.getProduct().getId()
                        .compareTo(b.getProduct().getId()));

        return toResponse(order, savedItems);
    }

    private OrderResponse replay(Order order, String requestHash) {
        if (!order.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used for a different request");
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items =
                orderItemRepository.findByPurchaseOrder_IdOrderByIdAsc(
                        order.getId());

        return toResponse(order, items);
    }

    private OrderResponse toResponse(
            Order order, List<OrderItem> items) {

        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                itemResponses);
    }

    private String calculateRequestHash(OrderRequest request) {
        String canonical = request.items().stream()
                .sorted((a, b) -> a.productId().compareTo(b.productId()))
                .map(item -> item.productId() + ":" + item.quantity())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }
}
