package com.inventory.backend.repository

import com.inventory.backend.entity.InventoryAssetMovement
import org.springframework.data.jpa.repository.JpaRepository

interface InventoryAssetMovementRepository : JpaRepository<InventoryAssetMovement, Long> {
    fun findAllByOrderByMovedAtDesc(): List<InventoryAssetMovement>
    fun findByAssetIdOrderByMovedAtDesc(assetId: Long): List<InventoryAssetMovement>
}
