package com.inventory.backend.service

import com.inventory.backend.dto.AuthResponse
import com.inventory.backend.dto.AuthUserResponse
import com.inventory.backend.dto.LoginRequest
import com.inventory.backend.dto.UserPermissionsResponse
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.AppUser
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.AppUserRepository
import com.inventory.backend.security.AuthenticatedUser
import com.inventory.backend.security.JwtService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpServletRequest

@Service
class AuthService(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val actionLogService: ActionLogService,
) {

    fun login(request: LoginRequest, httpRequest: HttpServletRequest): AuthResponse {
        val user = appUserRepository.findByUsernameIgnoreCase(request.username.trim())
            .orElseThrow { NotFoundException("Пользователь не найден") }

        if (user.active != true) {
            throw BadRequestException("Пользователь заблокирован")
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadRequestException("Неверный пароль")
        }

        actionLogService.log(
            actionType = ActionLogType.USER_LOGIN,
            targetName = user.username,
            details = "Успешный вход в систему",
            actor = user.fullName,
            deviceInfo = buildDeviceInfo(httpRequest),
        )
        return buildAuthResponse(user)
    }

    fun me(): AuthUserResponse {
        val auth = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BadRequestException("Пользователь не авторизован")
        return toUserResponse(auth.id)
    }

    fun refresh(): AuthResponse {
        val auth = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BadRequestException("Пользователь не авторизован")
        val user = appUserRepository.findById(auth.id)
            .orElseThrow { NotFoundException("Пользователь не найден") }

        if (user.active != true) {
            throw BadRequestException("Пользователь заблокирован")
        }

        return buildAuthResponse(user)
    }

    private fun toUserResponse(userId: Long): AuthUserResponse {
        val user = appUserRepository.findById(userId)
            .orElseThrow { NotFoundException("Пользователь не найден") }

        return AuthUserResponse(
            id = user.id!!,
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

    private fun buildAuthResponse(user: AppUser): AuthResponse {
        val (token, expiresAt) = jwtService.generateToken(user)
        return AuthResponse(
            token = token,
            expiresAt = expiresAt,
            user = toUserResponse(user.id!!),
        )
    }

    private fun buildDeviceInfo(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
        val remote = forwardedFor ?: request.remoteAddr ?: "unknown-ip"
        val userAgent = request.getHeader("User-Agent")?.trim().orEmpty()
        return if (userAgent.isBlank()) remote else "$remote | $userAgent"
    }
}
