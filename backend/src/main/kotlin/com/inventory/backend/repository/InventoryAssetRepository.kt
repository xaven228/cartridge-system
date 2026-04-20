package com.inventory.backend.repository

import com.inventory.backend.entity.InventoryAsset
import com.inventory.backend.entity.InventoryAssetStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface InventoryAssetRepository : JpaRepository<InventoryAsset, Long> {
    fun existsByInventoryCodeIgnoreCase(inventoryCode: String): Boolean
    fun existsByInventoryCodeIgnoreCaseAndIdNot(inventoryCode: String, id: Long): Boolean
    fun findByInventoryCodeIgnoreCase(inventoryCode: String): Optional<InventoryAsset>
    fun findAllByOrderByNameAsc(): List<InventoryAsset>
    fun findByDepartmentIdOrderByNameAsc(departmentId: Long): List<InventoryAsset>
    fun findByRoomIdOrderByNameAsc(roomId: Long): List<InventoryAsset>
    fun findByStatusOrderByNameAsc(status: InventoryAssetStatus): List<InventoryAsset>
}
