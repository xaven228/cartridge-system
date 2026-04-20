package com.inventory.backend.repository

import com.inventory.backend.entity.PrinterSlot
import org.springframework.data.jpa.repository.JpaRepository

interface PrinterSlotRepository : JpaRepository<PrinterSlot, Long> {
    fun findByPrinterIdOrderByIdAsc(printerId: Long): List<PrinterSlot>
    fun countByCartridgeModelId(cartridgeModelId: Long): Long
}
