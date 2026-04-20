package com.inventory.backend.repository

import com.inventory.backend.entity.RefillHistory
import com.inventory.backend.entity.RefillStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RefillHistoryRepository : JpaRepository<RefillHistory, Long> {
    fun findByCartridgeIdOrderByIdDesc(cartridgeId: Long): List<RefillHistory>
    fun findFirstByCartridgeIdAndStatusOrderByIdDesc(cartridgeId: Long, status: RefillStatus): Optional<RefillHistory>
}
