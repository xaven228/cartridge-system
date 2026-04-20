package com.inventory.backend.controller

import com.inventory.backend.entity.CartridgeModel
import com.inventory.backend.service.CartridgeModelService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cartridge-models")
@CrossOrigin
class CartridgeModelController(
    private val cartridgeModelService: CartridgeModelService,
) {
    @GetMapping
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAll(): List<CartridgeModel> = cartridgeModelService.getAll()

    @PostMapping
    @PreAuthorize("@authz.canEditCatalog()")
    fun create(@Valid @RequestBody cartridgeModel: CartridgeModel): CartridgeModel =
        cartridgeModelService.create(cartridgeModel)

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody cartridgeModel: CartridgeModel,
    ): CartridgeModel = cartridgeModelService.update(id, cartridgeModel)

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun delete(@PathVariable id: Long) {
        cartridgeModelService.delete(id)
    }
}
