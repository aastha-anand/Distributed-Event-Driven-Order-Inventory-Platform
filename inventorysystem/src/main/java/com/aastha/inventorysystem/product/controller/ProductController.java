package com.aastha.inventorysystem.product.controller;

import com.aastha.inventorysystem.product.service.ProductService;
import com.aastha.inventorysystem.product.dto.ProductRequest;
import com.aastha.inventorysystem.product.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

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
}
