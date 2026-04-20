package com.inventory.backend.controller

import com.inventory.backend.dto.RefillHistoryResponse
import com.inventory.backend.entity.RefillHistory
import com.inventory.backend.service.RefillHistoryService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/refill-history")
@CrossOrigin
class RefillHistoryController(
    private val refillHistoryService: RefillHistoryService,
) {
    @GetMapping("/cartridge/{cartridgeId}")
    @PreAuthorize("@authz.canViewCatalog()")
    fun getByCartridgeId(@PathVariable cartridgeId: Long): List<RefillHistoryResponse> =
        refillHistoryService.getByCartridgeId(cartridgeId)

    @PostMapping
    @PreAuthorize("@authz.canOperate()")
    fun create(@RequestBody refillHistory: RefillHistory): RefillHistoryResponse =
        refillHistoryService.create(refillHistory)
}
