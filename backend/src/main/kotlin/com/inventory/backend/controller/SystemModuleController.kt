package com.inventory.backend.controller

import com.inventory.backend.dto.SystemModuleResponse
import com.inventory.backend.service.SystemModuleService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/system-modules")
@CrossOrigin
class SystemModuleController(
    private val systemModuleService: SystemModuleService,
) {

    @GetMapping
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAll(): List<SystemModuleResponse> = systemModuleService.getAll()
}
