package com.aastha.inventorysystem.order;

import com.aastha.inventorysystem.order.dto.CreateOrderRequest;
import com.aastha.inventorysystem.order.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 150)
            String idempotencyKey,

            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response =
                orderService.createOrder(idempotencyKey, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
