package com.inventory.backend.security

import com.inventory.backend.entity.UserRole

data class AuthenticatedUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val canViewCatalog: Boolean,
    val canEditCatalog: Boolean,
    val canOperate: Boolean,
    val canViewLogs: Boolean,
    val canExportReports: Boolean,
    val canManageUsers: Boolean,
    val canManageThresholds: Boolean,
    val canManualDatetime: Boolean,
)
