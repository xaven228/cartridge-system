package com.inventory.backend.controller

import com.inventory.backend.dto.ActionLogResponse
import com.inventory.backend.entity.ActionLogEntityType
import com.inventory.backend.entity.ActionLogResult
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.service.ActionLogService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalTime

@RestController
@RequestMapping("/api/action-logs")
@CrossOrigin
@PreAuthorize("@authz.canViewLogs()")
class ActionLogController(
    private val actionLogService: ActionLogService,
) {
    @GetMapping
    fun getAll(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) actionType: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) result: String?,
        @RequestParam(required = false) targetName: String?,
    ): List<ActionLogResponse> {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must be less than or equal to dateTo")
        }

        val createdFrom = dateFrom?.atStartOfDay()
        val createdTo = dateTo?.atTime(LocalTime.MAX)
        val parsedActionType = parseActionType(actionType)
        val parsedEntityType = parseEntityType(entityType)
        val parsedResult = parseResult(result)

        return if (
            createdFrom == null &&
            createdTo == null &&
            parsedActionType == null &&
            parsedEntityType == null &&
            parsedResult == null &&
            actor.isNullOrBlank() &&
            targetName.isNullOrBlank()
        ) {
            actionLogService.getAll()
        } else {
            actionLogService.getFiltered(createdFrom, createdTo, actor, parsedActionType, parsedEntityType, parsedResult, targetName)
        }
    }

    private fun parseActionType(actionType: String?): ActionLogType? {
        if (actionType.isNullOrBlank()) {
            return null
        }

        return try {
            ActionLogType.valueOf(actionType.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported actionType: $actionType")
        }
    }

    private fun parseEntityType(entityType: String?): ActionLogEntityType? {
        if (entityType.isNullOrBlank()) {
            return null
        }

        return try {
            ActionLogEntityType.valueOf(entityType.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported entityType: $entityType")
        }
    }

    private fun parseResult(result: String?): ActionLogResult? {
        if (result.isNullOrBlank()) {
            return null
        }

        return try {
            ActionLogResult.valueOf(result.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported result: $result")
        }
    }
}
