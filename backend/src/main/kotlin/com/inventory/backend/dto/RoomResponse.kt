package com.inventory.backend.dto

import com.inventory.backend.entity.RoomStatus

data class RoomResponse(
    val id: Long,
    val name: String,
    val departmentId: Long,
    val departmentName: String,
    val status: RoomStatus,
    val comment: String?,
)
