package com.inventory.backend.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Convert
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}

@Entity
@Table(name = "action_logs")
class ActionLog : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    var actionType: ActionLogType = ActionLogType.CARTRIDGE_CREATED

    @Column(name = "target_name", nullable = false)
    var targetName: String = ""

    @Column(name = "details", length = 2000)
    var details: String? = null

    @Column(name = "actor")
    var actor: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 64)
    var entityType: ActionLogEntityType = ActionLogEntityType.SYSTEM

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 32)
    var result: ActionLogResult = ActionLogResult.SUCCESS

    @Column(name = "device_info", length = 1000)
    var deviceInfo: String? = null

    @Column(name = "old_values", length = 4000)
    var oldValues: String? = null

    @Column(name = "new_values", length = 4000)
    var newValues: String? = null

    @Column(name = "manual_datetime", nullable = false)
    var manualDateTime: Boolean = false
}

@Entity
@Table(name = "app_users")
class AppUser : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(name = "username", nullable = false, unique = true, length = 100)
    var username: String = ""

    @field:NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = ""

    @field:NotBlank
    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    var role: UserRole = UserRole.VIEWER

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true

    @Column(name = "can_view_catalog", nullable = false)
    var canViewCatalog: Boolean = true

    @Column(name = "can_edit_catalog", nullable = false)
    var canEditCatalog: Boolean = false

    @Column(name = "can_operate", nullable = false)
    var canOperate: Boolean = false

    @Column(name = "can_view_logs", nullable = false)
    var canViewLogs: Boolean = false

    @Column(name = "can_export_reports", nullable = false)
    var canExportReports: Boolean = false

    @Column(name = "can_manage_users", nullable = false)
    var canManageUsers: Boolean = false

    @Column(name = "can_manage_thresholds", nullable = false)
    var canManageThresholds: Boolean = false

    @Column(name = "can_manual_datetime", nullable = false)
    var canManualDatetime: Boolean = false
}

@Entity
@Table(name = "departments")
class Department : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(length = 1000)
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DepartmentStatus = DepartmentStatus.ACTIVE

    @JsonIgnore
    @OneToMany(mappedBy = "department")
    var cartridges: MutableList<Cartridge> = mutableListOf()

    @OneToMany(mappedBy = "department", cascade = [CascadeType.ALL], orphanRemoval = true)
    var printers: MutableList<Printer> = mutableListOf()
}

@Entity
@Table(name = "rooms")
class Room : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(nullable = false)
    var name: String = ""

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    lateinit var department: Department

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RoomStatus = RoomStatus.ACTIVE

    @Column(length = 1000)
    var comment: String? = null
}

@Entity
@Table(name = "printers")
class Printer : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(name = "model", length = 255)
    var model: String? = null

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null

    @Column(name = "serial_number", length = 255)
    var serialNumber: String? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    lateinit var department: Department

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    var room: Room? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    var deviceType: PrinterDeviceType = PrinterDeviceType.PRINTER

    @Enumerated(EnumType.STRING)
    @Column(name = "printer_type", nullable = false)
    var colorMode: PrinterColorMode = PrinterColorMode.MONOCHROME

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PrinterStatus = PrinterStatus.IN_OPERATION

    @Column(name = "commissioned_at")
    var commissionedAt: LocalDate? = null

    @Column(name = "written_off_at")
    var writtenOffAt: LocalDate? = null

    @Column(length = 1000)
    var comment: String? = null

    @JsonIgnore
    @OneToMany(mappedBy = "printer", cascade = [CascadeType.ALL], orphanRemoval = true)
    var slots: MutableList<PrinterSlot> = mutableListOf()
}

@Entity
@Table(name = "cartridge_models")
class CartridgeModel : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(name = "printer_model")
    var printerModel: String? = null

    var manufacturer: String? = null

    @Column(name = "color_type")
    var colorType: String? = null

    @Column(name = "resource_pages")
    var resourcePages: Int? = null

    @Column(name = "refillable", nullable = false)
    var refillable: Boolean = true

    @Column(name = "minimum_quantity", nullable = false)
    var minimumQuantity: Int = 0

    @Convert(converter = StringListConverter::class)
    @Column(name = "compatible_printer_models", length = 4000)
    var compatiblePrinterModels: MutableList<String> = mutableListOf()

    @JsonIgnore
    @OneToMany(mappedBy = "cartridgeModel")
    var cartridges: MutableList<Cartridge> = mutableListOf()
}

