package com.inventory.backend.security

import com.inventory.backend.entity.UserRole
import com.inventory.backend.repository.AppUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val appUserRepository: AppUserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        try {
            val claims = jwtService.parseClaims(token)
            val userId = claims.subject?.toLongOrNull()
            if (userId != null && SecurityContextHolder.getContext().authentication == null) {
                val user = appUserRepository.findById(userId).orElse(null)
                if (user != null && user.active == true) {
                    val principal = AuthenticatedUser(
                        id = user.id!!,
                        username = user.username,
                        fullName = user.fullName,
                        role = user.role,
                        canViewCatalog = user.canViewCatalog == true,
                        canEditCatalog = user.canEditCatalog == true,
                        canOperate = user.canOperate == true,
                        canViewLogs = user.canViewLogs == true,
                        canExportReports = user.canExportReports == true,
                        canManageUsers = user.canManageUsers == true,
                        canManageThresholds = user.canManageThresholds == true,
                        canManualDatetime = user.canManualDatetime == true,
                    )
                    val auth = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        } catch (_: Exception) {
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}
