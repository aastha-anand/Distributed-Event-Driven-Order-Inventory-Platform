package com.example.inventorysystem.product.service;

import com.example.inventorysystem.inventory.model.Inventory;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.product.dto.ProductDetails;
import com.example.inventorysystem.product.dto.ProductRequest;
import com.example.inventorysystem.product.dto.ProductResponse;
import com.example.inventorysystem.product.dto.ProductUpdateRequest;
import com.example.inventorysystem.product.model.Product;
import com.example.inventorysystem.product.repository.ProductRepository;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock ProductCatalogService catalogService;
    @InjectMocks ProductService service;

    private static ResponseStatusException status(Throwable t) {
        return (ResponseStatusException) t;
    }

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);
        ProductRequest req = new ProductRequest("SKU-1", "Widget", new BigDecimal("9.99"), 5);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(status(t).getStatusCode().value()).isEqualTo(409));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithInitialStockRecordsReceiptMovement() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 7L);
            return p;
        });
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse res = service.create(
                new ProductRequest("SKU-1", "Widget", new BigDecimal("9.99"), 5));

        assertThat(res.sku()).isEqualTo("SKU-1");
        assertThat(res.name()).isEqualTo("Widget");
        assertThat(res.active()).isTrue();
        assertThat(res.onHand()).isEqualTo(5);
        assertThat(res.reserved()).isZero();
        assertThat(res.available()).isEqualTo(5);

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getMovementType()).isEqualTo("RECEIPT");
        assertThat(captor.getValue().getOnHandDelta()).isEqualTo(5);
        assertThat(captor.getValue().getReference()).isEqualTo("INITIAL_STOCK");
    }

    @Test
    void createWithZeroStockSkipsMovement() {
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse res = service.create(
                new ProductRequest("SKU-2", "Empty", new BigDecimal("1.00"), 0));

        assertThat(res.onHand()).isZero();
        verify(movementRepository, never()).save(any());
    }

    @Test
    void getProductCombinesCatalogDetailsWithLiveInventory() {
        Product p = TestData.product(3L, "SKU-3", "4.50", true);
        when(catalogService.getDetails(3L))
                .thenReturn(new ProductDetails(3L, "SKU-3", "Name-SKU-3", new BigDecimal("4.50"), true));
        when(inventoryRepository.findById(3L)).thenReturn(Optional.of(TestData.inventory(p, 10, 4)));

        ProductResponse res = service.getProduct(3L);

        assertThat(res.id()).isEqualTo(3L);
        assertThat(res.onHand()).isEqualTo(10);
        assertThat(res.reserved()).isEqualTo(4);
        assertThat(res.available()).isEqualTo(6);
    }

    @Test
    void getProductWithoutInventoryIs404() {
        when(catalogService.getDetails(3L))
                .thenReturn(new ProductDetails(3L, "S", "N", BigDecimal.ONE, true));
        when(inventoryRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct(3L))
                .satisfies(t -> assertThat(status(t).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void listSkipsProductsThatHaveNoInventoryRow() {
        Product withStock = TestData.product(1L, "A", "1.00", true);
        Product withoutStock = TestData.product(2L, "B", "2.00", true);
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withStock, withoutStock)));
        when(inventoryRepository.findAllById(any()))
                .thenReturn(List.of(TestData.inventory(withStock, 5, 1)));

        List<ProductResponse> result = service.list(0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).available()).isEqualTo(4);
    }

    @Test
    void updateWithNoFieldsIs400() {
        assertThatThrownBy(() -> service.update(1L, new ProductUpdateRequest(null, null, null)))
                .satisfies(t -> assertThat(status(t).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void updateUnknownProductIs404() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new ProductUpdateRequest("New", null, null)))
                .satisfies(t -> assertThat(status(t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateWithoutInventoryIs404() {
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(TestData.product(1L, "A", "1.00", true)));
        when(inventoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new ProductUpdateRequest("New", null, null)))
                .satisfies(t -> assertThat(status(t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateAppliesFieldsAndEvictsCache() {
        Product p = TestData.product(1L, "A", "1.00", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(TestData.inventory(p, 8, 2)));

        ProductResponse res = service.update(1L,
                new ProductUpdateRequest("Renamed", new BigDecimal("3.25"), false));

        assertThat(res.name()).isEqualTo("Renamed");
        assertThat(res.price()).isEqualByComparingTo("3.25");
        assertThat(res.active()).isFalse();
        assertThat(res.available()).isEqualTo(6);
        verify(productRepository).saveAndFlush(p);
        verify(catalogService).evictAfterCommit(1L);
    }
}
