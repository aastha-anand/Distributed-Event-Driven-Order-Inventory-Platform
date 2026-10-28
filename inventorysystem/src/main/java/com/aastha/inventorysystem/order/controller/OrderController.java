package com.aastha.inventorysystem.order.controller;

import com.aastha.inventorysystem.order.dto.OrderRequest;
import com.aastha.inventorysystem.order.dto.OrderResponse;
import com.aastha.inventorysystem.order.service.OrderCancellationService;
import com.aastha.inventorysystem.order.service.OrderPaymentService;
import com.aastha.inventorysystem.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


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

    // Development/demo endpoints only.
    // Replace these with verified payment-provider webhooks in production.
    @PostMapping("/{orderId}/payment/success")
    public OrderResponse paymentSucceeded(@PathVariable Long orderId) {
        paymentService.paymentSucceeded(orderId);
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/payment/failure")
    public OrderResponse paymentFailed(@PathVariable Long orderId) {
        paymentService.paymentFailed(orderId);
        return orderService.getOrder(orderId);
    }
}

