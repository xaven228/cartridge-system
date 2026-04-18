package com.inventory.backend.service

import com.inventory.backend.dto.AuthResponse
import com.inventory.backend.dto.AuthUserResponse
import com.inventory.backend.dto.LoginRequest
import com.inventory.backend.dto.UserPermissionsResponse
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.AppUserRepository
import com.inventory.backend.security.AuthenticatedUser
import com.inventory.backend.security.JwtService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    fun login(request: LoginRequest): AuthResponse {
        val user = appUserRepository.findByUsernameIgnoreCase(request.username.trim())
            .orElseThrow { NotFoundException("Пользователь не найден") }

        if (user.active != true) {
            throw BadRequestException("Пользователь заблокирован")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadRequestException("Неверный пароль")
        }

        val (token, expiresAt) = jwtService.generateToken(user)
        return AuthResponse(
            token = token,
            expiresAt = expiresAt,
            user = toUserResponse(user.id),
        )
    }

    fun me(): AuthUserResponse {
        val auth = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BadRequestException("Пользователь не авторизован")
        return toUserResponse(auth.id)
    }

    private fun toUserResponse(userId: Long): AuthUserResponse {
        val user = appUserRepository.findById(userId)
            .orElseThrow { NotFoundException("Пользователь не найден") }

        return AuthUserResponse(
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
            )
        )
    }
}
