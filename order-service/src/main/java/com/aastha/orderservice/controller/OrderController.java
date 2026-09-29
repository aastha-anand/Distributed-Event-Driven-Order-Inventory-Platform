package com.aastha.orderservice.controller;


import com.aastha.orderservice.dto.CreateOrderRequest;
import com.aastha.orderservice.dto.OrderResponse;
import com.aastha.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request){
        var order=orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id){
        return OrderResponse.from(orderService.getOrder(id));
    }
}
