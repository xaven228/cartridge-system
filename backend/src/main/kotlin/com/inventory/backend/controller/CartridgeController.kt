package com.inventory.backend.controller

import com.inventory.backend.dto.AdjustQuantityRequest
import com.inventory.backend.dto.CartridgeResponse
import com.inventory.backend.dto.CreateCartridgeRequest
import com.inventory.backend.dto.InstallCartridgeRequest
import com.inventory.backend.dto.RemoveCartridgeInstallationRequest
import com.inventory.backend.dto.ReplaceCartridgeRequest
import com.inventory.backend.dto.ReturnFromRefillRequest
import com.inventory.backend.dto.SendToRefillRequest
import com.inventory.backend.dto.UpdateCartridgeRefillableRequest
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.service.CartridgeService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cartridges")
@CrossOrigin
class CartridgeController(
    private val cartridgeService: CartridgeService,
) {
    @GetMapping
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAll(
        @RequestParam(required = false) departmentId: Long?,
        @RequestParam(required = false) status: CartridgeStatus?,
    ): List<CartridgeResponse> = cartridgeService.getAll(departmentId, status)

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canViewCatalog()")
    fun getById(@PathVariable id: Long): CartridgeResponse = cartridgeService.getById(id)

    @PostMapping
    @PreAuthorize("@authz.canOperate()")
    fun create(@Valid @RequestBody request: CreateCartridgeRequest): CartridgeResponse = cartridgeService.create(request)

    @PatchMapping("/{id}/quantity")
    @PreAuthorize("@authz.canOperate()")
    fun adjustQuantity(
        @PathVariable id: Long,
        @Valid @RequestBody request: AdjustQuantityRequest,
    ): CartridgeResponse = cartridgeService.adjustQuantity(id, request)

    @PostMapping("/{id}/send-to-refill")
    @PreAuthorize("@authz.canOperate()")
    fun sendToRefill(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendToRefillRequest,
    ): CartridgeResponse = cartridgeService.sendToRefill(id, request)

    @PostMapping("/{id}/return-from-refill")
    @PreAuthorize("@authz.canOperate()")
    fun returnFromRefill(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReturnFromRefillRequest,
    ): CartridgeResponse = cartridgeService.returnFromRefill(id, request)

    @PostMapping("/{id}/install")
    @PreAuthorize("@authz.canOperate()")
    fun installToPrinter(
        @PathVariable id: Long,
        @Valid @RequestBody request: InstallCartridgeRequest,
    ): CartridgeResponse = cartridgeService.installToPrinter(id, request)

    @PostMapping("/{id}/replace")
    @PreAuthorize("@authz.canOperate()")
    fun replaceInPrinter(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReplaceCartridgeRequest,
    ): CartridgeResponse = cartridgeService.replaceInPrinter(id, request)

    @PostMapping("/{id}/remove-installation")
    @PreAuthorize("@authz.canOperate()")
    fun removeFromPrinter(
        @PathVariable id: Long,
        @Valid @RequestBody request: RemoveCartridgeInstallationRequest,
    ): CartridgeResponse = cartridgeService.removeFromPrinter(id, request)

    @PatchMapping("/{id}/refillable")
    @PreAuthorize("@authz.canOperate()")
    fun updateRefillable(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCartridgeRefillableRequest,
    ): CartridgeResponse = cartridgeService.updateRefillable(id, request)

    @PostMapping("/{id}/write-off")
    @PreAuthorize("@authz.canOperate()")
    fun writeOff(
        @PathVariable id: Long,
        @RequestBody(required = false) body: Map<String, String>?,
    ): CartridgeResponse = cartridgeService.writeOff(id, body?.get("comment"))

    @PostMapping("/{id}/mark-empty")
    @PreAuthorize("@authz.canOperate()")
    fun markInstalledAsEmpty(
        @PathVariable id: Long,
        @RequestBody(required = false) body: Map<String, String>?,
    ): CartridgeResponse = cartridgeService.markInstalledAsEmpty(id, body?.get("comment"))

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOperate()")
    fun delete(@PathVariable id: Long) {
        cartridgeService.delete(id)
    }
}
