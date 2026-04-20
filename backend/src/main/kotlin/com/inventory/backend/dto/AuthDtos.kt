package com.inventory.backend.dto

import com.inventory.backend.entity.UserRole
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
)

data class UserPermissionsResponse(
    val canViewCatalog: Boolean,
    val canEditCatalog: Boolean,
    val canOperate: Boolean,
    val canViewLogs: Boolean,
    val canExportReports: Boolean,
    val canManageUsers: Boolean,
    val canManageThresholds: Boolean,
    val canManualDatetime: Boolean,
)

data class AuthUserResponse(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val active: Boolean,
    val permissions: UserPermissionsResponse,
)

data class AuthResponse(
    val token: String,
    val expiresAt: Long,
    val user: AuthUserResponse,
)
