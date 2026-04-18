package com.inventory.backend.controller

import com.inventory.backend.dto.InventoryAssetMovementResponse
import com.inventory.backend.dto.InventoryAssetResponse
import com.inventory.backend.dto.TransferInventoryAssetRequest
import com.inventory.backend.dto.UpsertInventoryAssetRequest
import com.inventory.backend.entity.InventoryAssetStatus
import com.inventory.backend.service.InventoryAssetService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/inventory-assets")
@CrossOrigin
class InventoryAssetController(
    private val inventoryAssetService: InventoryAssetService,
) {

    @GetMapping
    fun getAll(
        @RequestParam(required = false) departmentId: Long?,
        @RequestParam(required = false) roomId: Long?,
        @RequestParam(required = false) status: InventoryAssetStatus?,
    ): List<InventoryAssetResponse> = inventoryAssetService.getAll(departmentId, roomId, status)

    @GetMapping("/movements")
    fun getMovements(@RequestParam(required = false) assetId: Long?): List<InventoryAssetMovementResponse> =
        inventoryAssetService.getMovements(assetId)

    @GetMapping("/{id}/movements")
    fun getAssetMovements(@PathVariable id: Long): List<InventoryAssetMovementResponse> =
        inventoryAssetService.getMovements(id)

    @PostMapping
    fun create(@Valid @RequestBody request: UpsertInventoryAssetRequest): InventoryAssetResponse =
        inventoryAssetService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpsertInventoryAssetRequest): InventoryAssetResponse =
        inventoryAssetService.update(id, request)

    @PostMapping("/{id}/transfer")
    fun transfer(@PathVariable id: Long, @RequestBody request: TransferInventoryAssetRequest): InventoryAssetResponse =
        inventoryAssetService.transfer(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = inventoryAssetService.delete(id)
}
