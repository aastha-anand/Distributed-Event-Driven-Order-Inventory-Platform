package com.example.inventorysystem.inventory.service;

import com.example.inventorysystem.inventory.model.InventoryMovement;
import com.example.inventorysystem.inventory.repository.InventoryMovementRepository;
import com.example.inventorysystem.inventory.repository.InventoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
}
