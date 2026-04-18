package com.inventory.backend.dto

import com.inventory.backend.entity.RoomStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpsertRoomRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    val departmentId: Long,
    @field:NotNull
    val status: RoomStatus,
    val comment: String? = null,
)
