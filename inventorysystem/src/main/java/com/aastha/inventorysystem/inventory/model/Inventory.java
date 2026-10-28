package com.aastha.inventorysystem.inventory.model;

import com.aastha.inventorysystem.product.model.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Inventory() {
    }

    public Inventory(Product product, int initialStock) {
        this.product = product;
        this.onHand = initialStock;
        this.reserved = 0;
    }

    public Long getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public int getAvailable() {
        return onHand - reserved;
    }

    public Long getVersion() {
        return version;
    }
}
