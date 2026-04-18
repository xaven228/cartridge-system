package com.inventory.backend.dto

import com.inventory.backend.entity.SystemModuleCode
import com.inventory.backend.entity.SystemModuleStatus

data class SystemModuleResponse(
    val code: SystemModuleCode,
    val title: String,
    val status: SystemModuleStatus,
    val description: String,
    val plannedScope: String,
)
