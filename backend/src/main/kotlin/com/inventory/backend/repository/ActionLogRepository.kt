package com.inventory.backend.repository

import com.inventory.backend.entity.ActionLog
import com.inventory.backend.entity.ActionLogType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.LocalDateTime

interface ActionLogRepository : JpaRepository<ActionLog, Long>, JpaSpecificationExecutor<ActionLog> {
    fun findByActionTypeInAndCreatedAtBetween(
        actionTypes: Collection<ActionLogType>,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<ActionLog>
}
