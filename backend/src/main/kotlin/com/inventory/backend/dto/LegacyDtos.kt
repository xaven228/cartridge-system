package com.inventory.backend.dto

import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.ActionLogEntityType
import com.inventory.backend.entity.ActionLogResult
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.DepartmentStatus
import com.inventory.backend.entity.PrinterColorMode
import com.inventory.backend.entity.PrinterDeviceType
import com.inventory.backend.entity.PrinterStatus
import com.inventory.backend.entity.RefillStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDate
import java.time.LocalDateTime

data class ActionLogResponse(
    val id: Long?,
    val actionType: ActionLogType?,
    val entityType: ActionLogEntityType?,
    val result: ActionLogResult?,
    val targetName: String?,
    val details: String?,
    val actor: String?,
    val deviceInfo: String?,
    val oldValues: String?,
    val newValues: String?,
    val manualDateTime: Boolean,
    val createdAt: LocalDateTime?,
)

data class CartridgeResponse(
    val id: Long?,
    val inventoryCode: String?,
    val cartridgeModelId: Long?,
    val cartridgeModelName: String?,
    val departmentId: Long?,
    val departmentName: String?,
    val quantity: Int?,
    val installedQuantity: Int?,
    val refillable: Boolean?,
    val empty: Boolean?,
    val status: CartridgeStatus?,
    val refillCount: Int?,
    val lastRefillDate: LocalDate?,
    val comment: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CurrentPrinterInstallationResponse(
    val cartridgeId: Long?,
    val inventoryCode: String?,
    val cartridgeModelName: String?,
    val quantity: Int?,
)

data class PrinterSlotResponse(
    val id: Long?,
    val name: String?,
    val cartridgeModelId: Long?,
    val cartridgeModelName: String?,
    val previousReplacementDate: LocalDate?,
    val lastReplacementDate: LocalDate?,
    val currentInstallation: CurrentPrinterInstallationResponse?,
)

data class DepartmentPrinterResponse(
    val id: Long?,
    val name: String?,
    val model: String?,
    val ipAddress: String?,
    val serialNumber: String?,
    val roomId: Long?,
    val roomName: String?,
    val deviceType: PrinterDeviceType?,
    val colorMode: PrinterColorMode?,
    val status: PrinterStatus?,
    val commissionedAt: LocalDate?,
    val writtenOffAt: LocalDate?,
    val comment: String?,
    val slots: List<PrinterSlotResponse>,
)

data class DepartmentResponse(
    val id: Long?,
    val name: String?,
    val description: String?,
    val status: DepartmentStatus?,
    val printers: List<DepartmentPrinterResponse>,
)

data class PrinterResponse(
    val id: Long?,
    val name: String?,
    val model: String?,
    val ipAddress: String?,
    val serialNumber: String?,
    val departmentId: Long?,
    val departmentName: String?,
    val roomId: Long?,
    val roomName: String?,
    val deviceType: PrinterDeviceType?,
    val colorMode: PrinterColorMode?,
    val status: PrinterStatus?,
    val commissionedAt: LocalDate?,
    val writtenOffAt: LocalDate?,
    val comment: String?,
    val slots: List<PrinterSlotResponse>,
)

data class RefillHistoryResponse(
    val id: Long?,
    val cartridgeId: Long?,
    val inventoryCode: String?,
    val sentAt: LocalDate?,
    val returnedAt: LocalDate?,
    val status: RefillStatus?,
    val quantity: Int?,
    val comment: String?,
    val createdBy: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

class AdjustQuantityRequest {
    @field:NotNull
    var quantity: Int? = null
    var comment: String? = null
}

class CreateCartridgeRequest {
    var inventoryCode: String? = null

    @field:NotNull
    var cartridgeModelId: Long? = null

    var departmentId: Long? = null

    @field:NotNull
    @field:Min(0)
    var quantity: Int? = null

    var refillable: Boolean? = null
    var status: CartridgeStatus? = null
    var comment: String? = null
}

class InstallCartridgeRequest {
    @field:NotNull
    var printerId: Long? = null

    @field:NotNull
    @field:Min(1)
    var quantity: Int? = null

    var comment: String? = null
}

class RemoveCartridgeInstallationRequest {
    @field:NotNull
    var printerId: Long? = null

    @field:NotNull
    @field:Min(1)
    var quantity: Int? = null

    var returnToStock: Boolean? = true
    var comment: String? = null
}

class ReplaceCartridgeRequest {
    @field:NotNull
    var printerId: Long? = null

    @field:NotBlank
    var removedOutcome: String? = null

    var comment: String? = null
    var actionDate: LocalDate? = null
    var createdBy: String? = null
}

class ReturnFromRefillRequest {
    @field:NotNull
    var returnedAt: LocalDate? = null

    @field:NotBlank
    var createdBy: String? = null

    @field:NotNull
    @field:Positive
    var quantity: Int? = null

    var comment: String? = null
}

class SendToRefillRequest {
    @field:NotNull
    var sentAt: LocalDate? = null

    @field:NotBlank
    var createdBy: String? = null

    @field:NotNull
    @field:Positive
    var quantity: Int? = null

    var comment: String? = null
}

class UpdateCartridgeRefillableRequest {
    @field:NotNull
    var refillable: Boolean? = null
}

class UpdateDepartmentRequest {
    @field:NotBlank
    var name: String? = null

    var description: String? = null

    @field:NotNull
    var status: DepartmentStatus? = DepartmentStatus.ACTIVE

    @field:Valid
    var printers: MutableList<DepartmentPrinterRequest> = mutableListOf()

    class DepartmentPrinterRequest {
        @field:NotBlank
        var name: String? = null
        var cartridgeModel: IdRef? = null
    }

    class IdRef {
        var id: Long? = null
    }
}

class UpsertPrinterRequest {
    @field:NotBlank
    var name: String? = null

    var model: String? = null

    var ipAddress: String? = null

    var serialNumber: String? = null

    @field:NotNull
    var departmentId: Long? = null

    @field:NotNull
    var roomId: Long? = null

    @field:NotNull
    var deviceType: PrinterDeviceType? = null

    @field:NotNull
    var colorMode: PrinterColorMode? = null

    @field:NotNull
    var status: PrinterStatus? = null

    var commissionedAt: LocalDate? = null

    var writtenOffAt: LocalDate? = null

    var comment: String? = null

    @field:Valid
    var slots: MutableList<PrinterSlotRequest> = mutableListOf()

    class PrinterSlotRequest {
        @field:NotBlank
        var name: String? = null

        @field:NotNull
        var cartridgeModelId: Long? = null
    }
}
