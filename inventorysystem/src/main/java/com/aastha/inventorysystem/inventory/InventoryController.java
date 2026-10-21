package com.aastha.inventorysystem.inventory;

import com.aastha.inventorysystem.inventory.dto.InventoryResponse;
import com.aastha.inventorysystem.inventory.dto.StockAdjustmentRequest;
import com.aastha.inventorysystem.inventory.dto.StockReservationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    @PostMapping("/{productId}/reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(
            @PathVariable Long productId,
            @Valid @RequestBody StockReservationRequest request) {
        inventoryService.reserveStock(
                productId,
                request.quantity(),
                request.orderReference());
    }

    @GetMapping("/{productId}/movements")
    public java.util.List<InventoryMovement> getMovements(
            @PathVariable Long productId) {
        if (!inventoryRepository.existsById(productId)) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Inventory not found");
        }

        return movementRepository
                .findByProductIdOrderByCreatedAtDesc(productId);
    }

}
