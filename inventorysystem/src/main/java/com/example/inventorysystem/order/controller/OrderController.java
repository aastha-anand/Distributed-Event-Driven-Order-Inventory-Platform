package com.example.inventorysystem.order.controller;

import com.example.inventorysystem.order.dto.OrderRequest;
import com.example.inventorysystem.order.dto.OrderResponse;
import com.example.inventorysystem.order.service.OrderCancellationService;
import com.example.inventorysystem.order.service.OrderPaymentService;
import com.example.inventorysystem.order.service.OrderService;
import com.example.inventorysystem.order.service.PaymentOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderService orderService;
    private final OrderPaymentService paymentService;
    private final OrderCancellationService cancellationService;

    public OrderController(
            OrderService orderService,
            OrderPaymentService paymentService,
            OrderCancellationService cancellationService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.cancellationService = cancellationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 150)
            String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {

        OrderResponse response =
                orderService.createOrder(idempotencyKey, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId) {
        cancellationService.cancelOrder(orderId);
        return orderService.getOrder(orderId);
    }

    // Payment-provider callbacks. Protected by an HMAC signature check in
    // ApiSecurityFilter (X-Webhook-Timestamp / X-Webhook-Signature).
    @PostMapping("/{orderId}/payment/success")
    public OrderResponse paymentSucceeded(@PathVariable Long orderId) {
        PaymentOutcome outcome = paymentService.paymentSucceeded(orderId);
        if (outcome == PaymentOutcome.RECONCILIATION_REQUIRED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation expired before payment arrived; "
                            + "flagged for reconciliation");
        }
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/payment/failure")
    public OrderResponse paymentFailed(@PathVariable Long orderId) {
        paymentService.paymentFailed(orderId);
        return orderService.getOrder(orderId);
    }
}

