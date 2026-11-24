package com.example.inventorysystem.inventory.service;

import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryMovementRepository movementRepository;
    @InjectMocks InventoryService service;

    private static int code(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode().value();
    }

    @Test
    void zeroAdjustmentIs400() {
        assertThatThrownBy(() -> service.adjustStock(1L, 0, "ref"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(400));
        verify(inventoryRepository, never()).adjustStock(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void unknownProductIs404() {
        when(inventoryRepository.adjustStock(1L, 5)).thenReturn(0);
        when(inventoryRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.adjustStock(1L, 5, "ref"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(404));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjustmentBelowReservedStockIs409() {
        when(inventoryRepository.adjustStock(1L, -50)).thenReturn(0);
        when(inventoryRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.adjustStock(1L, -50, "ref"))
                .satisfies(t -> assertThat(code(t)).isEqualTo(409));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void successfulAdjustmentRecordsMovement() {
        when(inventoryRepository.adjustStock(1L, 7)).thenReturn(1);

        service.adjustStock(1L, 7, "cycle-count");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        InventoryMovement m = captor.getValue();
        assertThat(m.getProductId()).isEqualTo(1L);
        assertThat(m.getMovementType()).isEqualTo("ADJUSTMENT");
        assertThat(m.getOnHandDelta()).isEqualTo(7);
        assertThat(m.getReservedDelta()).isZero();
        assertThat(m.getReference()).isEqualTo("cycle-count");
    }
}
