package com.aastha.inventorysystem.inventory;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryService(InventoryRepository inventoryRepository, InventoryMovementRepository movementRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public void adjustStock(Long productId, int delta, String reference){
        if(delta==0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Stock adjustment cannot be zero"
            );
        }

        int updated= inventoryRepository.adjustStock(productId,delta);

        if(updated==0){
            if(!inventoryRepository.existsById(productId)){
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found"
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Adjustment would result in negative stock"
            );
        }

        movementRepository.save(
                new InventoryMovement(
                        productId,
                        "ADJUSTMENT",
                        delta,
                        0,
                        reference
                )
        );
    }
    @Transactional
    public void reserveStock(
            Long productId, int quantity, String orderReference) {
        if (quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reservation quantity must be positive");
        }

        int updated =
                inventoryRepository.reserveStock(productId, quantity);

        if (updated == 0) {
            if (!inventoryRepository.existsById(productId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory not found");
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient available stock");
        }

        movementRepository.save(
                new InventoryMovement(
                        productId, "RESERVATION", 0, quantity, orderReference));
    }

}
