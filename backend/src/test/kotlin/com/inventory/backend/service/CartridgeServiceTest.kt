package com.inventory.backend.service

import com.inventory.backend.dto.CartridgeResponse
import com.inventory.backend.dto.CreateCartridgeRequest
import com.inventory.backend.dto.InstallCartridgeRequest
import com.inventory.backend.entity.Cartridge
import com.inventory.backend.entity.CartridgeModel
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.Department
import com.inventory.backend.entity.Printer
import com.inventory.backend.entity.PrinterInstallation
import com.inventory.backend.entity.PrinterSlot
import com.inventory.backend.entity.PrinterType
import com.inventory.backend.repository.CartridgeModelRepository
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.PrinterInstallationRepository
import com.inventory.backend.repository.PrinterRepository
import com.inventory.backend.repository.PrinterSlotRepository
import com.inventory.backend.repository.RefillHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

@ExtendWith(MockitoExtension::class)
class CartridgeServiceTest {
    @Mock
    private lateinit var cartridgeRepository: CartridgeRepository

    @Mock
    private lateinit var cartridgeModelRepository: CartridgeModelRepository

    @Mock
    private lateinit var departmentRepository: DepartmentRepository

    @Mock
    private lateinit var printerRepository: PrinterRepository

    @Mock
    private lateinit var printerSlotRepository: PrinterSlotRepository

    @Mock
    private lateinit var printerInstallationRepository: PrinterInstallationRepository

    @Mock
    private lateinit var refillHistoryRepository: RefillHistoryRepository

    @Mock
    private lateinit var actionLogService: ActionLogService

    @InjectMocks
    private lateinit var cartridgeService: CartridgeService

