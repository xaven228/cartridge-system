package com.inventory.backend.repository

import com.inventory.backend.entity.Cartridge
import com.inventory.backend.entity.CartridgeStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CartridgeRepository : JpaRepository<Cartridge, Long> {
    fun existsByInventoryCodeIgnoreCase(inventoryCode: String): Boolean
    fun countByDepartmentId(departmentId: Long): Long
    fun countByCartridgeModelId(cartridgeModelId: Long): Long
    fun findByDepartmentId(departmentId: Long): List<Cartridge>
    fun findByStatus(status: CartridgeStatus): List<Cartridge>
    fun findByStatusAndEmptyFalse(status: CartridgeStatus): List<Cartridge>
    fun findByDepartmentIdAndStatus(departmentId: Long, status: CartridgeStatus): List<Cartridge>

    @Query(
        """
        select case when count(c) > 0 then true else false end
        from Cartridge c
        where c.department.id = :departmentId
          and c.quantity > 0
          and c.status <> :writtenOffStatus
        """,
    )
    fun existsActiveStockByDepartmentId(
        @Param("departmentId") departmentId: Long,
        @Param("writtenOffStatus") writtenOffStatus: CartridgeStatus,
    ): Boolean

    @Query(
        """
        select c
        from Cartridge c
        where c.department.id = :departmentId
          and c.cartridgeModel.id = :cartridgeModelId
          and c.status = :status
          and c.refillable = :refillable
          and c.empty = :empty
        order by c.id asc
        """,
    )
    fun findCompatibleStockRows(
        @Param("departmentId") departmentId: Long,
        @Param("cartridgeModelId") cartridgeModelId: Long,
        @Param("status") status: CartridgeStatus,
        @Param("refillable") refillable: Boolean,
        @Param("empty") empty: Boolean,
    ): List<Cartridge>
}
