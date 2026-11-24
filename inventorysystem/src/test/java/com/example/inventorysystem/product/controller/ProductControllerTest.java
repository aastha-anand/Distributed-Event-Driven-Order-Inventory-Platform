package com.example.inventorysystem.product.controller;

import com.example.inventorysystem.exception.GlobalExceptionHandler;
import com.example.inventorysystem.product.dto.ProductRequest;
import com.example.inventorysystem.product.dto.ProductResponse;
import com.example.inventorysystem.product.dto.ProductUpdateRequest;
import com.example.inventorysystem.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock ProductService productService;
    MockMvc mvc;

    private static ProductResponse sample() {
        return new ProductResponse(1L, "SKU-1", "Widget", new BigDecimal("9.99"), true, 10, 2, 8);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(sample());

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"price\":9.99,\"initialStock\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.available").value(8));
    }

    @Test
    void createWithInvalidBodyReturns400WithFieldDetails() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"name\":\"\",\"price\":-1,\"initialStock\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.sku").exists())
                .andExpect(jsonPath("$.details.price").exists());
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or malformed request body"));
    }

    @Test
    void createDuplicateSkuReturns409() throws Exception {
        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "SKU Already Exists"));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"price\":9.99,\"initialStock\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SKU Already Exists"));
    }

    @Test
    void getReturnsProduct() throws Exception {
        when(productService.getProduct(1L)).thenReturn(sample());

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void getWithNonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: productId"));
    }

    @Test
    void listUsesDefaultsAndClampsPageSize() throws Exception {
        when(productService.list(0, 20)).thenReturn(List.of(sample()));
        when(productService.list(0, 100)).thenReturn(List.of());
        when(productService.list(0, 1)).thenReturn(List.of());

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        mvc.perform(get("/api/products?page=-3&size=5000")).andExpect(status().isOk());
        verify(productService).list(0, 100);

        mvc.perform(get("/api/products?size=0")).andExpect(status().isOk());
        verify(productService).list(eq(0), eq(1));
    }

    @Test
    void patchUpdatesProduct() throws Exception {
        when(productService.update(eq(1L), any(ProductUpdateRequest.class))).thenReturn(sample());

        mvc.perform(patch("/api/products/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void patchWithBlankNameReturns400() throws Exception {
        mvc.perform(patch("/api/products/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
    }
}
