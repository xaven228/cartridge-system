package com.inventory.backend.dto

import com.inventory.backend.entity.HallRequestPriority
import com.inventory.backend.entity.HallRequestStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class HallRequestResponse(
    val id: Long,
    val roomId: Long,
    val roomName: String,
    val departmentId: Long,
    val departmentName: String,
    val requesterName: String,
    val title: String,
    val description: String?,
    val priority: HallRequestPriority,
    val status: HallRequestStatus,
    val requestedAt: LocalDateTime,
    val plannedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val slaDueAt: LocalDateTime,
    val slaOverdue: Boolean,
    val slaMinutesRemaining: Long,
)

data class UpsertHallRequestRequest(
    @field:NotNull
    val roomId: Long,
    @field:NotBlank
    val requesterName: String,
    @field:NotBlank
    val title: String,
    val description: String? = null,
    @field:NotNull
    val priority: HallRequestPriority = HallRequestPriority.MEDIUM,
    @field:NotNull
    val status: HallRequestStatus = HallRequestStatus.OPEN,
    val plannedAt: LocalDateTime? = null,
)
