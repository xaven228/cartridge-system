package com.inventory.backend.controller

import com.inventory.backend.dto.PrinterResponse
import com.inventory.backend.dto.UpsertPrinterRequest
import com.inventory.backend.service.PrinterService
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
@RequestMapping("/api/printers")
@CrossOrigin
class PrinterController(
    private val printerService: PrinterService,
) {
    @GetMapping
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAll(): List<PrinterResponse> = printerService.getAll()

    @PostMapping
    @PreAuthorize("@authz.canEditCatalog()")
    fun create(@Valid @RequestBody request: UpsertPrinterRequest): PrinterResponse =
        printerService.create(request)

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpsertPrinterRequest,
    ): PrinterResponse = printerService.update(id, request)

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun delete(@PathVariable id: Long) {
        printerService.delete(id)
    }
}
