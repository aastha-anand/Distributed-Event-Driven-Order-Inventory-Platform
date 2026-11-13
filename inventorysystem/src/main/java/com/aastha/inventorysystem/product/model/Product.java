package com.aastha.inventorysystem.product.model;


import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Entity
@Table(name="products")
public class Product {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true,length = 100)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(String sku, String name, BigDecimal price, Boolean active) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.active = active == null || active;
    }

    public Product(String sku, String name, BigDecimal price) {
        this(sku, name, price, true);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Applies a partial update; null arguments leave the field unchanged. */
    public void update(String name, BigDecimal price, Boolean active) {
        if (name != null) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public boolean isActive() { return active;}

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

}
