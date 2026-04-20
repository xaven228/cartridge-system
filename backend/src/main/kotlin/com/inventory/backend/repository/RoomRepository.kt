package com.inventory.backend.repository

import com.inventory.backend.entity.Room
import com.inventory.backend.entity.RoomStatus
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room, Long> {
    fun findAllByOrderByNameAsc(): List<Room>
    fun findByDepartmentIdOrderByNameAsc(departmentId: Long): List<Room>
    fun countByDepartmentId(departmentId: Long): Long
    fun existsByDepartmentIdAndStatus(departmentId: Long, status: RoomStatus): Boolean
    fun existsByDepartmentIdAndNameIgnoreCase(departmentId: Long, name: String): Boolean
    fun existsByDepartmentIdAndNameIgnoreCaseAndIdNot(departmentId: Long, name: String, id: Long): Boolean
}
