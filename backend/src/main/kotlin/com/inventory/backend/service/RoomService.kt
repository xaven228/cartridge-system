package com.inventory.backend.service

import com.inventory.backend.dto.RoomResponse
import com.inventory.backend.dto.UpsertRoomRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.DepartmentStatus
import com.inventory.backend.entity.PrinterStatus
import com.inventory.backend.entity.Room
import com.inventory.backend.entity.RoomStatus
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.PrinterRepository
import com.inventory.backend.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val departmentRepository: DepartmentRepository,
    private val printerRepository: PrinterRepository,
    private val actionLogService: ActionLogService,
) {

    @Transactional(readOnly = true)
    fun getAll(departmentId: Long?): List<RoomResponse> {
        val rooms = if (departmentId == null) {
            roomRepository.findAllByOrderByNameAsc()
        } else {
            roomRepository.findByDepartmentIdOrderByNameAsc(departmentId)
        }
        return rooms.map(::toResponse)
    }

    @Transactional
    fun create(request: UpsertRoomRequest): RoomResponse {
        val department = departmentRepository.findById(request.departmentId)
            .orElseThrow { NotFoundException("Отдел не найден: ${request.departmentId}") }
        validateDepartmentForRoomChange(department.status, false)

        val normalizedName = request.name.trim()
        if (roomRepository.existsByDepartmentIdAndNameIgnoreCase(request.departmentId, normalizedName)) {
            throw ConflictException("Кабинет с таким названием уже существует в отделе")
        }

        val saved = roomRepository.save(
            Room().apply {
                name = normalizedName
                this.department = department
                status = request.status
                comment = request.comment?.trim()?.ifBlank { null }
            }
        )

        actionLogService.log(
            ActionLogType.ROOM_CREATED,
            saved.name,
            "Создан кабинет в отделе ${department.name}, статус: ${saved.status}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun update(id: Long, request: UpsertRoomRequest): RoomResponse {
        val room = roomRepository.findById(id)
            .orElseThrow { NotFoundException("Кабинет не найден: $id") }

        val department = departmentRepository.findById(request.departmentId)
            .orElseThrow { NotFoundException("Отдел не найден: ${request.departmentId}") }
        validateDepartmentForRoomChange(department.status, room.department.id == department.id)

        val normalizedName = request.name.trim()
        if (roomRepository.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(request.departmentId, normalizedName, id)) {
            throw ConflictException("Кабинет с таким названием уже существует в отделе")
        }

        room.name = normalizedName
        room.department = department
        room.status = request.status
        room.comment = request.comment?.trim()?.ifBlank { null }

        val saved = roomRepository.save(room)

        actionLogService.log(
            ActionLogType.ROOM_UPDATED,
            saved.name,
            "Обновлен кабинет. Отдел: ${department.name}, статус: ${saved.status}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val room = roomRepository.findById(id)
            .orElseThrow { NotFoundException("Кабинет не найден: $id") }

        if (room.status == RoomStatus.DECOMMISSIONED) {
            return
        }
        if (printerRepository.existsByRoomIdAndStatusNot(id, PrinterStatus.WRITTEN_OFF)) {
            throw BadRequestException("Нельзя вывести кабинет из использования, пока к нему привязаны активные принтеры")
        }

        room.status = RoomStatus.DECOMMISSIONED
        val saved = roomRepository.save(room)

        actionLogService.log(
            ActionLogType.ROOM_DECOMMISSIONED,
            saved.name,
            "Кабинет переведен в статус DECOMMISSIONED",
            "Система"
        )
    }

    fun toResponse(room: Room): RoomResponse = RoomResponse(
        id = room.id!!,
        name = room.name,
        departmentId = room.department.id!!,
        departmentName = room.department.name,
        status = room.status,
        comment = room.comment,
    )

    private fun validateDepartmentForRoomChange(status: DepartmentStatus, isCurrentDepartment: Boolean) {
        if (status == DepartmentStatus.ACTIVE || isCurrentDepartment) {
            return
        }
        throw BadRequestException("Нельзя привязать кабинет к отделу, который выведен из использования")
    }
}
