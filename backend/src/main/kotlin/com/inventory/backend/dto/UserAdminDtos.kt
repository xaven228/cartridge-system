package com.inventory.backend.dto

import com.inventory.backend.entity.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UpsertUserRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val fullName: String,
    val password: String?,
    @field:NotNull
    val role: UserRole,
    @field:NotNull
    val active: Boolean,
    val permissions: UserPermissionsResponse? = null,
)

data class UserAdminResponse(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val active: Boolean,
    val permissions: UserPermissionsResponse,
)
