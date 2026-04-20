package com.inventory.backend.repository

import com.inventory.backend.entity.PrinterInstallation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PrinterInstallationRepository : JpaRepository<PrinterInstallation, Long> {
    fun findByCartridgeId(cartridgeId: Long): List<PrinterInstallation>
    fun findByCartridgeIdAndPrinterSlotId(cartridgeId: Long, printerSlotId: Long): Optional<PrinterInstallation>
    fun findFirstByPrinterSlotIdAndQuantityGreaterThan(printerSlotId: Long, quantity: Int): Optional<PrinterInstallation>
    fun countByPrinterSlotId(printerSlotId: Long): Long
}
