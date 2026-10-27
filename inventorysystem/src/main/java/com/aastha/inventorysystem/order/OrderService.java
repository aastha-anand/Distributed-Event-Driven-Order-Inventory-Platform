package com.aastha.inventorysystem.order;

import com.aastha.inventorysystem.inventory.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.InventoryRepository;
import com.aastha.inventorysystem.inventory.InventoryReservationRepository;
import com.aastha.inventorysystem.order.dto.CreateOrderRequest;
import com.aastha.inventorysystem.order.dto.OrderResponse;
import com.aastha.inventorysystem.product.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final int RESERVATION_MINUTES=15;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryMovementRepository movementRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public OrderResponse createOrder(
            String idempotencyKey,
            CreateOrderRequest request
    ){
        if(idempotencyKey==null
        || idempotencyKey.length()>150
        || idempotencyKey.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-key header is required");

        }
        if (request == null || request.items() == null
                || request.items().isEmpty()
                || request.items().size() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order must contain between 1 and 50 items");
        }



    }



}
