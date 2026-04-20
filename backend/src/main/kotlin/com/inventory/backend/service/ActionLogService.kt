package com.inventory.backend.service

import com.inventory.backend.dto.ActionLogResponse
import com.inventory.backend.entity.ActionLog
import com.inventory.backend.entity.ActionLogEntityType
import com.inventory.backend.entity.ActionLogResult
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.security.AuthenticatedUser
import com.inventory.backend.repository.ActionLogRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

@Service
class ActionLogService(
    private val actionLogRepository: ActionLogRepository,
) {
    fun getAll(): List<ActionLogResponse> =
        actionLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).map(::toResponse)

    fun getFiltered(
        createdFrom: LocalDateTime?,
        createdTo: LocalDateTime?,
        actor: String?,
        actionType: ActionLogType?,
        entityType: ActionLogEntityType?,
        result: ActionLogResult?,
        targetName: String?,
    ): List<ActionLogResponse> {
        val spec = Specification<ActionLog> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            createdFrom?.let { predicates += cb.greaterThanOrEqualTo(root.get("createdAt"), it) }
            createdTo?.let { predicates += cb.lessThanOrEqualTo(root.get("createdAt"), it) }

            if (!actor.isNullOrBlank()) {
                predicates += cb.like(cb.lower(root.get("actor")), "%${actor.trim().lowercase()}%")
            }
            actionType?.let { predicates += cb.equal(root.get<Any>("actionType"), it) }
            entityType?.let { predicates += cb.equal(root.get<Any>("entityType"), it) }
            result?.let { predicates += cb.equal(root.get<Any>("result"), it) }
            if (!targetName.isNullOrBlank()) {
                predicates += cb.like(cb.lower(root.get("targetName")), "%${targetName.trim().lowercase()}%")
            }

            cb.and(*predicates.toTypedArray())
        }

        return actionLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).map(::toResponse)
    }

    fun log(
        actionType: ActionLogType,
        targetName: String,
        details: String,
        actor: String?,
        entityType: ActionLogEntityType = inferEntityType(actionType),
        result: ActionLogResult = ActionLogResult.SUCCESS,
        deviceInfo: String? = null,
        oldValues: String? = null,
        newValues: String? = null,
        manualDateTime: Boolean = false,
    ) {
        actionLogRepository.save(
            ActionLog().apply {
                this.actionType = actionType
                this.targetName = targetName
                this.details = details
                this.actor = resolveActor(actor)
                this.entityType = entityType
                this.result = result
                this.deviceInfo = deviceInfo?.trim()?.ifBlank { null }
                this.oldValues = oldValues?.trim()?.ifBlank { null }
                this.newValues = newValues?.trim()?.ifBlank { null }
                this.manualDateTime = manualDateTime
            },
        )
    }

    private fun resolveActor(actor: String?): String {
        if (!actor.isNullOrBlank()) {
            return actor
        }

        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        if (principal != null) {
            return principal.fullName.ifBlank { principal.username }
        }

        return "Система"
    }

    private fun inferEntityType(actionType: ActionLogType): ActionLogEntityType = when (actionType) {
        ActionLogType.USER_LOGIN -> ActionLogEntityType.AUTH
        ActionLogType.USER_CREATED, ActionLogType.USER_UPDATED -> ActionLogEntityType.USER
        ActionLogType.DEPARTMENT_CREATED,
        ActionLogType.DEPARTMENT_UPDATED,
        ActionLogType.DEPARTMENT_DELETED,
        ActionLogType.DEPARTMENT_DECOMMISSIONED -> ActionLogEntityType.DEPARTMENT
        ActionLogType.ROOM_CREATED,
        ActionLogType.ROOM_UPDATED,
        ActionLogType.ROOM_DELETED,
        ActionLogType.ROOM_DECOMMISSIONED -> ActionLogEntityType.ROOM
        ActionLogType.PRINTER_CREATED, ActionLogType.PRINTER_UPDATED, ActionLogType.PRINTER_WRITTEN_OFF -> ActionLogEntityType.PRINTER
        ActionLogType.CARTRIDGE_CREATED,
        ActionLogType.CARTRIDGE_QUANTITY_CHANGED,
        ActionLogType.CARTRIDGE_INSTALLED,
        ActionLogType.CARTRIDGE_REMOVED,
        ActionLogType.CARTRIDGE_SENT_TO_REFILL,
        ActionLogType.CARTRIDGE_RETURNED_FROM_REFILL,
        ActionLogType.CARTRIDGE_WRITTEN_OFF,
        ActionLogType.CARTRIDGE_MARKED_EMPTY,
        ActionLogType.CARTRIDGE_REFILLABLE_CHANGED,
        ActionLogType.CARTRIDGE_DELETED -> ActionLogEntityType.CARTRIDGE
        ActionLogType.CARTRIDGE_MODEL_CREATED, ActionLogType.CARTRIDGE_MODEL_DELETED -> ActionLogEntityType.CARTRIDGE_MODEL
        ActionLogType.THRESHOLD_CREATED, ActionLogType.THRESHOLD_UPDATED, ActionLogType.THRESHOLD_DELETED -> ActionLogEntityType.NOTIFICATION_THRESHOLD
        ActionLogType.INVENTORY_ASSET_CREATED,
        ActionLogType.INVENTORY_ASSET_UPDATED,
        ActionLogType.INVENTORY_ASSET_DELETED,
        ActionLogType.INVENTORY_ASSET_TRANSFERRED -> ActionLogEntityType.INVENTORY_ASSET
        ActionLogType.HALL_REQUEST_CREATED,
        ActionLogType.HALL_REQUEST_UPDATED,
        ActionLogType.HALL_REQUEST_DELETED,
        ActionLogType.HALL_REQUEST_ESCALATED -> ActionLogEntityType.HALL_REQUEST
    }

    private fun toResponse(actionLog: ActionLog): ActionLogResponse =
        ActionLogResponse(
            id = actionLog.id,
            actionType = actionLog.actionType,
            entityType = actionLog.entityType,
            result = actionLog.result,
            targetName = actionLog.targetName,
            details = actionLog.details,
            actor = actionLog.actor,
            deviceInfo = actionLog.deviceInfo,
            oldValues = actionLog.oldValues,
            newValues = actionLog.newValues,
            manualDateTime = actionLog.manualDateTime,
            createdAt = actionLog.createdAt,
        )
}
