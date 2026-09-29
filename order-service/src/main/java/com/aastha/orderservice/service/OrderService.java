package com.aastha.orderservice.service;

import com.aastha.orderservice.dto.CreateOrderRequest;
import com.aastha.orderservice.entity.Order;
import com.aastha.orderservice.entity.OrderStatus;
import com.aastha.orderservice.exception.OrderNotFoundException;
import com.aastha.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order=Order.builder()
                .productId(request.productId())
                .quantity(request.quantity())
                .status(OrderStatus.PENDING)
                .build();
        Order saved=orderRepository.save(order);
        eventProducer.publishOrderCreated(saved);
        return saved;
    }

    public Order getOrder(UUID id){
        return orderRepository.findById(id).
                orElseThrow(()->new OrderNotFoundException(id));
    }

}
