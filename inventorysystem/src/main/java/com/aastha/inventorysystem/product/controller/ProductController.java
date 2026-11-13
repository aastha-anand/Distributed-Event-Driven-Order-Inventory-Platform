package com.aastha.inventorysystem.product.controller;

import com.aastha.inventorysystem.product.dto.ProductRequest;
import com.aastha.inventorysystem.product.dto.ProductResponse;
import com.aastha.inventorysystem.product.dto.ProductUpdateRequest;
import com.aastha.inventorysystem.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @Valid @RequestBody ProductRequest request){
        return productService.create(request);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @PatchMapping("/{productId}")
    public ProductResponse update(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(productId, request);
    }
}
