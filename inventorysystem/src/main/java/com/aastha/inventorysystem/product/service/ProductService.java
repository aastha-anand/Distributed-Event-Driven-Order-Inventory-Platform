package com.aastha.inventorysystem.product.service;

import com.aastha.inventorysystem.inventory.model.Inventory;
import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import com.aastha.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.product.dto.ProductRequest;
import com.aastha.inventorysystem.product.dto.ProductResponse;
import com.aastha.inventorysystem.product.model.Product;
import com.aastha.inventorysystem.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    public ProductService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "SKU Already Exists");
        }

        Product product = productRepository.saveAndFlush(
                new Product(
                        request.sku(),
                        request.name(),
                        request.price(),
                        true
                )
        );

        Inventory inventory = inventoryRepository.save(
                new Inventory(product, request.initialStock())
        );

        if (request.initialStock() > 0) {
            movementRepository.save(
                    new InventoryMovement(
                            product.getId(),
                            "RECEIPT",
                            request.initialStock(),
                            0,
                            "INITIAL_STOCK"
                    )
            );
        }
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable()
        );
    }
}
