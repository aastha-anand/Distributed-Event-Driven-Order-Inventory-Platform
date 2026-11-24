package com.example.inventorysystem.inventory.controller;

import com.example.inventorysystem.exception.GlobalExceptionHandler;
import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.inventory.service.InventoryService;
import com.example.inventorysystem.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock InventoryService inventoryService;
    @Mock InventoryMovementRepository movementRepository;
    @Mock InventoryRepository inventoryRepository;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new InventoryController(inventoryService, movementRepository, inventoryRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getInventoryReturnsStockLevels() throws Exception {
        var product = TestData.product(4L, "S", "1.00", true);
        when(inventoryRepository.findById(4L)).thenReturn(Optional.of(TestData.inventory(product, 10, 3)));

        mvc.perform(get("/api/inventory/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(4))
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.reserved").value(3))
                .andExpect(jsonPath("$.available").value(7));
    }

    @Test
    void getInventoryUnknownProductIs404() throws Exception {
        when(inventoryRepository.findById(4L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/inventory/4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Inventory not found"));
    }

    @Test
    void adjustReturns204() throws Exception {
        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":4,\"delta\":5,\"reference\":\"recount\"}"))
                .andExpect(status().isNoContent());

        verify(inventoryService).adjustStock(4L, 5, "recount");
    }

    @Test
    void adjustWithMissingFieldsIs400() throws Exception {
        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.productId").exists())
                .andExpect(jsonPath("$.details.delta").exists());
    }

    @Test
    void adjustConflictFromServiceIs409() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "negative"))
                .when(inventoryService).adjustStock(eq(4L), eq(-9), any());

        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":4,\"delta\":-9}"))
                .andExpect(status().isConflict());
    }

    @Test
    void movementsForUnknownProductIs404() throws Exception {
        when(inventoryRepository.existsById(4L)).thenReturn(false);

        mvc.perform(get("/api/inventory/4/movements")).andExpect(status().isNotFound());
    }

    @Test
    void movementsAreMappedAndPageIsClamped() throws Exception {
        when(inventoryRepository.existsById(4L)).thenReturn(true);
        InventoryMovement m = new InventoryMovement(4L, "RECEIPT", 5, 0, "po-1");
        ReflectionTestUtils.setField(m, "id", 11L);
        ReflectionTestUtils.setField(m, "createdAt", OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        when(movementRepository.findByProductId(eq(4L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m)));

        mvc.perform(get("/api/inventory/4/movements?page=-1&size=9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].movementType").value("RECEIPT"))
                .andExpect(jsonPath("$[0].onHandDelta").value(5))
                .andExpect(jsonPath("$[0].reference").value("po-1"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findByProductId(eq(4L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void movementsPageSizeHasMinimumOfOne() throws Exception {
        when(inventoryRepository.existsById(4L)).thenReturn(true);
        when(movementRepository.findByProductId(eq(4L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/inventory/4/movements?size=0")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findByProductId(eq(4L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
