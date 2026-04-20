package com.inventory.backend.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class NotificationAlertResponse(
    val cartridgeModelId: Long,
    val cartridgeModelName: String,
    val departmentId: Long,
    val departmentName: String,
    val currentQuantity: Int,
    val thresholdQuantity: Int,
    val source: String,
)

data class NotificationThresholdResponse(
    val id: Long,
    val cartridgeModelId: Long,
    val cartridgeModelName: String,
    val departmentId: Long?,
    val departmentName: String?,
    val minimumQuantity: Int,
    val active: Boolean,
    val comment: String?,
)

data class UpsertNotificationThresholdRequest(
    @field:NotNull
    val cartridgeModelId: Long,
    val departmentId: Long?,
    @field:NotNull
    @field:Min(0)
    val minimumQuantity: Int,
    val active: Boolean = true,
    val comment: String? = null,
)
