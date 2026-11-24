package com.example.inventorysystem.product.service;

import com.example.inventorysystem.product.dto.ProductDetails;
import com.example.inventorysystem.product.repository.ProductRepository;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    @InjectMocks ProductCatalogService service;

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getDetailsMapsProductToDto() {
        when(productRepository.findById(5L))
                .thenReturn(Optional.of(TestData.product(5L, "SKU-5", "12.00", true)));

        ProductDetails d = service.getDetails(5L);

        assertThat(d.id()).isEqualTo(5L);
        assertThat(d.sku()).isEqualTo("SKU-5");
        assertThat(d.name()).isEqualTo("Name-SKU-5");
        assertThat(d.price()).isEqualByComparingTo("12.00");
        assertThat(d.active()).isTrue();
    }

    @Test
    void getDetailsUnknownProductIs404() {
        when(productRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetails(5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void evictWithoutTransactionEvictsImmediately() {
        when(cacheManager.getCache(ProductCatalogService.CACHE_NAME)).thenReturn(cache);

        service.evictAfterCommit(9L);

        verify(cache).evict(9L);
    }

    @Test
    void evictInsideTransactionWaitsForCommit() {
        when(cacheManager.getCache(ProductCatalogService.CACHE_NAME)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        service.evictAfterCommit(9L);

        verify(cache, never()).evict(9L);
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.get(0).afterCommit();
        verify(cache).evict(9L);
    }

    @Test
    void evictToleratesMissingCache() {
        when(cacheManager.getCache(ProductCatalogService.CACHE_NAME)).thenReturn(null);

        assertThatCode(() -> service.evictAfterCommit(9L)).doesNotThrowAnyException();
    }

    @Test
    void evictSwallowsCacheFailures() {
        when(cacheManager.getCache(ProductCatalogService.CACHE_NAME)).thenReturn(cache);
        doThrow(new IllegalStateException("redis down")).when(cache).evict(9L);

        assertThatCode(() -> service.evictAfterCommit(9L)).doesNotThrowAnyException();
    }
}
