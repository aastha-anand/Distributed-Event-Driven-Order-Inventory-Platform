package com.example.inventorysystem.inventory.controller;

import com.example.inventorysystem.inventory.dto.InventoryResponse;
import com.example.inventorysystem.inventory.dto.MovementResponse;
import com.example.inventorysystem.inventory.dto.StockAdjustmentRequest;
import com.example.inventorysystem.inventory.model.Inventory;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import com.example.inventorysystem.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;
    private final InventoryMovementRepository movementRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryController(InventoryService inventoryService, InventoryMovementRepository movementRepository, InventoryRepository inventoryRepository) {
        this.inventoryService = inventoryService;
        this.movementRepository = movementRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(
            @PathVariable Long productId
    ){
        Inventory i=inventoryRepository.findById(productId)
                .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found"));

        return new InventoryResponse(
                i.getProductId(), i.getOnHand(),
                i.getReserved(), i.getAvailable());
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjust(
            @Valid @RequestBody StockAdjustmentRequest request) {
        inventoryService.adjustStock(
                request.productId(),
                request.delta(),
                request.reference());
    }

    @GetMapping("/{productId}/movements")
    public java.util.List<MovementResponse> getMovements(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (!inventoryRepository.existsById(productId)) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Inventory not found");
        }

        // Bounded page (max 200), newest first; never expose the JPA entity.
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        return movementRepository.findByProductId(productId, pageRequest)
                .map(MovementResponse::from)
                .getContent();
    }

}
