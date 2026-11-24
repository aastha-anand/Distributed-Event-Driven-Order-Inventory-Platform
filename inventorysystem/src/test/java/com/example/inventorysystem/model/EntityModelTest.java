package com.example.inventorysystem.model;

import com.example.inventorysystem.inventory.model.Inventory;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.model.InventoryReservation;
import com.example.inventorysystem.inventory.model.ReservationStatus;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderItem;
import com.example.inventorysystem.order.model.OrderStatus;
import com.example.inventorysystem.order.model.PaymentStatus;
import com.example.inventorysystem.product.model.Product;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entities keep their lifecycle callbacks (@PrePersist / @PreUpdate) package-private or
 * protected, so they are invoked reflectively here, the same way JPA does.
 */
class EntityModelTest {

    @Test
    void productDefaultsPartialUpdatesAndCallbacks() {
        Product p = new Product("SKU", "Name", new BigDecimal("1.00"));
        assertThat(p.isActive()).isTrue();
        assertThat(new Product("S", "N", BigDecimal.ONE, null).isActive()).isTrue();
        assertThat(new Product("S", "N", BigDecimal.ONE, false).isActive()).isFalse();

        p.update(null, null, null);
        assertThat(p.getName()).isEqualTo("Name");
        p.update("New", new BigDecimal("2.00"), false);
        assertThat(p.getName()).isEqualTo("New");
        assertThat(p.getPrice()).isEqualByComparingTo("2.00");
        assertThat(p.isActive()).isFalse();
        assertThat(p.getSku()).isEqualTo("SKU");
        assertThat(p.getId()).isNull();

        ReflectionTestUtils.invokeMethod(p, "onCreate");
        assertThat((Object) ReflectionTestUtils.getField(p, "createdAt")).isNotNull();
        ReflectionTestUtils.invokeMethod(p, "onUpdate");
        assertThat((Object) ReflectionTestUtils.getField(p, "updatedAt")).isNotNull();
    }

    @Test
    void inventoryComputesAvailableStock() {
        Product p = TestData.product(1L, "A", "1.00", true);
        Inventory inv = TestData.inventory(p, 10, 4);
        assertThat(inv.getProductId()).isEqualTo(1L);
        assertThat(inv.getOnHand()).isEqualTo(10);
        assertThat(inv.getReserved()).isEqualTo(4);
        assertThat(inv.getAvailable()).isEqualTo(6);
        assertThat(inv.getVersion()).isNull();
    }

    @Test
    void inventoryMovementGettersAndCallback() {
        InventoryMovement m = new InventoryMovement(1L, "RECEIPT", 5, 0, "ref");
        assertThat(m.getProductId()).isEqualTo(1L);
        assertThat(m.getMovementType()).isEqualTo("RECEIPT");
        assertThat(m.getOnHandDelta()).isEqualTo(5);
        assertThat(m.getReservedDelta()).isZero();
        assertThat(m.getReference()).isEqualTo("ref");
        assertThat(m.getId()).isNull();
        assertThat(m.getCreatedAt()).isNull();
        ReflectionTestUtils.invokeMethod(m, "onCreate");
        assertThat(m.getCreatedAt()).isNotNull();
    }

    @Test
    void orderHappyPathAndCallbacks() {
        Order o = new Order("k", "h", new BigDecimal("5.00"), OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(o.getIdempotencyKey()).isEqualTo("k");
        assertThat(o.getRequestHash()).isEqualTo("h");
        assertThat(o.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(o.getExpiresAt()).isNotNull();
        assertThat(o.getId()).isNull();

        ReflectionTestUtils.invokeMethod(o, "onCreate");
        assertThat(o.getCreatedAt()).isNotNull();
        assertThat(o.getUpdatedAt()).isNotNull();
        ReflectionTestUtils.invokeMethod(o, "onUpdate");

        o.markReserved();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        o.markConfirmed();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void orderOtherOutcomes() {
        Order failed = TestData.reservedOrder(1L);
        failed.markPaymentFailed();
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);

        Order cancelled = TestData.reservedOrder(2L);
        cancelled.markCancelled();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order expired = TestData.reservedOrder(3L);
        expired.markExpired();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void orderRejectsIllegalTransitions() {
        Order fresh = new Order("k", "h", BigDecimal.ONE, OffsetDateTime.now());
        assertThatThrownBy(fresh::markConfirmed).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(fresh::markPaymentFailed).isInstanceOf(IllegalStateException.class);

        Order confirmed = TestData.reservedOrder(1L);
        confirmed.markConfirmed();
        assertThatThrownBy(confirmed::markCancelled).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(confirmed::markExpired).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(confirmed::markReserved).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void orderItemComputesLineTotal() {
        Order o = TestData.reservedOrder(1L);
        Product p = TestData.product(1L, "A", "2.50", true);
        OrderItem item = new OrderItem(o, p, 4, p.getPrice());

        assertThat(item.getLineTotal()).isEqualByComparingTo("10.00");
        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(item.getProduct()).isSameAs(p);
        assertThat(item.getPurchaseOrder()).isSameAs(o);
        assertThat(item.getId()).isNull();
    }

    @Test
    void reservationLifecycle() {
        Order o = TestData.reservedOrder(1L);
        Product p = TestData.product(1L, "A", "2.50", true);

        InventoryReservation released = TestData.reservation(o, p, 2);
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(released.getQuantity()).isEqualTo(2);
        assertThat(released.getProduct()).isSameAs(p);
        assertThat(released.getOrderItem()).isNotNull();
        assertThat(released.getExpiresAt()).isNotNull();
        assertThat(released.getId()).isNull();
        assertThat(released.getCreatedAt()).isNull();
        ReflectionTestUtils.invokeMethod(released, "onCreate");
        assertThat(released.getCreatedAt()).isNotNull();
        released.markReleased();
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        InventoryReservation consumed = TestData.reservation(o, p, 2);
        consumed.markConsumed();
        assertThat(consumed.getStatus()).isEqualTo(ReservationStatus.CONSUMED);

        InventoryReservation expired = TestData.reservation(o, p, 2);
        expired.markExpired();
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // a resolved reservation cannot be resolved again
        assertThatThrownBy(released::markConsumed).isInstanceOf(IllegalStateException.class);
    }
}
