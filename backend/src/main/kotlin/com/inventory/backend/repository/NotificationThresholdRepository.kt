package com.inventory.backend.repository

import com.inventory.backend.entity.NotificationThreshold
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationThresholdRepository : JpaRepository<NotificationThreshold, Long> {
    fun findByActiveTrueOrderByIdAsc(): List<NotificationThreshold>
    fun existsByCartridgeModelIdAndDepartmentIdIsNull(cartridgeModelId: Long): Boolean
    fun existsByCartridgeModelIdAndDepartmentId(cartridgeModelId: Long, departmentId: Long): Boolean
    fun existsByCartridgeModelIdAndDepartmentIdIsNullAndIdNot(cartridgeModelId: Long, id: Long): Boolean
    fun existsByCartridgeModelIdAndDepartmentIdAndIdNot(
        cartridgeModelId: Long,
        departmentId: Long,
        id: Long,
    ): Boolean
}
