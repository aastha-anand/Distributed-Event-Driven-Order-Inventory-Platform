package com.aastha.inventorysystem.order;




import com.aastha.inventorysystem.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "order_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_order_product",
                        columnNames = {"order_id", "product_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_order_items_order_id",
                        columnList = "order_id"
                )
        }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(
            name = "unit_price",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal unitPrice;

    protected OrderItem() {
        // Required by JPA.
    }

    public OrderItem(
            Order purchaseOrder,
            Product product,
            Integer quantity,
            BigDecimal unitPrice) {

        this.purchaseOrder = purchaseOrder;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }

    public Long getId() {
        return id;
    }

    public Order getPurchaseOrder() {
        return purchaseOrder;
    }

    public Product getProduct() {
        return product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