    @Test
    fun createShouldReturnResponseWithLocalDateTimeAuditFields() {
        val model = CartridgeModel().apply {
            id = 10L
            name = "HP 83A"
        }

        val department = Department().apply {
            id = 20L
            name = "Склад"
        }

        val request = CreateCartridgeRequest().apply {
            cartridgeModelId = model.id
            quantity = 2
            refillable = true
            status = CartridgeStatus.IN_STOCK
            comment = "new item"
        }

        val createdAt = LocalDateTime.of(2026, 3, 21, 15, 0, 0)
        val updatedAt = LocalDateTime.of(2026, 3, 21, 15, 1, 0)

        val saved = Cartridge().apply {
            id = 100L
            inventoryCode = "CRT-A1B2C3D4"
            cartridgeModel = model
            this.department = department
            quantity = request.quantity!!
            status = request.status!!
            refillCount = 0
            comment = request.comment
            this.createdAt = createdAt
            this.updatedAt = updatedAt
        }

        `when`(cartridgeModelRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        `when`(departmentRepository.findByNameIgnoreCase("Склад")).thenReturn(Optional.of(department))
        `when`(
            cartridgeRepository.findCompatibleStockRows(
                department.id!!,
                model.id!!,
                CartridgeStatus.IN_STOCK,
                true,
                false,
            ),
        ).thenReturn(listOf())
        `when`(cartridgeRepository.existsByInventoryCodeIgnoreCase(any())).thenReturn(false)
        `when`(cartridgeRepository.save(any(Cartridge::class.java))).thenReturn(saved)
        `when`(printerInstallationRepository.findByCartridgeId(saved.id!!)).thenReturn(listOf())

        val response: CartridgeResponse = cartridgeService.create(request)

        assertThat(response).isNotNull
        assertThat(response.id).isEqualTo(100L)
        assertThat(response.createdAt).isEqualTo(createdAt)
        assertThat(response.updatedAt).isEqualTo(updatedAt)
        assertThat(response.inventoryCode).isEqualTo("CRT-A1B2C3D4")
        assertThat(response.departmentId).isEqualTo(20L)
        assertThat(response.cartridgeModelId).isEqualTo(10L)
    }

    @Test
    fun createShouldMergeIntoExistingStockRowWhenInventoryCodeNotSpecified() {
        val model = CartridgeModel().apply {
            id = 10L
            name = "TK-1120"
        }

        val department = Department().apply {
            id = 20L
            name = "Склад"
        }

        val existingStock = Cartridge().apply {
            id = 100L
            inventoryCode = "CRT-STOCK"
            cartridgeModel = model
            this.department = department
            quantity = 3
            refillable = true
            empty = false
            status = CartridgeStatus.IN_STOCK
            refillCount = 0
            comment = ""
        }

        val request = CreateCartridgeRequest().apply {
            cartridgeModelId = model.id
            quantity = 2
            refillable = true
            comment = "new batch"
        }

        `when`(cartridgeModelRepository.findById(model.id!!)).thenReturn(Optional.of(model))
        `when`(departmentRepository.findByNameIgnoreCase("Склад")).thenReturn(Optional.of(department))
        `when`(
            cartridgeRepository.findCompatibleStockRows(
                department.id!!,
                model.id!!,
                CartridgeStatus.IN_STOCK,
                true,
                false,
            ),
        ).thenReturn(listOf(existingStock))
        `when`(cartridgeRepository.save(existingStock)).thenReturn(existingStock)
        `when`(printerInstallationRepository.findByCartridgeId(existingStock.id!!)).thenReturn(listOf())

        val response = cartridgeService.create(request)

        assertThat(existingStock.quantity).isEqualTo(5)
        assertThat(existingStock.comment).isEqualTo("new batch")
        assertThat(response.id).isEqualTo(100L)
        assertThat(response.quantity).isEqualTo(5)
        verify(cartridgeRepository, never()).existsByInventoryCodeIgnoreCase(any())
    }

    @Test
    fun installShouldSplitAggregateStockRowAndLeaveRestInStock() {
        val model = CartridgeModel().apply {
            id = 10L
            name = "TK-1120"
        }

        val department = Department().apply {
            id = 20L
            name = "Склад"
        }

        val stockCartridge = Cartridge().apply {
            id = 100L
            inventoryCode = "CRT-STOCK"
            cartridgeModel = model
            this.department = department
            quantity = 3
            refillable = true
            empty = false
            status = CartridgeStatus.IN_STOCK
            refillCount = 0
        }

        val printer = Printer().apply {
            id = 30L
            name = "IT / Точка 1"
            printerType = PrinterType.MONOCHROME
            this.department = department
            slots = mutableListOf()
        }

        val printerSlot = PrinterSlot().apply {
            id = 31L
            name = "Основной"
            this.printer = printer
            cartridgeModel = model
        }

        val request = InstallCartridgeRequest().apply {
            printerId = printerSlot.id
            quantity = 1
            comment = "install"
        }

        val generatedId = AtomicLong(500L)

        `when`(cartridgeRepository.findById(stockCartridge.id!!)).thenReturn(Optional.of(stockCartridge))
        `when`(printerSlotRepository.findById(printerSlot.id!!)).thenReturn(Optional.of(printerSlot))
        `when`(printerInstallationRepository.findFirstByPrinterSlotIdAndQuantityGreaterThan(printerSlot.id!!, 0))
            .thenReturn(Optional.empty())
        `when`(cartridgeRepository.existsByInventoryCodeIgnoreCase(any())).thenReturn(false)
        `when`(cartridgeRepository.save(any(Cartridge::class.java))).thenAnswer { invocation ->
            val saved = invocation.getArgument<Cartridge>(0)
            if (saved.id == null) {
                saved.id = generatedId.getAndIncrement()
            }
            saved
        }
        `when`(printerInstallationRepository.save(any(PrinterInstallation::class.java))).thenAnswer { invocation ->
            invocation.getArgument(0)
        }
        `when`(printerInstallationRepository.findByCartridgeId(eq(500L))).thenReturn(
            listOf(
                PrinterInstallation().apply {
                    this.printerSlot = printerSlot
                    cartridge = stockCartridge
                    quantity = 1
                },
            ),
        )

        val response = cartridgeService.installToPrinter(stockCartridge.id!!, request)

        assertThat(stockCartridge.quantity).isEqualTo(2)
        assertThat(response.id).isEqualTo(500L)
        assertThat(response.status).isEqualTo(CartridgeStatus.INSTALLED)
        assertThat(response.quantity).isEqualTo(0)
        assertThat(response.installedQuantity).isEqualTo(1)
        verify(printerInstallationRepository).save(any(PrinterInstallation::class.java))
    }
}
