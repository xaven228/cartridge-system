package com.inventory.backend.security

import com.inventory.backend.entity.UserRole

data class AuthenticatedUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: UserRole,
)
