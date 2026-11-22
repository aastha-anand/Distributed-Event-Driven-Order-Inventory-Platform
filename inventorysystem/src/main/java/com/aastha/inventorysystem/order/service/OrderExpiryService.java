package com.aastha.inventorysystem.order.service;

import com.aastha.inventorysystem.event.OrderEventType;
import com.aastha.inventorysystem.event.OutboxService;
import com.aastha.inventorysystem.inventory.model.InventoryReservation;
import com.aastha.inventorysystem.inventory.model.ReservationStatus;
import com.aastha.inventorysystem.inventory.repository.InventoryReservationRepository;
import com.aastha.inventorysystem.order.model.Order;
import com.aastha.inventorysystem.order.model.OrderStatus;
import com.aastha.inventorysystem.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OrderExpiryService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderExpiryService.class);

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OrderPaymentService paymentService;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    public OrderExpiryService(
            OrderRepository orderRepository,
            InventoryReservationRepository reservationRepository,
            OrderPaymentService paymentService,
            OutboxService outboxService,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.paymentService = paymentService;
        this.outboxService = outboxService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Deliberately NOT @Transactional: each order is expired in its own
    // transaction, so one bad order cannot roll back (and endlessly retry)
    // the whole batch.
    @Scheduled(fixedDelayString = "${inventory.order-expiry-delay-ms:30000}")
    public void processExpiredOrders() {
        processExpiredOrders(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Package-visible so tests can run the expiry sweep on demand. */
    int processExpiredOrders(OffsetDateTime now) {
        List<Long> expiredOrderIds = orderRepository.findExpiredOrderIds(
                OrderStatus.RESERVED,
                now,
                PageRequest.of(0, 100));

        int expired = 0;
        for (Long orderId : expiredOrderIds) {
            try {
                Boolean done = transactionTemplate.execute(
                        status -> expireOrder(orderId, now));
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to expire order {}", orderId, exception);
            }
        }
        return expired;
    }

    private boolean expireOrder(Long orderId, OffsetDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        // It may have been paid, cancelled, or expired by another request
        // between the batch query and acquiring the lock.
        if (order == null
                || order.getStatus() != OrderStatus.RESERVED
                || order.getExpiresAt().isAfter(now)) {
            return false;
        }

        List<InventoryReservation> reservations =
                reservationRepository.findByOrderIdForUpdate(orderId);

        paymentService.releaseReservations(
                reservations, orderId, ReservationStatus.EXPIRED);

        order.markExpired();
        outboxService.recordOrderEvent(
                OrderEventType.ORDER_EXPIRED, order, null);
        return true;
    }
}
