package com.aastha.orderservice.exception;

import java.util.UUID;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order not found:"+id);
    }
}