@Entity
@Table(name = "cartridges")
class Cartridge : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(name = "inventory_code", nullable = false, unique = true)
    var inventoryCode: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_model_id", nullable = false)
    lateinit var cartridgeModel: CartridgeModel

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    lateinit var department: Department

    @field:Min(0)
    @Column(nullable = false)
    var quantity: Int = 0

    @Column(name = "refillable", nullable = false)
    var refillable: Boolean = true

    @Column(name = "empty", nullable = false)
    var empty: Boolean = false

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CartridgeStatus = CartridgeStatus.IN_STOCK

    @Column(name = "refill_count", nullable = false)
    var refillCount: Int = 0

    @Column(name = "last_refill_date")
    var lastRefillDate: LocalDate? = null

    @Column(length = 1000)
    var comment: String? = null

    @JsonIgnore
    @OneToMany(mappedBy = "cartridge", cascade = [CascadeType.ALL], orphanRemoval = true)
    var refillHistory: MutableList<RefillHistory> = mutableListOf()

    @JsonIgnore
    @OneToMany(mappedBy = "cartridge", cascade = [CascadeType.ALL], orphanRemoval = true)
    var printerInstallations: MutableList<PrinterInstallation> = mutableListOf()
}

@Entity
@Table(name = "printer_slots")
class PrinterSlot : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(nullable = false)
    var name: String = ""

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id", nullable = false)
    lateinit var printer: Printer

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_model_id")
    var cartridgeModel: CartridgeModel? = null

    @Column(name = "previous_replacement_date")
    var previousReplacementDate: LocalDate? = null

    @Column(name = "last_replacement_date")
    var lastReplacementDate: LocalDate? = null

    @JsonIgnore
    @OneToMany(mappedBy = "printerSlot")
    var installations: MutableList<PrinterInstallation> = mutableListOf()
}

@Entity
@Table(name = "printer_installations")
class PrinterInstallation : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_slot_id", nullable = false)
    lateinit var printerSlot: PrinterSlot

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_id", nullable = false)
    lateinit var cartridge: Cartridge

    @field:Min(0)
    @Column(nullable = false)
    var quantity: Int = 0
}

@Entity
@Table(name = "refill_history")
class RefillHistory : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_id", nullable = false)
    lateinit var cartridge: Cartridge

    @Column(name = "sent_at")
    var sentAt: LocalDate? = null

    @Column(name = "returned_at")
    var returnedAt: LocalDate? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefillStatus = RefillStatus.SENT

    @Column(nullable = false)
    var quantity: Int = 0

    @Column(length = 1000)
    var comment: String? = null

    @Column(name = "created_by")
    var createdBy: String? = null
}

@Entity
@Table(name = "notification_thresholds")
class NotificationThreshold : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_model_id", nullable = false)
    lateinit var cartridgeModel: CartridgeModel

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null

    @field:Min(0)
    @Column(name = "minimum_quantity", nullable = false)
    var minimumQuantity: Int = 0

    @Column(name = "is_active", nullable = false)
    var active: Boolean = true

    @Column(length = 1000)
    var comment: String? = null
}

@Entity
@Table(name = "inventory_assets")
class InventoryAsset : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @field:NotBlank
    @Column(name = "inventory_code", nullable = false, unique = true)
    var inventoryCode: String = ""

    @field:NotBlank
    @Column(nullable = false)
    var name: String = ""

    @Column(length = 100)
    var category: String? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    var room: Room? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InventoryAssetStatus = InventoryAssetStatus.IN_USE

    @Column(nullable = false)
    var quantity: Int = 1

    @Column(length = 1000)
    var comment: String? = null
}

@Entity
@Table(name = "inventory_asset_movements")
class InventoryAssetMovement : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    lateinit var asset: InventoryAsset

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_department_id")
    var fromDepartment: Department? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_room_id")
    var fromRoom: Room? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id")
    var toDepartment: Department? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_room_id")
    var toRoom: Room? = null

    @Column(name = "moved_at", nullable = false)
    var movedAt: LocalDateTime? = null

    @Column(length = 255)
    var actor: String? = null

    @Column(length = 1000)
    var comment: String? = null
}

@Entity
@Table(name = "hall_requests")
class HallRequest : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    lateinit var room: Room

    @field:NotBlank
    @Column(name = "requester_name", nullable = false)
    var requesterName: String = ""

    @field:NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var priority: HallRequestPriority = HallRequestPriority.MEDIUM

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: HallRequestStatus = HallRequestStatus.OPEN

    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "planned_at")
    var plannedAt: LocalDateTime? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
}
