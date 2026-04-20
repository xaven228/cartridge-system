package com.inventory.backend.repository

import com.inventory.backend.entity.Printer
import com.inventory.backend.entity.PrinterColorMode
import com.inventory.backend.entity.PrinterStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PrinterRepository : JpaRepository<Printer, Long> {
    fun findByDepartmentIdOrderByIdAsc(departmentId: Long): List<Printer>
    fun countByColorMode(colorMode: PrinterColorMode): Long
    fun existsByRoomId(roomId: Long): Boolean
    fun existsByRoomIdAndStatusNot(roomId: Long, status: PrinterStatus): Boolean
    fun existsByDepartmentIdAndStatusNot(departmentId: Long, status: PrinterStatus): Boolean
}
