package com.inventory.backend.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component("authz")
class AuthorizationService {

    fun canViewCatalog(): Boolean = currentUser()?.canViewCatalog == true

    fun canEditCatalog(): Boolean = currentUser()?.canEditCatalog == true

    fun canOperate(): Boolean = currentUser()?.canOperate == true

    fun canViewLogs(): Boolean = currentUser()?.canViewLogs == true

    fun canExportReports(): Boolean = currentUser()?.canExportReports == true

    fun canManageUsers(): Boolean = currentUser()?.canManageUsers == true

    fun canManageThresholds(): Boolean = currentUser()?.canManageThresholds == true

    fun canManualDatetime(): Boolean = currentUser()?.canManualDatetime == true

    private fun currentUser(): AuthenticatedUser? =
        SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
}
