package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import com.aastha.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderStatus;
import com.aastha.inventorysystem.order.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OrderPaymentService paymentService;

    public OrderExpiryService(
            OrderRepository orderRepository,
            InventoryReservationRepository reservationRepository,
            OrderPaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.paymentService = paymentService;
    }

    // Run every 30 seconds; process a bounded batch per transaction.
    @Scheduled(fixedDelayString = "${inventory.order-expiry-delay-ms:30000}")
    @Transactional
    public void processExpiredOrders() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Long> expiredOrderIds = orderRepository.findExpiredOrderIds(
                OrderStatus.RESERVED,
                now,
                PageRequest.of(0, 100));

        for (Long orderId : expiredOrderIds) {
            expireOrder(orderId, now);
        }
    }

    private void expireOrder(Long orderId, OffsetDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        // It may have been paid, cancelled, or expired by another request.
        if (order == null
                || order.getStatus() != OrderStatus.RESERVED
                || order.getExpiresAt().isAfter(now)) {
            return;
        }

        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdForUpdate(orderId);

        paymentService.releaseReservations(
                reservations, orderId, "RELEASE");

        order.markExpired();
    }
}
