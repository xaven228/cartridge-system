package com.inventory.backend.service

import com.inventory.backend.dto.UpsertUserRequest
import com.inventory.backend.dto.UserAdminResponse
import com.inventory.backend.dto.UserPermissionsResponse
import com.inventory.backend.entity.AppUser
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.AppUserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAdminService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional(readOnly = true)
    fun getAll(): List<UserAdminResponse> = appUserRepository.findAll()
        .sortedBy { it.username.lowercase() }
        .map(::toResponse)

    @Transactional
    fun create(request: UpsertUserRequest): UserAdminResponse {
        val username = request.username.trim()
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw ConflictException("Пользователь с таким логином уже существует")
        }
        val password = request.password?.trim().orEmpty()
        if (password.isBlank()) {
            throw BadRequestException("Для нового пользователя нужен пароль")
        }

        val user = AppUser.builder()
            .username(username)
            .fullName(request.fullName.trim())
            .passwordHash(passwordEncoder.encode(password))
            .role(request.role)
            .active(request.active)
            .build()

        applyPermissions(user, request)
        return toResponse(appUserRepository.save(user))
    }

    @Transactional
    fun update(id: Long, request: UpsertUserRequest): UserAdminResponse {
        val user = appUserRepository.findById(id)
            .orElseThrow { NotFoundException("Пользователь не найден: $id") }

        val username = request.username.trim()
        val duplicate = appUserRepository.findByUsernameIgnoreCase(username).orElse(null)
        if (duplicate != null && duplicate.id != id) {
            throw ConflictException("Пользователь с таким логином уже существует")
        }

        user.username = username
        user.fullName = request.fullName.trim()
        user.role = request.role
        user.active = request.active

        val password = request.password?.trim()
        if (!password.isNullOrBlank()) {
            user.passwordHash = passwordEncoder.encode(password)
        }

        applyPermissions(user, request)
        return toResponse(appUserRepository.save(user))
    }

    private fun applyPermissions(user: AppUser, request: UpsertUserRequest) {
        val defaults = defaultPermissionsByRole(request.role)
        val source = request.permissions ?: defaults

        user.canViewCatalog = source.canViewCatalog
        user.canEditCatalog = source.canEditCatalog
        user.canOperate = source.canOperate
        user.canViewLogs = source.canViewLogs
        user.canExportReports = source.canExportReports
        user.canManageUsers = source.canManageUsers
        user.canManageThresholds = source.canManageThresholds
        user.canManualDatetime = source.canManualDatetime
    }

    private fun defaultPermissionsByRole(role: com.inventory.backend.entity.UserRole): UserPermissionsResponse {
        return when (role) {
            com.inventory.backend.entity.UserRole.ADMIN -> UserPermissionsResponse(
                canViewCatalog = true,
                canEditCatalog = true,
                canOperate = true,
                canViewLogs = true,
                canExportReports = true,
                canManageUsers = true,
                canManageThresholds = true,
                canManualDatetime = true,
            )

            com.inventory.backend.entity.UserRole.OPERATOR -> UserPermissionsResponse(
                canViewCatalog = true,
                canEditCatalog = true,
                canOperate = true,
                canViewLogs = true,
                canExportReports = false,
                canManageUsers = false,
                canManageThresholds = false,
                canManualDatetime = false,
            )

            com.inventory.backend.entity.UserRole.VIEWER -> UserPermissionsResponse(
                canViewCatalog = true,
                canEditCatalog = false,
                canOperate = false,
                canViewLogs = false,
                canExportReports = false,
                canManageUsers = false,
                canManageThresholds = false,
                canManualDatetime = false,
            )
        }
    }

    private fun toResponse(user: AppUser): UserAdminResponse = UserAdminResponse(
        id = user.id,
        username = user.username,
        fullName = user.fullName,
        role = user.role,
        active = user.active == true,
        permissions = UserPermissionsResponse(
            canViewCatalog = user.canViewCatalog == true,
            canEditCatalog = user.canEditCatalog == true,
            canOperate = user.canOperate == true,
            canViewLogs = user.canViewLogs == true,
            canExportReports = user.canExportReports == true,
            canManageUsers = user.canManageUsers == true,
            canManageThresholds = user.canManageThresholds == true,
            canManualDatetime = user.canManualDatetime == true,
        ),
    )
}
