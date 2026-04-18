package com.inventory.backend.service

import com.inventory.backend.dto.RoomResponse
import com.inventory.backend.dto.UpsertRoomRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.Room
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

        val normalizedName = request.name.trim()
        if (roomRepository.existsByDepartmentIdAndNameIgnoreCase(request.departmentId, normalizedName)) {
            throw ConflictException("Кабинет с таким названием уже существует в отделе")
        }

        val saved = roomRepository.save(
            Room.builder()
                .name(normalizedName)
                .department(department)
                .status(request.status)
                .comment(request.comment?.trim()?.ifBlank { null })
                .build()
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

        if (printerRepository.existsByRoomId(id)) {
            throw BadRequestException("Нельзя удалить кабинет, пока к нему привязаны принтеры")
        }

        actionLogService.log(
            ActionLogType.ROOM_DELETED,
            room.name,
            "Кабинет удален",
            "Система"
        )

        roomRepository.delete(room)
    }

    fun toResponse(room: Room): RoomResponse = RoomResponse(
        id = room.id,
        name = room.name,
        departmentId = room.department.id,
        departmentName = room.department.name,
        status = room.status,
        comment = room.comment,
    )
}
