package com.inventory.backend.repository

import com.inventory.backend.entity.Department
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DepartmentRepository : JpaRepository<Department, Long> {
    fun findAllByOrderByNameAsc(): List<Department>
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findByNameIgnoreCase(name: String): Optional<Department>
}
