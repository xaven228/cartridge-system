package com.inventory.backend.service

import com.inventory.backend.dto.CurrentPrinterInstallationResponse
import com.inventory.backend.dto.DepartmentPrinterResponse
import com.inventory.backend.dto.DepartmentResponse
import com.inventory.backend.dto.PrinterSlotResponse
import com.inventory.backend.dto.UpdateDepartmentRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.Department
import com.inventory.backend.entity.DepartmentStatus
import com.inventory.backend.entity.PrinterStatus
import com.inventory.backend.entity.RoomStatus
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.PrinterInstallationRepository
import com.inventory.backend.repository.PrinterRepository
import com.inventory.backend.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val cartridgeRepository: CartridgeRepository,
    private val printerInstallationRepository: PrinterInstallationRepository,
    private val printerRepository: PrinterRepository,
    private val roomRepository: RoomRepository,
    private val actionLogService: ActionLogService,
) {
    @Transactional(readOnly = true)
    fun getAll(): List<DepartmentResponse> = departmentRepository.findAllByOrderByNameAsc().map(::toResponse)

    @Transactional
    fun create(request: UpdateDepartmentRequest): DepartmentResponse {
        val name = request.name!!.trim()
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw ConflictException("Отдел с таким названием уже существует: ${request.name}")
        }

        val department = Department()
        applyRequest(department, request)
        val saved = departmentRepository.save(department)
        actionLogService.log(ActionLogType.DEPARTMENT_CREATED, saved.name, "Создан отдел", "Система")
        return toResponse(saved)
    }

    @Transactional
    fun update(id: Long, request: UpdateDepartmentRequest): DepartmentResponse {
        val department = departmentRepository.findById(id)
            .orElseThrow { NotFoundException("Отдел не найден: $id") }
        val targetName = request.name!!.trim()

        val nameTaken = departmentRepository.existsByNameIgnoreCase(targetName) &&
            departmentRepository.findAll().any { it.id != id && it.name.equals(targetName, ignoreCase = true) }

        if (nameTaken) {
            throw ConflictException("Отдел с таким названием уже существует: ${request.name}")
        }

        validateDecommissioning(department.id!!, department.status, request.status!!)
        applyRequest(department, request)
        val saved = departmentRepository.save(department)
        actionLogService.log(ActionLogType.DEPARTMENT_UPDATED, saved.name, "Обновлен отдел", "Система")
        return toResponse(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val department = departmentRepository.findById(id)
            .orElseThrow { NotFoundException("Отдел не найден: $id") }

        validateDecommissioning(id, department.status, DepartmentStatus.DECOMMISSIONED)

        if (department.status == DepartmentStatus.DECOMMISSIONED) {
            return
        }

        department.status = DepartmentStatus.DECOMMISSIONED
        departmentRepository.save(department)
        actionLogService.log(
            ActionLogType.DEPARTMENT_DECOMMISSIONED,
            department.name,
            "Отдел переведен в статус DECOMMISSIONED",
            "Система",
        )
    }

    private fun applyRequest(department: Department, request: UpdateDepartmentRequest) {
        department.name = request.name!!.trim()
        department.description = request.description?.trim()?.ifBlank { null }
        department.status = request.status ?: DepartmentStatus.ACTIVE
        if (department.printers == null) {
            department.printers = mutableListOf()
        }
    }

    private fun validateDecommissioning(id: Long, currentStatus: DepartmentStatus, targetStatus: DepartmentStatus) {
        if (targetStatus != DepartmentStatus.DECOMMISSIONED || currentStatus == DepartmentStatus.DECOMMISSIONED) {
            return
        }

        if (cartridgeRepository.existsActiveStockByDepartmentId(id, CartridgeStatus.WRITTEN_OFF)) {
            throw ConflictException("Нельзя вывести отдел из использования, пока в нем есть активные картриджные остатки")
        }
        if (roomRepository.existsByDepartmentIdAndStatus(id, RoomStatus.ACTIVE)) {
            throw ConflictException("Нельзя вывести отдел из использования, пока в нем есть действующие кабинеты/залы")
        }
        if (printerRepository.existsByDepartmentIdAndStatusNot(id, PrinterStatus.WRITTEN_OFF)) {
            throw ConflictException("Нельзя вывести отдел из использования, пока в нем есть активные принтеры")
        }
    }

    private fun toResponse(department: Department): DepartmentResponse =
        DepartmentResponse(
            id = department.id,
            name = department.name,
            description = department.description,
            status = department.status,
            printers = mapPrinters(department),
        )

    private fun mapPrinters(department: Department): List<DepartmentPrinterResponse> =
        printerRepository.findByDepartmentIdOrderByIdAsc(department.id!!).map { printer ->
            DepartmentPrinterResponse(
                id = printer.id,
                name = printer.name,
                model = printer.model,
                ipAddress = printer.ipAddress,
                serialNumber = printer.serialNumber,
                roomId = printer.room?.id,
                roomName = printer.room?.name,
                deviceType = printer.deviceType,
                colorMode = printer.colorMode,
                status = printer.status,
                commissionedAt = printer.commissionedAt,
                writtenOffAt = printer.writtenOffAt,
                comment = printer.comment,
                slots = printer.slots.map { slot ->
                    val installation = slot.id?.let {
                        printerInstallationRepository.findFirstByPrinterSlotIdAndQuantityGreaterThan(it, 0).orElse(null)
                    }

                    PrinterSlotResponse(
                        id = slot.id,
                        name = slot.name,
                        cartridgeModelId = slot.cartridgeModel?.id,
                        cartridgeModelName = slot.cartridgeModel?.name,
                        previousReplacementDate = slot.previousReplacementDate,
                        lastReplacementDate = slot.lastReplacementDate,
                        currentInstallation = installation?.let {
                            CurrentPrinterInstallationResponse(
                                cartridgeId = it.cartridge.id,
                                inventoryCode = it.cartridge.inventoryCode,
                                cartridgeModelName = it.cartridge.cartridgeModel.name,
                                quantity = it.quantity,
                            )
                        },
                    )
                },
            )
        }
}
