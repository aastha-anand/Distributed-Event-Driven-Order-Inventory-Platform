package com.example.inventorysystem.order.controller;

import com.example.inventorysystem.exception.GlobalExceptionHandler;
import com.example.inventorysystem.order.dto.OrderRequest;
import com.example.inventorysystem.order.dto.OrderResponse;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;
import com.example.inventorysystem.order.service.OrderCancellationService;
import com.example.inventorysystem.order.service.OrderPaymentService;
import com.example.inventorysystem.order.service.OrderService;
import com.example.inventorysystem.order.service.PaymentOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock OrderService orderService;
    @Mock OrderPaymentService paymentService;
    @Mock OrderCancellationService cancellationService;
    MockMvc mvc;

    private static OrderResponse response(OrderStatus s, PaymentStatus p) {
        return new OrderResponse(1L, s, p, new BigDecimal("20.00"), OffsetDateTime.now().plusMinutes(10), List.of());
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new OrderController(orderService, paymentService, cancellationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201() throws Exception {
        when(orderService.createOrder(eq("key-1"), any(OrderRequest.class)))
                .thenReturn(response(OrderStatus.RESERVED, PaymentStatus.PENDING));

        mvc.perform(post("/api/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    @Test
    void createWithoutIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required header: Idempotency-Key"));
    }

    @Test
    void createWithEmptyItemsIs400() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.items").exists());
    }

    @Test
    void createWithInvalidItemIs400() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":-1,\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insufficientStockFromServiceIs409() throws Exception {
        when(orderService.createOrder(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock"));

        mvc.perform(post("/api/orders").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock"));
    }

    @Test
    void getReturnsOrder() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(response(OrderStatus.RESERVED, PaymentStatus.PENDING));

        mvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void getUnknownOrderIs404() throws Exception {
        when(orderService.getOrder(9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        mvc.perform(get("/api/orders/9")).andExpect(status().isNotFound());
    }

    @Test
    void cancelCallsServiceThenReturnsFreshOrder() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(response(OrderStatus.CANCELLED, PaymentStatus.PENDING));

        mvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(cancellationService).cancelOrder(1L);
    }

    @Test
    void paymentSuccessConfirmsOrder() throws Exception {
        when(paymentService.paymentSucceeded(1L)).thenReturn(PaymentOutcome.CONFIRMED);
        when(orderService.getOrder(1L)).thenReturn(response(OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED));

        mvc.perform(post("/api/orders/1/payment/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void repeatedPaymentSuccessIsStill200() throws Exception {
        when(paymentService.paymentSucceeded(1L)).thenReturn(PaymentOutcome.ALREADY_CONFIRMED);
        when(orderService.getOrder(1L)).thenReturn(response(OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED));

        mvc.perform(post("/api/orders/1/payment/success")).andExpect(status().isOk());
    }

    @Test
    void latePaymentAnswers409() throws Exception {
        when(paymentService.paymentSucceeded(1L)).thenReturn(PaymentOutcome.RECONCILIATION_REQUIRED);

        mvc.perform(post("/api/orders/1/payment/success"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Reservation expired before payment arrived; flagged for reconciliation"));
    }

    @Test
    void paymentFailureReturnsFailedOrder() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(response(OrderStatus.PAYMENT_FAILED, PaymentStatus.FAILED));

        mvc.perform(post("/api/orders/1/payment/failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        verify(paymentService).paymentFailed(1L);
    }
}
