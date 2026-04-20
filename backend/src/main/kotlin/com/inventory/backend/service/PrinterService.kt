package com.inventory.backend.service

import com.inventory.backend.dto.CurrentPrinterInstallationResponse
import com.inventory.backend.dto.PrinterResponse
import com.inventory.backend.dto.PrinterSlotResponse
import com.inventory.backend.dto.UpsertPrinterRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.CartridgeModel
import com.inventory.backend.entity.Department
import com.inventory.backend.entity.DepartmentStatus
import com.inventory.backend.entity.Printer
import com.inventory.backend.entity.PrinterInstallation
import com.inventory.backend.entity.PrinterStatus
import com.inventory.backend.entity.PrinterSlot
import com.inventory.backend.entity.Room
import com.inventory.backend.entity.RoomStatus
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.CartridgeModelRepository
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.PrinterInstallationRepository
import com.inventory.backend.repository.PrinterRepository
import com.inventory.backend.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PrinterService(
    private val printerRepository: PrinterRepository,
    private val departmentRepository: DepartmentRepository,
    private val cartridgeModelRepository: CartridgeModelRepository,
    private val printerInstallationRepository: PrinterInstallationRepository,
    private val roomRepository: RoomRepository,
    private val actionLogService: ActionLogService,
) {
    @Transactional(readOnly = true)
    fun getAll(): List<PrinterResponse> = printerRepository.findAll().map(::toResponse)

    @Transactional
    fun create(request: UpsertPrinterRequest): PrinterResponse {
        val department = resolveDepartment(request.departmentId!!)
        val room = resolveRoom(request.roomId!!, department)

        val printer = Printer().apply {
            name = request.name!!.trim()
            model = request.model?.trim()?.ifBlank { null }
            ipAddress = request.ipAddress?.trim()?.ifBlank { null }
            serialNumber = request.serialNumber?.trim()?.ifBlank { null }
            this.department = department
            this.room = room
            deviceType = request.deviceType!!
            colorMode = request.colorMode!!
            status = request.status!!
            commissionedAt = request.commissionedAt
            writtenOffAt = resolveWrittenOffAt(request.status!!, request.writtenOffAt)
            comment = request.comment?.trim()?.ifBlank { null }
            slots = mutableListOf()
        }

        applySlots(printer, request)
        val saved = printerRepository.save(printer)
        actionLogService.log(
            ActionLogType.PRINTER_CREATED,
            saved.name,
            "Создан принтер. Статус: ${saved.status}, слотов: ${saved.slots.size}",
            actor = null,
            newValues = printerAuditState(saved),
        )
        return toResponse(saved)
    }

    @Transactional
    fun update(id: Long, request: UpsertPrinterRequest): PrinterResponse {
        val printer = printerRepository.findById(id)
            .orElseThrow { NotFoundException("Принтер не найден: $id") }
        val department = resolveDepartment(request.departmentId!!)
        val room = resolveRoom(request.roomId!!, department)
        val oldValues = printerAuditState(printer)

        printer.name = request.name!!.trim()
        printer.model = request.model?.trim()?.ifBlank { null }
        printer.ipAddress = request.ipAddress?.trim()?.ifBlank { null }
        printer.serialNumber = request.serialNumber?.trim()?.ifBlank { null }
        printer.department = department
        printer.room = room
        printer.deviceType = request.deviceType!!
        printer.colorMode = request.colorMode!!
        printer.status = request.status!!
        printer.commissionedAt = request.commissionedAt
        printer.writtenOffAt = resolveWrittenOffAt(request.status!!, request.writtenOffAt)
        printer.comment = request.comment?.trim()?.ifBlank { null }

        applySlots(printer, request)
        val saved = printerRepository.save(printer)
        actionLogService.log(
            ActionLogType.PRINTER_UPDATED,
            saved.name,
            "Обновлен принтер. Статус: ${saved.status}, слотов: ${saved.slots.size}",
            actor = null,
            oldValues = oldValues,
            newValues = printerAuditState(saved),
        )
        return toResponse(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val printer = printerRepository.findById(id)
            .orElseThrow { NotFoundException("Принтер не найден: $id") }
        val oldValues = printerAuditState(printer)
        printer.status = PrinterStatus.WRITTEN_OFF
        if (printer.writtenOffAt == null) {
            printer.writtenOffAt = java.time.LocalDate.now()
        }
        val saved = printerRepository.save(printer)
        actionLogService.log(
            ActionLogType.PRINTER_WRITTEN_OFF,
            saved.name,
            "Принтер переведен в статус списан",
            actor = null,
            oldValues = oldValues,
            newValues = printerAuditState(saved),
        )
    }

    private fun applySlots(printer: Printer, request: UpsertPrinterRequest) {
        val slotRequests = normalizeSlotRequests(request)
        printer.slots.clear()
        printer.slots.addAll(
            slotRequests.map { slot ->
                val resolvedCartridgeModel = cartridgeModelRepository.findById(slot.cartridgeModelId!!)
                    .orElseThrow {
                        NotFoundException("Модель картриджа не найдена: ${slot.cartridgeModelId}")
                    }
                validateCompatibility(printer.model, resolvedCartridgeModel, slot.name!!.trim())
                PrinterSlot().apply {
                    name = slot.name!!.trim()
                    this.printer = printer
                    cartridgeModel = resolvedCartridgeModel
                }
            },
        )
    }

    private fun validateCompatibility(printerModel: String?, cartridgeModel: CartridgeModel, slotName: String) {
        val normalizedPrinterModel = printerModel?.trim()?.ifBlank { null } ?: return
        val compatibleModels = cartridgeModel.compatiblePrinterModels
            .map(String::trim)
            .filter(String::isNotBlank)

        if (compatibleModels.isEmpty()) {
            return
        }

        val compatible = compatibleModels.any { it.equals(normalizedPrinterModel, ignoreCase = true) }
        if (!compatible) {
            throw BadRequestException(
                "Модель картриджа \"${cartridgeModel.name}\" не совместима с принтером \"$normalizedPrinterModel\" для слота \"$slotName\"",
            )
        }
    }

    private fun normalizeSlotRequests(request: UpsertPrinterRequest): List<UpsertPrinterRequest.PrinterSlotRequest> {
        if (request.colorMode == com.inventory.backend.entity.PrinterColorMode.MONOCHROME && request.slots.isEmpty()) {
            throw NotFoundException("Для ч/б принтера нужно указать хотя бы один слот")
        }

        return request.slots.filter { !it.name.isNullOrBlank() }
    }

    fun toResponse(printer: Printer): PrinterResponse =
        PrinterResponse(
            id = printer.id,
            name = printer.name,
            model = printer.model,
            ipAddress = printer.ipAddress,
            serialNumber = printer.serialNumber,
            departmentId = printer.department?.id,
            departmentName = printer.department?.name,
            roomId = printer.room?.id,
            roomName = printer.room?.name,
            deviceType = printer.deviceType,
            colorMode = printer.colorMode,
            status = printer.status,
            commissionedAt = printer.commissionedAt,
            writtenOffAt = printer.writtenOffAt,
            comment = printer.comment,
            slots = printer.slots.map(::toSlotResponse),
        )

    private fun resolveDepartment(departmentId: Long): Department {
        val department = departmentRepository.findById(departmentId)
            .orElseThrow { NotFoundException("Отдел не найден: $departmentId") }

        if (department.status != DepartmentStatus.ACTIVE) {
            throw BadRequestException("Нельзя привязать принтер к отделу, который выведен из использования")
        }

        return department
    }

    private fun resolveRoom(roomId: Long, department: Department): Room {
        val room = roomRepository.findById(roomId)
            .orElseThrow { NotFoundException("Кабинет не найден: $roomId") }
        if (room.department == null || room.department.id != department.id) {
            throw NotFoundException("Кабинет не относится к выбранному отделу")
        }
        if (room.status != RoomStatus.ACTIVE) {
            throw BadRequestException("Нельзя привязать принтер к кабинету, который выведен из использования")
        }
        return room
    }

    private fun resolveWrittenOffAt(status: PrinterStatus, requestedDate: java.time.LocalDate?): java.time.LocalDate? =
        if (status == PrinterStatus.WRITTEN_OFF) requestedDate ?: java.time.LocalDate.now() else null

    private fun printerAuditState(printer: Printer): String {
        val slotSummary = printer.slots
            .sortedBy { it.name.lowercase() }
            .joinToString(",") { "${it.name}:${it.cartridgeModel?.name ?: "-"}" }

        return listOf(
            "name=${printer.name}",
            "model=${printer.model ?: "-"}",
            "department=${printer.department.name}",
            "room=${printer.room?.name ?: "-"}",
            "deviceType=${printer.deviceType}",
            "colorMode=${printer.colorMode}",
            "status=${printer.status}",
            "ip=${printer.ipAddress ?: "-"}",
            "serial=${printer.serialNumber ?: "-"}",
            "commissionedAt=${printer.commissionedAt ?: "-"}",
            "writtenOffAt=${printer.writtenOffAt ?: "-"}",
            "comment=${printer.comment ?: "-"}",
            "slots=[$slotSummary]",
        ).joinToString("; ")
    }

    fun toSlotResponse(slot: PrinterSlot): PrinterSlotResponse {
        val installation: PrinterInstallation? = slot.id?.let {
            printerInstallationRepository.findFirstByPrinterSlotIdAndQuantityGreaterThan(it, 0).orElse(null)
        }

        return PrinterSlotResponse(
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
    }
}
