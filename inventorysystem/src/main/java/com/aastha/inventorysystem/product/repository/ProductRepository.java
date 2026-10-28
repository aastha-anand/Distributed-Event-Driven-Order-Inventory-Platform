package com.aastha.inventorysystem.product.repository;

import com.aastha.inventorysystem.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
}
