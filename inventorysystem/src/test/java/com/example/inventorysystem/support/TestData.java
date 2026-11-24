package com.example.inventorysystem.support;

import com.example.inventorysystem.inventory.model.Inventory;
import com.example.inventorysystem.inventory.model.InventoryReservation;
import com.example.inventorysystem.order.model.Order;
import com.example.inventorysystem.order.model.OrderItem;
import com.example.inventorysystem.product.model.Product;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Small factory for entities whose ids are normally assigned by the database. */
public final class TestData {

    private TestData() {
    }

    public static Product product(long id, String sku, String price, boolean active) {
        Product p = new Product(sku, "Name-" + sku, new BigDecimal(price), active);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    public static Inventory inventory(Product product, int onHand, int reserved) {
        Inventory inv = new Inventory(product, onHand);
        ReflectionTestUtils.setField(inv, "productId", product.getId());
        ReflectionTestUtils.setField(inv, "reserved", reserved);
        return inv;
    }

    /** A RESERVED / PENDING order expiring at the given time. */
    public static Order reservedOrder(long id, OffsetDateTime expiresAt) {
        Order o = new Order("key-" + id, "hash-" + id, new BigDecimal("20.00"), expiresAt);
        ReflectionTestUtils.setField(o, "id", id);
        o.markReserved();
        return o;
    }

    public static Order reservedOrder(long id) {
        return reservedOrder(id, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
    }

    public static OrderItem orderItem(Order order, Product product, int qty) {
        OrderItem item = new OrderItem(order, product, qty, product.getPrice());
        ReflectionTestUtils.setField(item, "id", product.getId() * 100);
        return item;
    }

    public static InventoryReservation reservation(Order order, Product product, int qty) {
        return new InventoryReservation(
                orderItem(order, product, qty), product, qty,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));
    }
}
