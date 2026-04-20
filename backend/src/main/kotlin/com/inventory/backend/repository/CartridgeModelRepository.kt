package com.inventory.backend.repository

import com.inventory.backend.entity.CartridgeModel
import org.springframework.data.jpa.repository.JpaRepository

interface CartridgeModelRepository : JpaRepository<CartridgeModel, Long> {
    fun existsByNameIgnoreCase(name: String): Boolean
}
