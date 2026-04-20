package com.inventory.backend.service

import com.inventory.backend.dto.InventoryAssetResponse
import com.inventory.backend.dto.InventoryAssetMovementResponse
import com.inventory.backend.dto.TransferInventoryAssetRequest
import com.inventory.backend.dto.UpsertInventoryAssetRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.Department
import com.inventory.backend.entity.InventoryAsset
import com.inventory.backend.entity.InventoryAssetMovement
import com.inventory.backend.entity.InventoryAssetStatus
import com.inventory.backend.entity.Room
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.InventoryAssetMovementRepository
import com.inventory.backend.repository.InventoryAssetRepository
import com.inventory.backend.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InventoryAssetService(
    private val inventoryAssetRepository: InventoryAssetRepository,
    private val inventoryAssetMovementRepository: InventoryAssetMovementRepository,
    private val departmentRepository: DepartmentRepository,
    private val roomRepository: RoomRepository,
    private val actionLogService: ActionLogService,
) {

    @Transactional(readOnly = true)
    fun getAll(departmentId: Long?, roomId: Long?, status: InventoryAssetStatus?): List<InventoryAssetResponse> {
        val assets = when {
            roomId != null -> inventoryAssetRepository.findByRoomIdOrderByNameAsc(roomId)
            departmentId != null -> inventoryAssetRepository.findByDepartmentIdOrderByNameAsc(departmentId)
            status != null -> inventoryAssetRepository.findByStatusOrderByNameAsc(status)
            else -> inventoryAssetRepository.findAllByOrderByNameAsc()
        }

        return assets
            .asSequence()
            .filter { status == null || it.status == status }
            .map(::toResponse)
            .toList()
    }

    @Transactional
    fun create(request: UpsertInventoryAssetRequest): InventoryAssetResponse {
        val normalizedCode = request.inventoryCode.trim()
        if (inventoryAssetRepository.existsByInventoryCodeIgnoreCase(normalizedCode)) {
            throw ConflictException("Актив с таким инвентарным номером уже существует")
        }

        val (department, room) = resolveLocation(request.departmentId, request.roomId)
        val saved = inventoryAssetRepository.save(
            InventoryAsset().apply {
                inventoryCode = normalizedCode
                name = request.name.trim()
                category = request.category?.trim()?.ifBlank { null }
                this.department = department
                this.room = room
                status = request.status
                quantity = request.quantity
                comment = request.comment?.trim()?.ifBlank { null }
            }
        )

        actionLogService.log(
            ActionLogType.INVENTORY_ASSET_CREATED,
            saved.name,
            "Создан актив ${saved.inventoryCode}, статус: ${saved.status}, количество: ${saved.quantity}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun update(id: Long, request: UpsertInventoryAssetRequest): InventoryAssetResponse {
        val asset = inventoryAssetRepository.findById(id)
            .orElseThrow { NotFoundException("Актив не найден: $id") }

        val normalizedCode = request.inventoryCode.trim()
        if (inventoryAssetRepository.existsByInventoryCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
            throw ConflictException("Актив с таким инвентарным номером уже существует")
        }

        val (department, room) = resolveLocation(request.departmentId, request.roomId)
        asset.inventoryCode = normalizedCode
        asset.name = request.name.trim()
        asset.category = request.category?.trim()?.ifBlank { null }
        asset.department = department
        asset.room = room
        asset.status = request.status
        asset.quantity = request.quantity
        asset.comment = request.comment?.trim()?.ifBlank { null }

        val saved = inventoryAssetRepository.save(asset)

        actionLogService.log(
            ActionLogType.INVENTORY_ASSET_UPDATED,
            saved.name,
            "Обновлен актив ${saved.inventoryCode}, статус: ${saved.status}, количество: ${saved.quantity}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val asset = inventoryAssetRepository.findById(id)
            .orElseThrow { NotFoundException("Актив не найден: $id") }

        actionLogService.log(
            ActionLogType.INVENTORY_ASSET_DELETED,
            asset.name,
            "Удален актив ${asset.inventoryCode}",
            "Система"
        )

        inventoryAssetRepository.delete(asset)
    }

    @Transactional(readOnly = true)
    fun getMovements(assetId: Long?): List<InventoryAssetMovementResponse> {
        val rows = if (assetId == null) {
            inventoryAssetMovementRepository.findAllByOrderByMovedAtDesc()
        } else {
            inventoryAssetMovementRepository.findByAssetIdOrderByMovedAtDesc(assetId)
        }
        return rows.map(::toMovementResponse)
    }

    @Transactional
    fun transfer(id: Long, request: TransferInventoryAssetRequest): InventoryAssetResponse {
        val asset = inventoryAssetRepository.findById(id)
            .orElseThrow { NotFoundException("Актив не найден: $id") }
        if (asset.status == InventoryAssetStatus.WRITTEN_OFF) {
            throw BadRequestException("Списанный актив нельзя перемещать")
        }

        val fromDepartment = asset.department
        val fromRoom = asset.room
        val (toDepartment, toRoom) = resolveLocation(request.toDepartmentId, request.toRoomId)

        val sameDepartment = (fromDepartment?.id == toDepartment?.id)
        val sameRoom = (fromRoom?.id == toRoom?.id)
        if (sameDepartment && sameRoom) {
            throw BadRequestException("Новая локация совпадает с текущей")
        }

        asset.department = toDepartment
        asset.room = toRoom
        val saved = inventoryAssetRepository.save(asset)

        val movement = inventoryAssetMovementRepository.save(
            InventoryAssetMovement().apply {
                this.asset = saved
                this.fromDepartment = fromDepartment
                this.fromRoom = fromRoom
                this.toDepartment = toDepartment
                this.toRoom = toRoom
                movedAt = request.movedAt ?: LocalDateTime.now()
                actor = request.actor?.trim()?.ifBlank { "Система" } ?: "Система"
                comment = request.comment?.trim()?.ifBlank { null }
            }
        )

        actionLogService.log(
            ActionLogType.INVENTORY_ASSET_TRANSFERRED,
            saved.name,
            "Перемещение ${saved.inventoryCode}: ${locationLabel(fromDepartment, fromRoom)} -> ${locationLabel(toDepartment, toRoom)}",
            movement.actor ?: "Система"
        )

        return toResponse(saved)
    }

    private fun resolveLocation(departmentId: Long?, roomId: Long?): Pair<Department?, Room?> {
        val department = departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("Отдел не найден: $it") }
        }
        val room = roomId?.let {
            roomRepository.findById(it).orElseThrow { NotFoundException("Кабинет не найден: $it") }
        }

        if (room != null && department != null && room.department.id != department.id) {
            throw BadRequestException("Кабинет не принадлежит выбранному отделу")
        }

        val resolvedDepartment = department ?: room?.department
        return resolvedDepartment to room
    }

    private fun toResponse(asset: InventoryAsset): InventoryAssetResponse = InventoryAssetResponse(
        id = asset.id!!,
        inventoryCode = asset.inventoryCode,
        name = asset.name,
        category = asset.category,
        departmentId = asset.department?.id,
        departmentName = asset.department?.name,
        roomId = asset.room?.id,
        roomName = asset.room?.name,
        status = asset.status,
        quantity = asset.quantity,
        comment = asset.comment,
    )

    private fun toMovementResponse(movement: InventoryAssetMovement): InventoryAssetMovementResponse = InventoryAssetMovementResponse(
        id = movement.id!!,
        assetId = movement.asset.id!!,
        assetInventoryCode = movement.asset.inventoryCode,
        assetName = movement.asset.name,
        fromDepartmentId = movement.fromDepartment?.id,
        fromDepartmentName = movement.fromDepartment?.name,
        fromRoomId = movement.fromRoom?.id,
        fromRoomName = movement.fromRoom?.name,
        toDepartmentId = movement.toDepartment?.id,
        toDepartmentName = movement.toDepartment?.name,
        toRoomId = movement.toRoom?.id,
        toRoomName = movement.toRoom?.name,
        movedAt = movement.movedAt!!,
        actor = movement.actor,
        comment = movement.comment,
    )

    private fun locationLabel(department: Department?, room: Room?): String {
        val departmentName = department?.name ?: "Без отдела"
        val roomName = room?.name ?: "без кабинета"
        return "$departmentName / $roomName"
    }
}
