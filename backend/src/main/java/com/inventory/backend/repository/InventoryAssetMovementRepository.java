package com.inventory.backend.repository;

import com.inventory.backend.entity.InventoryAssetMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryAssetMovementRepository extends JpaRepository<InventoryAssetMovement, Long> {
    List<InventoryAssetMovement> findAllByOrderByMovedAtDesc();

    List<InventoryAssetMovement> findByAssetIdOrderByMovedAtDesc(Long assetId);
}
