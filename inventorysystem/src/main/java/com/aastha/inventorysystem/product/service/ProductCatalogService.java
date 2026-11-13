package com.aastha.inventorysystem.product.service;

import com.aastha.inventorysystem.product.dto.ProductDetails;
import com.aastha.inventorysystem.product.model.Product;
import com.aastha.inventorysystem.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductCatalogService {
    public static final String CACHE_NAME="products";

    private static final Logger log=
            LoggerFactory.getLogger(ProductCatalogService.class);

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    public ProductCatalogService(
            ProductRepository productRepository,
            CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#productId")
    public ProductDetails getDetails(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"));

        return new ProductDetails(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                product.isActive());
    }

    public void evictAfterCommit(Long productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evict(productId);
                        }
                    });
        } else {
            evict(productId);
        }
    }

    private void evict(Long productId) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.evict(productId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not evict product {} from cache", productId, e);
        }
    }
}
