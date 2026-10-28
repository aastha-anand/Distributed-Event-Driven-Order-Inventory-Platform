package com.aastha.inventorysystem.inventory.repository;

import com.aastha.inventorysystem.inventory.model.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
  List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
}
