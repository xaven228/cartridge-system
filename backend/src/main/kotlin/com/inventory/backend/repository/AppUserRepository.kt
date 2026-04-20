package com.inventory.backend.repository

import com.inventory.backend.entity.AppUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsernameIgnoreCase(username: String): Optional<AppUser>
    fun existsByUsernameIgnoreCase(username: String): Boolean
}
