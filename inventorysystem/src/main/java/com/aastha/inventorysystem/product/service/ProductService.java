package com.aastha.inventorysystem.product.service;

import com.aastha.inventorysystem.inventory.model.Inventory;
import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import com.aastha.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.product.dto.ProductDetails;
import com.aastha.inventorysystem.product.dto.ProductRequest;
import com.aastha.inventorysystem.product.dto.ProductResponse;
import com.aastha.inventorysystem.product.dto.ProductUpdateRequest;
import com.aastha.inventorysystem.product.model.Product;
import com.aastha.inventorysystem.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductCatalogService catalogService;

    public ProductService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ProductCatalogService catalogService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.catalogService=catalogService;
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

        return toResponse(product, inventory);
    }


    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        ProductDetails details=catalogService.getDetails(productId);
        Inventory inventory=inventoryRepository.findById(productId)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found"
                ));
        return new ProductResponse(
                details.id(),
                details.sku(),
                details.name(),
                details.price(),
                details.active(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable()

        );
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(int page, int size) {
        Page<Product> products = productRepository.findAll(
                PageRequest.of(page, size, Sort.by("id")));

        List<Long> ids = products.getContent().stream()
                .map(Product::getId)
                .toList();

        Map<Long, Inventory> inventoryByProduct = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findAllById(ids)) {
            inventoryByProduct.put(inventory.getProductId(), inventory);
        }

        return products.getContent().stream()
                .filter(product -> inventoryByProduct.containsKey(product.getId()))
                .map(product -> toResponse(
                        product, inventoryByProduct.get(product.getId())))
                .toList();
    }

    @Transactional
    public ProductResponse update(Long productId, ProductUpdateRequest request) {
        if (request.name() == null
                && request.price() == null
                && request.active() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide at least one of: name, price, active");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Inventory not found"
                ));

        product.update(request.name(), request.price(), request.active());
        productRepository.saveAndFlush(product);

        catalogService.evictAfterCommit(productId);

        return toResponse(product, inventory);
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                product.isActive(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailable()
        );
    }
}
