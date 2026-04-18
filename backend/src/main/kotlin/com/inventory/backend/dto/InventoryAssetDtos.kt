package com.inventory.backend.dto

import com.inventory.backend.entity.InventoryAssetStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class InventoryAssetResponse(
    val id: Long,
    val inventoryCode: String,
    val name: String,
    val category: String?,
    val departmentId: Long?,
    val departmentName: String?,
    val roomId: Long?,
    val roomName: String?,
    val status: InventoryAssetStatus,
    val quantity: Int,
    val comment: String?,
)

data class UpsertInventoryAssetRequest(
    @field:NotBlank
    val inventoryCode: String,
    @field:NotBlank
    val name: String,
    val category: String? = null,
    val departmentId: Long? = null,
    val roomId: Long? = null,
    @field:NotNull
    val status: InventoryAssetStatus,
    @field:Min(0)
    val quantity: Int,
    val comment: String? = null,
)

data class TransferInventoryAssetRequest(
    val toDepartmentId: Long? = null,
    val toRoomId: Long? = null,
    val actor: String? = null,
    val comment: String? = null,
    val movedAt: LocalDateTime? = null,
)

data class InventoryAssetMovementResponse(
    val id: Long,
    val assetId: Long,
    val assetInventoryCode: String,
    val assetName: String,
    val fromDepartmentId: Long?,
    val fromDepartmentName: String?,
    val fromRoomId: Long?,
    val fromRoomName: String?,
    val toDepartmentId: Long?,
    val toDepartmentName: String?,
    val toRoomId: Long?,
    val toRoomName: String?,
    val movedAt: LocalDateTime,
    val actor: String?,
    val comment: String?,
)
